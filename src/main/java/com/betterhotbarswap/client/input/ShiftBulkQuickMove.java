package com.betterhotbarswap.client.input;

import com.betterhotbarswap.BetterHotbarSwap;
import com.betterhotbarswap.client.debug.BHSDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// Shift + Kısayol v7: burst (BurstTakes) üretir, burası doğrular/yakınsar.
// SETTLE (3 sabit tick) + tick-başına plan adımı (output-drain, evict,
// place, topup, bubble). SWAP'lar imleçsizdir; PICKUP sadece taze okuma +
// whole-fit ile. Takılma oyuncuya mesajla bildirilir (sessiz kalınmaz).
public final class ShiftBulkQuickMove {
    private ShiftBulkQuickMove() {
    }

    private static AbstractContainerMenu handler;
    private static Slot slot;
    private static int hotbarIndex;
    private static ItemStack anchor = ItemStack.EMPTY;
    private static String itemKey = "";
    private static int actions;
    private static int ticks;
    private static int stableTicks;
    private static int lastTotal;
    private static String lastOutputKey;
    private static boolean settled;
    private static String lastProgressKey = "";
    private static int stuckTicks;
    // İmleç ancak birkaç tick kalıcıysa gerçektir (sunucu düzeltmesi 1-2 tick'te gelir);
    // hayalet imlece göre "kurtarma" PICKUP'ı sunucuda dolu slotu imlece çekebilir.
    private static int carriedTicks;
    private static final int CARRIED_CONFIRM = 3;
    // Kaynak-governor (anti-dupe yapısal sigorta): bulk alımları girdiyi
    // tüketmek ZORUNDADIR. Belli sürede girdi azalmadıysa kaynak tükenmeyen
    // (sonsuz) kaynaktır -> DUR. Eski tahmin kalıntıları da bu yüzden durur.
    private static java.util.List<Integer> govInputIds = new java.util.ArrayList<>();
    private static int govInputTotal;
    private static int govPressTakes;

    private static final int SETTLE_NEED = 3;
    private static final int SETTLE_TIMEOUT = 40;
    private static final int MAX_ACTIONS = 300;
    private static final int MAX_TICKS = 600;

    public static boolean isActive() {
        return handler != null;
    }

    public static void cancel() {
        handler = null;
        slot = null;
        anchor = ItemStack.EMPTY;
    }

    public static void start(AbstractContainerMenu h, Slot focusedSlot, int hb, ItemStack anchorStack) {
        start(h, focusedSlot, hb, anchorStack, 0);
    }

