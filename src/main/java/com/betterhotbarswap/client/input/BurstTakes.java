package com.betterhotbarswap.client.input;

import com.betterhotbarswap.client.debug.BHSDebug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Aynı-tick burst motoru (hız tavanı: 0.06-0.1sn algısı).
//
// GERÇEK (bytecode ile doğrulandı, 1.20.1):
// - Tek QUICK_MOVE/PICKUP = TEK craft (döngü yok); vanilya shift-click bile
//   sadece görünen stack'i alır. "Toplu paket" diye bir şey YOK.
// - Her craft deterministiktir: grid'den slot-başına 1 tüketim (+artık kontrol),
//   sonuç her seferinde aynı R. Yerleşim insertItem kurallarıyla bellidir.
// - O yüzden N craft = N alım ŞARTTIR; ama hepsi AYNI tick'te ateşlenebilir
//   (sunucu paketleri sırayla aynı tick'te işler).
//
// YÖNTEM: vanilla crafting handler + tarif eşleşti + artık-ürün yok +
// imleç boş + sayı taze ise, N alım simüle edilir (executor ile AYNI karar
// sırası, AYNI yardımcılar) ve tüm paketler tek tick'te gider. Tahminler
// mutlak atamadır (vanilla öngörüsüyle idempotent, bug #23).
// Güven sarsılırsa (modded handler, artık, eşleşmesizlik, istisna) null döner
// ve eski güvenli yola (tek alım + drain) düşülür. Kalanı v6 settle+plan
// tick'leri doğrular/yakınsar; imleç sigortası her zaman devrededir.
public final class BurstTakes {
    private BurstTakes() {
    }

    public record BurstClick(int slotId, int button, SlotActionType action) {
    }

    public static final class BurstPlan {
        public final List<BurstClick> clicks = new ArrayList<>();
        public final Map<Integer, ItemStack> predicted = new HashMap<>();
        public int crafts;
    }

    private static final int MAX_CRAFTS = 96;

