package com.betterhotbarswap.client.input;

import com.betterhotbarswap.client.debug.BHSDebug;
import com.betterhotbarswap.config.BHSConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

// Düzenli toplama (F6) - FARE-GÜDÜMLÜ alım kitaplığı (v2 refound).
//
// KURAL: Shift + kısayol basılıyken fareyle GÖSTERİLEN/TIKLANAN depo yığını,
// atanmış slot (N) düzenine yerleştirilir. Oto-drain YOKTUR: her alım açık
// bir kullanıcı hareketidir (tuş basışı/tekrara veya tıklama). Tutuş +
// fareyi gezdirme = hızlı süpürme; bırakma = durma.
//
// DUVAR (ziyaret kapsamlı): N SADECE ziyaretin İLK alımında dolar
// (dolu-farklıysa evict); sonraki TÜM alımlar N'i atlar (sağa + main).
// Sıra: N, N+1..8, main 35..9. N'in SOLUNA (0..N-1) asla yazılmaz.
// Ekran kapanıp handler değişince duvar sıfırlanır.
//
// Teknik: imleç-zincir disiplini (boş-hedefe place, kısmi-hedefe whole-fit
// birleştirme, kalan bitince kaynağa iade). SWAP ile yabancı item imlece
// gelmez (evict hariç: o da boş-hedefli takas). Her yolda imleç BOŞ biter.
// Üretim output'larında çalışMAZ (orada bulk geçerli; saf-depo ekranı şartı).
public final class TidyCollect {
    private TidyCollect() {
    }

    private static ScreenHandler wallHandler;
    private static final boolean[] wallClaimed = new boolean[10];
    private static int lastTakenDest = -1;
    private static long lastFullMsgMs;

    private static void resetWall(ScreenHandler h) {
        if (wallHandler != h) {
            wallHandler = h;
            java.util.Arrays.fill(wallClaimed, false);
        }
    }

    // Sıra düzeni: N, N+1..8, main 35..9.
    private static List<Integer> buildOrder(int anchor) {
        List<Integer> ord = new ArrayList<>();
        for (int i = anchor; i < 9; i++) ord.add(i);
        for (int i = 35; i >= 9; i--) ord.add(i);
        return ord;
    }