    public static void start(AbstractContainerMenu h, Slot focusedSlot, int hb, ItemStack anchorStack, int pressTakes) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.gameMode == null) return;
        int slotId = VanillaClickExecutor.resolveSlotId(h, focusedSlot);
        if (slotId < 0 || slotId >= h.slots.size()) {
            BHSDebug.log("SHIFT_BULK-QM abort (NOMAP)");
            return;
        }
        handler = h;
        slot = focusedSlot;
        hotbarIndex = hb;
        anchor = anchorStack.copy();
        itemKey = keyOf(anchor);
        actions = 0;
        ticks = 0;
        carriedTicks = 0;
        stableTicks = 0;
        settled = false;
        lastProgressKey = "";
        stuckTicks = 0;
        lastTotal = totalOf(client.player.getInventory(), anchor);
        lastOutputKey = outputKey();
        govInputIds = BurstTakes.inputSlotIds(h, focusedSlot, client.player.getInventory());
        govInputTotal = govTotal(client);
        govPressTakes = pressTakes;
        // QUICK_MOVE YOK: üretim burst ile, tekil alım executor ile yapılır.
        // Burası sadece doğrulama/yakınsama koludur (settle + plan).
        BHSDebug.log("SHIFT_BULK-QM arm -> hb" + (hb + 1) + " " + shortStack(anchor));
    }

    public static void onTick(Minecraft client) {
        if (handler == null) return;
        if (client == null || client.player == null || client.gameMode == null) {
            cancel();
            return;
        }
        if (client.player.containerMenu != handler) {
            cancel();
            return;
        }
        Inventory inv0 = client.player.getInventory();
        if (handler.getCarried().isEmpty()) {
            carriedTicks = 0;
        } else if (++carriedTicks < CARRIED_CONFIRM) {
            return;
        } else {
            // İmleç kurtarma (SADECE bulk sırasında): en yakın boşa yerleştir
            // + DUR. Kullanıcının elindeki de olsa en güvenli yer boş slottur;
            // sessizce imleçte bırakmaktan iyidir. Boş yoksa dokunulmaz.
            ItemStack cur = handler.getCarried().copy();
            // "0" modunda hotbar'a dokunulmaz: main ilk-boş.
            int empty = hotbarIndex == 9 ? firstEmptyMain(inv0)
                : nearestEmptyHotbar(inv0, hotbarIndex);
            if (empty == -1 && hotbarIndex != 9) empty = firstEmptyMain(inv0);
            int emptyReal = empty == -1 ? -1 : VanillaClickExecutor.realSlotId(handler, inv0, empty);
            if (emptyReal != -1) {
                client.gameMode.handleContainerInput(handler.containerId, emptyReal, 0, ContainerInput.PICKUP, client.player);
                inv0.setItem(empty, cur);
                BHSDebug.log("SHIFT_BULK-QM imlec-kurtarma x" + cur.getCount() + " -> " + slotName(empty));
                finish("imlec-kurtarma");
            } else {
                finish("imlec-dolu");
            }
            return;
        }
        if (slot == null || !handler.slots.contains(slot)) {
            finish("slot-gitti");
            return;
        }
        if (++ticks > MAX_TICKS || actions >= MAX_ACTIONS) {
            finish("tavan");
            return;
        }
        // Kaynak-governor: alım yapıldıysa girdi AZALMIŞ olmalı. 15 tick'te
        // azalma yoksa kaynak tükenmiyor (sonsuz/hayalet) -> DUR.
        if (!govInputIds.isEmpty() && (govPressTakes > 0 || actions > 0) && ticks > 15) {
            if (govTotal(client) >= govInputTotal) {
                finish("kaynak-tukenmedi");
                return;
            }
        }
        Inventory inv = client.player.getInventory();

        if (!settled) {
            int nowTotal = totalOf(inv, anchor);
            String nowOutput = outputKey();
            if (nowTotal == lastTotal && nowOutput.equals(lastOutputKey)) {
                stableTicks++;
            } else {
                stableTicks = 0;
                lastTotal = nowTotal;
                lastOutputKey = nowOutput;
            }
            if (stableTicks < SETTLE_NEED) {
                if (ticks > SETTLE_TIMEOUT) {
                    settled = true;
                } else {
                    return;
                }
            }
            settled = true;
        }

        // İlerleme sigortası: aksiyonlara rağmen hedef+toplam 6 tick kıpırdamazsa
        // sonsuz döngüye girme, bitir (hangi mekanizma takılırsa takılsın).
        String progressKey = null;
        try {
            progressKey = inv.getItem(hotbarIndex).getCount() + ":" + totalOf(inv, anchor);
        } catch (Exception ignored) {
        }
        if (progressKey != null) {
            if (progressKey.equals(lastProgressKey)) {
                if (++stuckTicks >= 6 && actions > 0) {
                    finish("ilerleme-yok");
                    return;
                }
            } else {
                stuckTicks = 0;
                lastProgressKey = progressKey;
            }
        }

        int max = Math.max(1, anchor.getMaxStackSize());

        // (a) Output'ta kalan varsa tek alım (bulk yerleşimi: forcePriority).
        ItemStack out = slot.getItem();
        if (!out.isEmpty() && keyOf(out).equals(itemKey)) {
            boolean ok = VanillaClickExecutor.assignSlotToHotbar(handler, slot, hotbarIndex, false, true);
            if (ok) {
                actions++;
            } else {
                finish("yer-yok");
            }
            return;
        }
        if (!out.isEmpty() && !keyOf(out).equals(itemKey)) {
            finish("urun-degisti");
            return;
        }

        // "0" modu (main-only): plan adımları hotbar-endekslidir, burada
        // çalışmaz. Output alımları executor içinden mainOnly'e gider;
        // düzenleme adımı yoktur (output boşsa iş bitti).
        if (hotbarIndex == 9) {
            finish("toplandı");
            return;
        }

        ItemStack targetStack = inv.getItem(hotbarIndex).copy();
        int targetRealId = VanillaClickExecutor.realSlotId(handler, inv, hotbarIndex);
        if (targetRealId == -1) {
            finish("slot-gitti");
            return;
        }

        // SWAP planı: imleçsiz adımlar (evict/place/bubble) AYNI tick'te toplu
        // gider; imleçli topup/output-alım tek-tick özeldir. Her adım sayıyı
        // paketten ÖNCE snapshot'lar, tahmin mutlak atamadır (bug #23).
        boolean acted = false;

        // (b) Hedef farklı-item ise boş yere SWAP (evict). Bulk her zaman
        // ON-kuralları: hotbar-yakın önce, yoksa main.
        if (!targetStack.isEmpty() && !isSameStackable(targetStack, anchor)) {
            boolean done = false;
            int emptyHb = nearestEmptyHotbar(inv, hotbarIndex);
            if (emptyHb != -1) {
                client.gameMode.handleContainerInput(handler.containerId, targetRealId, emptyHb, ContainerInput.SWAP, client.player);
                inv.setItem(emptyHb, targetStack);
                inv.setItem(hotbarIndex, ItemStack.EMPTY);
                actions++;
                BHSDebug.log("SHIFT_BULK-QM evict hb" + (hotbarIndex + 1) + " -> hb" + (emptyHb + 1));
                done = true;
            }
            if (!done) {
                int mainEmpty = firstEmptyMain(inv);
                int mainRealId = mainEmpty == -1 ? -1 : VanillaClickExecutor.realSlotId(handler, inv, mainEmpty);
                if (mainEmpty == -1 || mainRealId == -1) {
                    finish("yer-yok");
                    return;
                }
                client.gameMode.handleContainerInput(handler.containerId, mainRealId, hotbarIndex, ContainerInput.SWAP, client.player);
                inv.setItem(mainEmpty, targetStack);
                inv.setItem(hotbarIndex, ItemStack.EMPTY);
                actions++;
                BHSDebug.log("SHIFT_BULK-QM evict hb" + (hotbarIndex + 1) + " -> m" + mainEmpty);
                done = true;
            }
            targetStack = ItemStack.EMPTY;
            acted = true;
        }

        // (c) Hedef boş + kaynak varsa: en büyük tam yığın SWAP ile hedefe.
        // Sayılar paketlerden ÖNCE snapshot'lanır (vanilla da öngörür; sonrası
        // okuma çift-uygulama/hayalet üretir - bug #23).
        if (targetStack.isEmpty()) {
            int best = -1;
            int bestCount = 0;
            for (int i = 0; i < 36; i++) {
                if (i == hotbarIndex) continue;
                ItemStack s = inv.getItem(i);
                if (s.isEmpty() || s.getCount() <= 0 || !isSameStackable(s, anchor)) continue;
                if (s.getCount() > bestCount) {
                    bestCount = s.getCount();
                    best = i;
                }
            }
            if (best != -1) {
                ItemStack moving = inv.getItem(best).copy();
                if (moving.isEmpty() || moving.getCount() <= 0) {
                    finish("hayalet-kaynak");
                    return;
                }
                if (best < 9) {
                    int srcRealId = VanillaClickExecutor.realSlotId(handler, inv, best);
                    if (srcRealId == -1) {
                        finish("slot-gitti");
                        return;
                    }
                    client.gameMode.handleContainerInput(handler.containerId, targetRealId, best, ContainerInput.SWAP, client.player);
                } else {
                    int srcRealId = VanillaClickExecutor.realSlotId(handler, inv, best);
                    if (srcRealId == -1) {
                        finish("slot-gitti");
                        return;
                    }
                    client.gameMode.handleContainerInput(handler.containerId, srcRealId, hotbarIndex, ContainerInput.SWAP, client.player);
                }
                ItemStack moved = moving;
                inv.setItem(best, ItemStack.EMPTY);
                inv.setItem(hotbarIndex, moved);
                actions++;
                BHSDebug.log("SHIFT_BULK-QM place x" + moved.getCount() + " -> hb" + (hotbarIndex + 1));
                targetStack = moved.copy();
                acted = true;
            } else if (!acted) {
                finish("dagitik-bos");
                return;
            }
        }

        // (d) Hedef kısmi + bütün-sığan kaynak varsa TEK PICKUP birleştirme
        // (tick başına bir tane; imleç disiplini). OFF'ta kaynak main'den.
        int space = max - targetStack.getCount();
        boolean topped = false;
        java.util.List<Integer> topOrder = collectOrder(hotbarIndex);
        if (space > 0 && !targetStack.isEmpty()) {
            for (int pass = 0; pass < 2 && !topped; pass++) {
                for (int idx : topOrder) {
                    if (idx == hotbarIndex) continue;
                    ItemStack s = inv.getItem(idx);
                    if (s.isEmpty() || s.getCount() <= 0 || !isSameStackable(s, anchor)) continue;
                    boolean partial = s.getCount() < s.getMaxStackSize();
                    if ((pass == 0) != partial) continue;
                    if (s.getCount() > space) continue;
                    int srcRealId = VanillaClickExecutor.realSlotId(handler, inv, idx);
                    if (srcRealId == -1) continue;
                    int beforeCount = s.getCount();
                    client.gameMode.handleContainerInput(handler.containerId, srcRealId, 0, ContainerInput.PICKUP, client.player);
                    client.gameMode.handleContainerInput(handler.containerId, targetRealId, 0, ContainerInput.PICKUP, client.player);
                    ItemStack t = targetStack.copy();
                    t.setCount(targetStack.getCount() + beforeCount);
                    inv.setItem(hotbarIndex, t);
                    inv.setItem(idx, ItemStack.EMPTY);
                    actions++;
                    BHSDebug.log("SHIFT_BULK-QM topup +" + beforeCount + " -> hb" + (hotbarIndex + 1));
                    targetStack = t;
                    acted = true;
                    topped = true;
                    break;
                }
            }
        }

        // (e) Hedef dolu + uzakta yığın + yakında boş varsa: SWAP ile yaklaştır
        // (en-yakın kuralı; overflow sağ-altta kalmaz). Aynı tick'te zincirlenir
        // (imleçsiz adımlar güvenlidir).
        int bubbleSwaps = 0;
        boolean bubbled;
        do {
            bubbled = false;
            java.util.List<Integer> order = collectOrder(hotbarIndex);
            for (int oi = 0; oi < order.size() && !bubbled; oi++) {
            int emptyIdx = order.get(oi);
            if (!inv.getItem(emptyIdx).isEmpty()) continue;
            for (int si = oi + 1; si < order.size() && !bubbled; si++) {
                int srcIdx = order.get(si);
                ItemStack s = inv.getItem(srcIdx);
                if (s.isEmpty() || s.getCount() <= 0 || !isSameStackable(s, anchor)) continue;
                int emptyRealId = VanillaClickExecutor.realSlotId(handler, inv, emptyIdx);
                int srcRealId = VanillaClickExecutor.realSlotId(handler, inv, srcIdx);
                if (emptyRealId == -1 || srcRealId == -1) continue;
                // SWAP kuralı: tıklanan slot <-> hotbar[düğme]; düğme 0-8 olmalı.
                // Boş main <-> main arası SWAP yok: oraya PICKUP çifti (boş hedefe
                // bütün-yığın her zaman sığar, imleç-güvenli).
                ItemStack movingSrc = s.copy();
                if (emptyIdx < 9 && srcIdx < 9) {
                    client.gameMode.handleContainerInput(handler.containerId, srcRealId, emptyIdx, ContainerInput.SWAP, client.player);
                } else if (emptyIdx < 9) {
                    client.gameMode.handleContainerInput(handler.containerId, srcRealId, emptyIdx, ContainerInput.SWAP, client.player);
                } else if (srcIdx < 9) {
                    client.gameMode.handleContainerInput(handler.containerId, emptyRealId, srcIdx, ContainerInput.SWAP, client.player);
                } else {
                    client.gameMode.handleContainerInput(handler.containerId, srcRealId, 0, ContainerInput.PICKUP, client.player);
                    client.gameMode.handleContainerInput(handler.containerId, emptyRealId, 0, ContainerInput.PICKUP, client.player);
                }
                ItemStack moved = movingSrc;
                inv.setItem(emptyIdx, moved);
                inv.setItem(srcIdx, ItemStack.EMPTY);
                actions++;
                BHSDebug.log("SHIFT_BULK-QM yerles " + slotName(srcIdx) + " -> " + slotName(emptyIdx));
                acted = true;
                bubbled = true;
                bubbleSwaps++;
            }
        } } while (bubbled && bubbleSwaps < 12);
        if (!acted) {
            finish(targetStack.isEmpty() ? "dagitik-bos" : "toplandı");
        }
    }

    private static String slotName(int idx) {
        return idx < 9 ? "hb" + (idx + 1) : "m" + idx;
    }

    private static int govTotal(Minecraft client) {
        try {
            int total = 0;
            for (int id : govInputIds) {
                ItemStack s = handler.getSlot(id).getItem();
                if (!s.isEmpty()) total += s.getCount();
            }
            return total;
        } catch (Exception e) {
            return -1;
        }
    }

    private static void finish(String why) {
        BHSDebug.log("SHIFT_BULK-QM stop (" + why + ") actions=" + actions);
        cancel();
    }

    private static String outputKey() {
        if (slot == null) return "no-slot";
        try {
            return keyOf(slot.getItem());
        } catch (Exception e) {
            return "err";
        }
    }

    private static java.util.List<Integer> collectOrder(int target) {
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (int d = 1; d < 9; d++) {
            if (target - d >= 0) order.add(target - d);
            if (target + d < 9) order.add(target + d);
        }
        for (int i = 9; i < 36; i++) order.add(i);
        return order;
    }

    private static int nearestEmptyHotbar(Inventory inv, int target) {
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            if (i == target || !inv.getItem(i).isEmpty()) continue;
            int dist = Math.abs(i - target);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private static int firstEmptyMain(Inventory inv) {
        for (int i = 9; i < 36; i++) if (inv.getItem(i).isEmpty()) return i;
        return -1;
    }

    private static int totalOf(Inventory inv, ItemStack anchorStack) {
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && isSameStackable(s, anchorStack)) total += s.getCount();
        }
        return total;
    }

    private static boolean isSameStackable(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;
        return ItemStack.isSameItemSameComponents(a, b);
    }

    private static String keyOf(ItemStack s) {
        if (s == null || s.isEmpty()) return "empty";
        try {
            return s.getItem().toString() + "|" + s.getComponentsPatch();
        } catch (Exception e) {
            return s.getItem().toString();
        }
    }

    private static String shortStack(ItemStack s) {
        if (s == null || s.isEmpty()) return "bos";
        try {
            return s.getHoverName().getString() + "x" + s.getCount();
        } catch (Exception e) {
            return "?";
        }
    }
}