    public static BurstPlan plan(MinecraftClient client, ScreenHandler handler, Slot focusedSlot, int hotbarIndex, ItemStack anchor) {
        try {
            if (client == null || client.player == null || client.world == null) return null;
            if (handler == null || focusedSlot == null || anchor == null || anchor.isEmpty()) return null;
            if (!handler.getCursorStack().isEmpty()) return null;
            if (hotbarIndex < 0 || hotbarIndex > 10) return null;
            // Sim hotbar-endekslidir: main-only (9) ve sol-el (10) burst'suzdur.
            // 9 drain'e duser, 10 tekil alimdir (controller guard).
            if (hotbarIndex > 8) return null;
            PlayerInventory inv = client.player.getInventory();

            // Sadece vanilya crafting handler'ları (yerleşim deterministik).
            // Masa: slot 0 output + 1-9 ızgara; envanter: slot 0 output + 1-4 ızgara.
            int gridFrom;
            int gridTo;
            int gridW;
            boolean isCrafting = handler instanceof CraftingScreenHandler
                || handler instanceof PlayerScreenHandler;
            int[] grid;
            boolean[] used;
            if (isCrafting) {
                if (handler instanceof CraftingScreenHandler) {
                    gridFrom = 1;
                    gridTo = 9;
                    gridW = 3;
                } else {
                    gridFrom = 1;
                    gridTo = 4;
                    gridW = 2;
                }
                if (handler.slots.size() < gridTo + 1) return null;
                if (VanillaClickExecutor.resolveSlotId(handler, focusedSlot) != 0) return null;
                ItemStack outNow = handler.getSlot(0).getStack();
                if (outNow.isEmpty() || outNow.getItem() != anchor.getItem()
                    || outNow.getCount() != anchor.getCount()
                    || !ItemStack.areItemsAndComponentsEqual(outNow, anchor)) {
                    // Odak dışı senkron kayması: güvenli yola düş.
                    return null;
                }
                // Izgara yerleşim doğrulaması (vanilya dışı yerleşim -> fallback).
                for (int i = gridFrom; i <= gridTo; i++) {
                    if (handler.getSlot(i).inventory == inv) return null;
                }
                grid = new int[gridTo - gridFrom + 1];
                used = new boolean[grid.length];
                for (int i = 0; i < grid.length; i++) {
                    ItemStack s = handler.getSlot(gridFrom + i).getStack();
                    grid[i] = s.isEmpty() ? 0 : s.getCount();
                    if (grid[i] > 0) used[i] = true;
                }
                // Tarif eşleşmesi + artık-ürün kontrolü (kova/su şişesi vb. -> fallback).
                CraftingInventory craftCopy = new CraftingInventory(handler, gridW, gridW == 3 ? 3 : 2);
                for (int i = 0; i < grid.length; i++) {
                    craftCopy.setStack(i, handler.getSlot(gridFrom + i).getStack().copy());
                }
                Optional<net.minecraft.recipe.RecipeEntry<CraftingRecipe>> match = client.world.getRecipeManager()
                    .getFirstMatch(RecipeType.CRAFTING, craftCopy, client.world);
                if (match.isEmpty()) return null;
                for (ItemStack rem : match.get().value().getRemainder(craftCopy)) {
                    if (!rem.isEmpty()) return null;
                }
            } else {
                // YOL B - genel output (taş kesici, demircilik, dokuma, modded):
                // output envanteri dışındaki oyuncu-dışı slotlar girdidir
                // (runtime gruplama, tablo yok). Tüketim 1:1 varsayımı TEMKİNLİDİR
                // (az sayar; fazlası drain ile gelir). Paylaşımlı envanter
                // (fırın) -> boş liste -> fallback. Örs/tüccar canTake kapısında
                // elenir (bulk'a hiç gelmez).
                List<Integer> inputIds = inputSlotIds(handler, focusedSlot, inv);
                if (inputIds == null || inputIds.isEmpty()) return null;
                grid = new int[inputIds.size()];
                used = new boolean[grid.length];
                for (int i = 0; i < inputIds.size(); i++) {
                    ItemStack s = handler.getSlot(inputIds.get(i)).getStack();
                    grid[i] = s.isEmpty() ? 0 : s.getCount();
                    if (grid[i] > 0) used[i] = true;
                }
            }
            // Temkinli craft sayısı: boşalmamış slotların min'i (çöp varsa eksik
            // sayar - güvenli yöne hata; fazlası drain ile gelir).
            int crafts = Integer.MAX_VALUE;
            for (int i = 0; i < grid.length; i++) {
                if (used[i]) crafts = Math.min(crafts, grid[i]);
            }
            if (crafts == Integer.MAX_VALUE || crafts < 2 || crafts > MAX_CRAFTS) return null;

            int outputRealId = VanillaClickExecutor.resolveSlotId(handler, focusedSlot);
            int hbRealId = VanillaClickExecutor.realSlotId(handler, inv, hotbarIndex);
            if (outputRealId < 0 || hbRealId < 0) return null;
            Map<Integer, Integer> realIds = new HashMap<>();
            for (int i = 0; i < 36; i++) realIds.put(i, VanillaClickExecutor.realSlotId(handler, inv, i));

            // Simülasyon durumu (kopyalar üzerinde).
            // SWAP YASASI (sunucu bytecode'undan, internalOnSlotClick):
            // - SWAP output<->BOŞ hb = alım (tüketim + refill).
            // - SWAP output<->DOLU hb = SESSİZ RED (canInsert=false). Asla ateşlenmez!
            // - PICKUP output = alım (tüketim + refill).
            // Yani output'a dokunan her GEÇERLİ alım tüketir; refill = sustain ? R : boş.
            // Yabancı-item output'a SADECE reddedilen paketle girebilirdi - artık
            // öyle paket YOK, o yüzden simOut hep çapa veya boştur.
            Map<Integer, ItemStack> sim = new HashMap<>();
            for (int i = 0; i < 36; i++) sim.put(i, inv.getStack(i).copy());
            ItemStack simOut = anchor.copy();
            int r = anchor.getCount();
            simOut.setCount(r);
            int max = Math.max(1, anchor.getMaxCount());
            BurstPlan plan = new BurstPlan();
            int takes = 0;

            for (int c = 0; c < crafts && takes < 160; c++) {
                // Output'ta çapa ürünü yoksa güvenli devam edilemez.
                if (simOut.isEmpty() || !isSame(simOut, anchor)) break;
                int fc = simOut.getCount();
                ItemStack oldHb = sim.get(hotbarIndex);
                boolean sameHb = !oldHb.isEmpty() && isSame(oldHb, anchor);
                // Executor ile AYNI karar sırası (survival dalı).
                if (oldHb.isEmpty() && outputRealId != hbRealId) {
                    // BOŞ hedefe SWAP = alım (tüketim + refill). Tek paket!
                    plan.clicks.add(new BurstClick(outputRealId, hotbarIndex, SlotActionType.SWAP));
                    ItemStack fc2 = anchor.copy();
                    fc2.setCount(fc);
                    sim.put(hotbarIndex, fc2);
                    consumeGrid(grid, used);
                    simOut = refillOrEmpty(grid, used, anchor, r);
                    plan.crafts++;
                    takes++;
                } else if (sameHb && fc <= max - oldHb.getCount() && outputRealId != hbRealId) {
                    // MERGE (bütün sığar).
                    plan.clicks.add(new BurstClick(outputRealId, 0, SlotActionType.PICKUP));
                    plan.clicks.add(new BurstClick(hbRealId, 0, SlotActionType.PICKUP));
                    ItemStack m = oldHb.copy();
                    m.setCount(oldHb.getCount() + fc);
                    sim.put(hotbarIndex, m);
                    consumeGrid(grid, used);
                    simOut = refillOrEmpty(grid, used, anchor, r);
                    plan.crafts++;
                    takes++;
                } else if (sameHb && max - oldHb.getCount() > 0 && outputRealId != hbRealId) {
                    // MERGE_FILL (kısmi + kalan). Bulk her zaman ON-kuralları.
                    int rem = fc - (max - oldHb.getCount());
                    int dest = findDest(sim, anchor, rem, hotbarIndex);
                    if (dest == -1 || realIds.get(dest) == -1) break;
                    plan.clicks.add(new BurstClick(outputRealId, 0, SlotActionType.PICKUP));
                    plan.clicks.add(new BurstClick(hbRealId, 0, SlotActionType.PICKUP));
                    plan.clicks.add(new BurstClick(realIds.get(dest), 0, SlotActionType.PICKUP));
                    ItemStack f = oldHb.copy();
                    f.setCount(max);
                    sim.put(hotbarIndex, f);
                    ItemStack de = sim.get(dest);
                    ItemStack dn = de.isEmpty() ? anchor.copy() : de.copy();
                    dn.setCount(de.isEmpty() ? rem : de.getCount() + rem);
                    sim.put(dest, dn);
                    consumeGrid(grid, used);
                    simOut = refillOrEmpty(grid, used, anchor, r);
                    plan.crafts++;
                    takes++;
                } else if (sameHb && oldHb.getCount() >= max && outputRealId != hbRealId) {
                    // OVERFLOW (hedef dolu, alım taşma hedefine). Bulk ON-kuralları.
                    int dest = findDest(sim, anchor, fc, hotbarIndex);
                    if (dest == -1 || realIds.get(dest) == -1) break;
                    plan.clicks.add(new BurstClick(outputRealId, 0, SlotActionType.PICKUP));
                    plan.clicks.add(new BurstClick(realIds.get(dest), 0, SlotActionType.PICKUP));
                    ItemStack de = sim.get(dest);
                    ItemStack dn = de.isEmpty() ? anchor.copy() : de.copy();
                    dn.setCount(de.isEmpty() ? fc : de.getCount() + fc);
                    sim.put(dest, dn);
                    consumeGrid(grid, used);
                    simOut = refillOrEmpty(grid, used, anchor, r);
                    plan.crafts++;
                    takes++;
                } else if (!oldHb.isEmpty() && outputRealId != hbRealId) {
                    // PRIORITY (bulk her zaman ON-kuralları): önce SADECE evict
                    // SWAP'ı (output'a dokunmaz), sonra boşalan hedefin alımı.
                    int empty = nearestEvict(sim, hotbarIndex);
                    if (empty == -1 || realIds.get(empty) == -1) break;
                    Integer emptyReal = realIds.get(empty);
                    if (empty < 9) {
                        plan.clicks.add(new BurstClick(hbRealId, empty, SlotActionType.SWAP));
                    } else {
                        plan.clicks.add(new BurstClick(emptyReal, hotbarIndex, SlotActionType.SWAP));
                    }
                    sim.put(empty, oldHb.copy());
                    sim.put(hotbarIndex, ItemStack.EMPTY);
                    takes++;
                } else {
                    // Düz SWAP dolu hedefe REDDEDİLİR (vanilla ile aynı) -> ateşleme,
                    // burst DURUR. Kalanı drain alır (taze okuma).
                    break;
                }
            }
            if (plan.crafts < 2) return null;
            plan.predicted.putAll(sim);
            return plan;
        } catch (Exception e) {
            BHSDebug.log("BURST abort (plan-hata " + e.getClass().getSimpleName() + ")");
            return null;
        }
    }