    // Saf-depo ekranı mı? (output slotu YOKSA: crafting/fırın/tezgah elenir.)
    // CraftingResultSlot sınıfı içerik beklemeden eler (boş output'lu üretim
    // ekranında ızgara soygunu olmasın diye).
    private static boolean isPureStorage(ScreenHandler h, PlayerInventory inv) {
        try {
            for (Slot s : h.slots) {
                if (s == null || s.inventory == null) continue;
                if (s.inventory == inv) continue;
                if (s instanceof net.minecraft.screen.slot.CraftingResultSlot) return false;
                if (!s.hasStack()) continue;
                if (!s.canTakeItems(MinecraftClient.getInstance().player)) continue;
                ItemStack st = s.getStack();
                if (!s.canInsert(st)) return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    // Bu slot alıma uygun mu? (taze okuma; fare konumu + wyścig güvenliği için
    // çağıran her seferinde yeniden doğrular.)
    public static boolean canTake(ScreenHandler h, Slot slot) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null || client.interactionManager == null) return false;
            if (!BHSConfig.tidyCollect) return false;
            if (h == null || slot == null || slot.inventory == null) return false;
            PlayerInventory inv = client.player.getInventory();
            if (slot.inventory == inv) return false;
            if (!slot.hasStack() || !slot.canTakeItems(client.player)) return false;
            if (SlotClassifier.isProbableOutputSlot(slot, client.player, false)) return false;
            if (!h.getCursorStack().isEmpty()) return false;
            if (!isPureStorage(h, inv)) return false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Ana giriş: hover/tıklanan yığını anchor düzenine yerleştirir.
    // true = yerleşti (vanilla iptal edilir); false = dokunulmadı (vanilla yapar).
    public static boolean takeFocused(ScreenHandler h, Slot slot, int hotbarIndex) {
        return tryTake(h, slot, hotbarIndex) == TAKE_PLACED;
    }

    public static final int TAKE_PLACED = 0;
    public static final int TAKE_FAILED = 1;
    public static final int TAKE_GATED = 2;
    // Hız kesici (BetterHotbarSwapping çözümü + genel taban): alımlar senkron
    // turundan hızlı giderse sunucu onayları yetişemez, tahminler bayat okumayla
    // sapar (tuhaf yerleşimler + hayaletler). İKİ kapı vardır:
    // 1) çift-kapı (anchor+slot, 150ms): aynı yığının art arda alımı (ping-pong,
    //    hayalet-eless) engellenir; tap temposu etkilenmez.
    // 2) genel taban (50ms): toplam alım hızı sunucu tikine iner (~20/sn tavan).
    // İkisi de sessiz reddeder (paket YOK, damga YOK).
    private static final long PAIR_GAP_NANOS = 150_000_000L;
    private static final long FLOOR_GAP_NANOS = 50_000_000L;
    private static final long[] lastPairNanos = new long[10 * 128];
    private static long lastAnyTakeNanos;

    // Kapı: pencere doluysa sessizce reddeder (paket YOK, damga YOK).
    public static boolean gateOpen(int hotbarIndex, int slotId) {
        if (hotbarIndex < 0 || hotbarIndex > 9) return false;
        long now = System.nanoTime();
        int pair = hotbarIndex * 128 + (slotId & 127);
        if (now - lastPairNanos[pair] < PAIR_GAP_NANOS) return false;
        if (now - lastAnyTakeNanos < FLOOR_GAP_NANOS) return false;
        lastPairNanos[pair] = now;
        lastAnyTakeNanos = now;
        return true;
    }

    // Kapılı alım: pencere doluysa sessizce reddeder (paket YOK).
    public static int tryTake(ScreenHandler h, Slot slot, int hotbarIndex) {
        try {
            if (hotbarIndex < 0 || hotbarIndex > 9 || slot == null) return TAKE_FAILED;
            if (!gateOpen(hotbarIndex, slot.id)) return TAKE_GATED;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null || client.interactionManager == null) return TAKE_FAILED;
            if (!canTake(h, slot)) return TAKE_FAILED;
            PlayerInventory inv = client.player.getInventory();
            resetWall(h);
            boolean forceAnchor = !wallClaimed[hotbarIndex];
            List<Integer> order = buildOrder(hotbarIndex);
            if (!takeStackIntoOrder(client, inv, slot, hotbarIndex, order, forceAnchor)) {
                return TAKE_FAILED;
            }
            wallClaimed[hotbarIndex] = true;
            return TAKE_PLACED;
        } catch (Exception e) {
            return TAKE_FAILED;
        }
    }

    // Düzenli toplama aktif olabilecek ekran mı? (saf-depo, creative değil)
    public static boolean isTidyScreen(ScreenHandler h) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || h == null) return false;
        if (client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen) return false;
        return isPureStorage(h, client.player.getInventory());
    }

    private static boolean inRedirect;

    // EVRENSEL tık yolu: Shift + kısayol fiziksel basılıyken depo slotundan gelen
    // HER QUICK_MOVE (vanilla Shift+tık, Shift+çift-tık "hepsini topla",
    // Mouse Tweaks/benzeri modların Shift-sürüklemesi) tidy sıraya yönlendirilir.
    // Hepsi ClientPlayerInteractionManager.clickSlot'tan geçer (javap doğrulandı),
    // bu yüzden tek kapı yeter; ekran/mod sınıfına bakılmaz.
    // true = vanilla QUICK_MOVE iptal (alındı YA DA yer yok); asla sola dolmaz.
    // Kapı (gateOpen) YOK: her tık bilinçli kullanıcı hareketidir; çift-tık
    // döngüsü aynı karede N yığın üretir ve her birinin alınması gerekir.
    public static boolean redirectQuickMove(int syncId, int slotId) {
        if (inRedirect) return false;
        try {
            if (!BHSConfig.tidyCollect || !HotbarInputController.isTidyHeld()) return false;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null || client.interactionManager == null) return false;
            ScreenHandler h = client.player.currentScreenHandler;
            if (h == null || h.syncId != syncId || slotId < 0 || slotId >= h.slots.size()) return false;
            if (!isTidyScreen(h)) return false;
            Slot slot = h.getSlot(slotId);
            if (!canTake(h, slot)) return false;
            int anchor = HotbarInputController.latchedHotbar();
            inRedirect = true;
            int prevSel = client.player.getInventory().selectedSlot;
            ItemStack pre = slot.getStack().copy();
            resetWall(h);
            boolean ok = takeStackIntoOrder(client, client.player.getInventory(), slot, anchor,
                buildOrder(anchor), !wallClaimed[anchor]);
            HotbarInputController.restoreSelectionIfChanged(client, prevSel);
            if (ok) {
                wallClaimed[anchor] = true;
                BHSDebug.log("TIDY al-qm x" + pre.getCount() + " -> " + slotName(lastTakenDest));
            } else {
                tellFull(client);
            }
            return true;
        } catch (Exception e) {
            BHSDebug.log("TIDY qm-hata " + e);
            // Paket gönderilmeye başlandıysa vanilla'yı ARTIK çalıştırma (çift işlem).
            boolean started = inRedirect;
            return started;
        } finally {
            inRedirect = false;
        }
    }

    // Çekirdek alım: src yığınını sıra düzenine yerleştirir. Her yolda imleç
    // BOŞ biter (yer yoksa kaynağa iade edilir). true = yerleşti.
    private static boolean takeStackIntoOrder(MinecraftClient client, PlayerInventory inv, Slot src,
                                              int anchor, List<Integer> order, boolean forceAnchor) {
        lastTakenDest = -1;
        ItemStack stack = src.getStack().copy();
        // Ön-kontrol: sıra toplam kapasitesi yetmiyorsa HİÇ paket gitmez
        // (al-geri-ver flicker'ı olmaz; sessiz false + mesaj üstte).
        if (!hasRoom(inv, stack, anchor, order, forceAnchor)) {
            BHSDebug.log("TIDY yer-yok (on-kontrol)");
            return false;
        }
        ItemStack chestStack = stack;
        int srcId = src.id;
        // 1) Kaynaktan al (imleç = tamamı).
        client.interactionManager.clickSlot(handlerOf(inv).syncId, srcId, 0, SlotActionType.PICKUP, client.player);
        int remaining = chestStack.getCount();
        for (int oi = 0; oi < order.size() && remaining > 0; oi++) {
            int destIdx = order.get(oi);
            // İlk alım İLK sıraya zorlanır (N dolu-farklıysa evict).
            if (forceAnchor && oi == 0) {
                ItemStack cur = inv.getStack(destIdx).copy();
                if (!cur.isEmpty() && !isSame(cur, chestStack)) {
                    int ev = firstEmptyInOrder(inv, order, 1);
                    int nReal = VanillaClickExecutor.realSlotId(handlerOf(inv), inv, destIdx);
                    int evReal = ev == -1 ? -1 : VanillaClickExecutor.realSlotId(handlerOf(inv), inv, ev);
                    if (ev == -1 || nReal == -1 || evReal == -1) {
                        client.interactionManager.clickSlot(handlerOf(inv).syncId, srcId, 0, SlotActionType.PICKUP, client.player);
                        BHSDebug.log("TIDY yer-yok (evict)");
                        return false;
                    }
                    // SWAP kuralı: tıklanan slot <-> hotbar[düğme] (düğme 0-8).
                    // Main-anchor (9) SWAP'lanamaz: PICKUP çiftiyle taşınır.
                    if (destIdx < 9 && ev < 9) {
                        client.interactionManager.clickSlot(handlerOf(inv).syncId, nReal, ev, SlotActionType.SWAP, client.player);
                    } else if (destIdx < 9) {
                        client.interactionManager.clickSlot(handlerOf(inv).syncId, evReal, destIdx, SlotActionType.SWAP, client.player);
                    } else {
                        client.interactionManager.clickSlot(handlerOf(inv).syncId, nReal, 0, SlotActionType.PICKUP, client.player);
                        client.interactionManager.clickSlot(handlerOf(inv).syncId, evReal, 0, SlotActionType.PICKUP, client.player);
                    }
                    inv.setStack(ev, cur);
                    inv.setStack(destIdx, ItemStack.EMPTY);
                }
            }
            ItemStack d = inv.getStack(destIdx).copy();
            if (!d.isEmpty() && !isSame(d, chestStack)) continue;
            int destReal = VanillaClickExecutor.realSlotId(handlerOf(inv), inv, destIdx);
            if (destReal == -1) continue;
            if (d.isEmpty()) {
                client.interactionManager.clickSlot(handlerOf(inv).syncId, destReal, 0, SlotActionType.PICKUP, client.player);
                ItemStack placed = chestStack.copy();
                placed.setCount(remaining);
                inv.setStack(destIdx, placed);
                if (lastTakenDest == -1) lastTakenDest = destIdx;
                remaining = 0;
            } else {
                int room = d.getMaxCount() - d.getCount();
                if (room <= 0) continue;
                int move = Math.min(room, remaining);
                client.interactionManager.clickSlot(handlerOf(inv).syncId, destReal, 0, SlotActionType.PICKUP, client.player);
                ItemStack grown = d.copy();
                grown.setCount(d.getCount() + move);
                inv.setStack(destIdx, grown);
                remaining -= move;
                if (lastTakenDest == -1) lastTakenDest = destIdx;
            }
        }
        if (remaining > 0) {
            // Sıra doldu: kalanı kaynağa iade et (imleç BOŞ kalır).
            client.interactionManager.clickSlot(handlerOf(inv).syncId, srcId, 0, SlotActionType.PICKUP, client.player);
            BHSDebug.log("TIDY yer-yok (kalan x" + remaining + " iade)");
            return false;
        }
        return true;
    }

    // Ön-kontrol: sıra toplam kapasitesi yetmiyorsa HİÇ paket gitmez
    // (al-geri-ver flicker'ı olmaz). Evict durumu dahil edilir.
    private static boolean hasRoom(PlayerInventory inv, ItemStack stack, int anchor,
                                   List<Integer> order, boolean forceAnchor) {
        try {
            int need = stack.getCount();
            int evIdx = -1;
            // Sıranın ilk elemanı (anchor=9 "0" modunda ilk eleman m35'tir, anchor değil).
            int first = order.get(0);
            if (forceAnchor) {
                ItemStack cur = inv.getStack(first);
                if (!cur.isEmpty() && !isSame(cur, stack)) {
                    for (int oi = 1; oi < order.size(); oi++) {
                        if (inv.getStack(order.get(oi)).isEmpty()) {
                            evIdx = order.get(oi);
                            break;
                        }
                    }
                    if (evIdx == -1) return false;
                }
            }
            int cap = 0;
            for (int oi = 0; oi < order.size(); oi++) {
                int idx = order.get(oi);
                if (idx == evIdx) continue;
                ItemStack d = (idx == first && evIdx != -1) ? ItemStack.EMPTY : inv.getStack(idx);
                if (d.isEmpty()) cap += stack.getMaxCount();
                else if (isSame(d, stack)) cap += Math.max(0, d.getMaxCount() - d.getCount());
                if (cap >= need) return true;
            }
            return cap >= need;
        } catch (Exception e) {
            return false;
        }
    }

    private static ScreenHandler handlerOf(PlayerInventory inv) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null || client.player == null ? null : client.player.currentScreenHandler;
    }

    // Sıra içinde ilk BOŞ (fromOi'den itibaren).
    private static int firstEmptyInOrder(PlayerInventory inv, List<Integer> order, int fromOi) {
        for (int oi = fromOi; oi < order.size(); oi++) {
            int idx = order.get(oi);
            if (inv.getStack(idx).isEmpty()) return idx;
        }
        return -1;
    }

    public static int lastDest() {
        return lastTakenDest;
    }

    public static void tellFull(MinecraftClient client) {
        try {
            long now = System.currentTimeMillis();
            if (now - lastFullMsgMs < 10000) return;
            lastFullMsgMs = now;
            if (client.player == null) return;
            client.player.sendMessage(
                net.minecraft.text.Text.literal("Yer yok — envanterde boş yer aç.")
                    .formatted(net.minecraft.util.Formatting.RED),
                false);
        } catch (Exception ignored) {
        }
    }

    private static boolean isSame(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;
        return java.util.Objects.equals(a.getNbt(), b.getNbt());
    }

    public static String slotName(int idx) {
        return idx < 9 ? "hb" + (idx + 1) : "m" + idx;
    }
}