    public static void fire(MinecraftClient client, ScreenHandler handler, BurstPlan plan, int hotbarIndex) {
        if (client == null || client.interactionManager == null || plan == null) return;
        for (BurstClick c : plan.clicks) {
            client.interactionManager.clickSlot(handler.syncId, c.slotId(), c.button(), c.action(), client.player);
        }
        PlayerInventory inv = client.player.getInventory();
        for (Map.Entry<Integer, ItemStack> e : plan.predicted.entrySet()) {
            inv.setStack(e.getKey(), e.getValue().copy());
        }
        BHSDebug.log("BURST crafts=" + plan.crafts + " packets=" + plan.clicks.size() + " -> hb" + (hotbarIndex + 1));
    }

    private static void consumeGrid(int[] grid, boolean[] used) {
        for (int i = 0; i < grid.length; i++) {
            if (used[i]) grid[i]--;
        }
    }

    private static ItemStack refillOrEmpty(int[] grid, boolean[] used, ItemStack anchor, int r) {
        for (int i = 0; i < grid.length; i++) {
            if (used[i] && grid[i] <= 0) return ItemStack.EMPTY;
        }
        ItemStack s = anchor.copy();
        s.setCount(r);
        return s;
    }

    // Executor PRIORITY parity: once hotbar-ici bos (yakınlık), yoksa main 9->35 ilk bos.
    private static int nearestEvict(Map<Integer, ItemStack> sim, int target) {
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            if (i == target || !sim.get(i).isEmpty()) continue;
            int dist = Math.abs(i - target);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        if (best != -1) return best;
        for (int i = 9; i < 36; i++) {
            if (sim.get(i).isEmpty()) return i;
        }
        return -1;
    }

    // Runtime girdi-gruplama: odak output slotunun envanteri dışındaki,
    // oyuncu-dışı container slotları = girdiler (handler id listesi).
    // Paylaşımlı envanterde (fırın: girdi+output aynı) BOŞ liste döner.
    public static List<Integer> inputSlotIds(ScreenHandler handler, Slot outputSlot, PlayerInventory inv) {
        List<Integer> ids = new ArrayList<>();
        try {
            if (handler == null || outputSlot == null || outputSlot.inventory == null || inv == null) return ids;
            Object outInv = outputSlot.inventory;
            if (outInv == inv) return ids;
            for (Slot s : handler.slots) {
                if (s == null || s.inventory == null) continue;
                if (s == outputSlot) continue;
                if (s.inventory == inv || s.inventory == outInv) continue;
                ids.add(s.id);
            }
        } catch (Exception ignored) {
        }
        return ids;
    }

    // Executor ile aynı sıra: aynı-item yeterli boşluk (hotbar-yakın, main),
    // sonra boş (en-yakın hotbar, main). Odak output olduğu için exclude=-1.
    private static int findDest(Map<Integer, ItemStack> sim, ItemStack anchor, int need, int target) {        for (int d = 1; d < 9; d++) {
            for (int cand : new int[]{target - d, target + d}) {
                if (cand < 0 || cand > 8 || cand == target) continue;
                ItemStack s = sim.get(cand);
                if (!isSame(s, anchor)) continue;
                if (s.getCount() + need <= s.getMaxCount()) return cand;
            }
        }
        for (int i = 9; i < 36; i++) {
            ItemStack s = sim.get(i);
            if (!isSame(s, anchor)) continue;
            if (s.getCount() + need <= s.getMaxCount()) return i;
        }
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            if (i == target || !sim.get(i).isEmpty()) continue;
            int dist = Math.abs(i - target);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        if (best != -1) return best;
        for (int i = 9; i < 36; i++) {
            if (sim.get(i).isEmpty()) return i;
        }
        return -1;
    }

    private static int nearestEmpty(Map<Integer, ItemStack> sim, int target) {
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            if (i == target || !sim.get(i).isEmpty()) continue;
            int dist = Math.abs(i - target);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        if (best != -1) return best;
        for (int i = 9; i < 36; i++) {
            if (sim.get(i).isEmpty()) return i;
        }
        return -1;
    }

    private static boolean isSame(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;
        return ItemStack.areItemsAndComponentsEqual(a, b);
    }
}
