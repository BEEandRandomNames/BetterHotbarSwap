package com.betterhotbarswap.client.input;

import com.betterhotbarswap.BetterHotbarSwap;
import com.betterhotbarswap.client.debug.BHSDebug;
import com.betterhotbarswap.config.BHSConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class VanillaClickExecutor {
    private static int creativeActionSeq = 0;
    private static void runNextTick(Runnable r) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) client.execute(r);
    }
    private static int getRealSlotId(ScreenHandler handler, PlayerInventory inv, int invIndex) {
        for (Slot s : handler.slots) if (s.inventory == inv && s.getIndex() == invIndex) return s.id;
        return -1;
    }
    private static int getMappedSlotId(ScreenHandler handler, Slot slot) {
        if (slot == null || slot.inventory == null) return -1;
        for (Slot s : handler.slots) if (s.inventory == slot.inventory && s.getIndex() == slot.getIndex()) return s.id;
        return -1;
    }
    // ShiftBulkQuickMove için açık köprüler (bug #9 dersi: id'ye değil (env,index)'e güven).
    public static int resolveSlotId(ScreenHandler handler, Slot slot) {
        return getMappedSlotId(handler, slot);
    }
    public static int realSlotId(ScreenHandler handler, PlayerInventory inv, int invIndex) {
        return getRealSlotId(handler, inv, invIndex);
    }
    // Handler id'leri güvenilir mi? (Creative envanter sekmesi listeyi yıkıp yeniden
    // kurduğu için tüm id'ler 0 kalır -> güvenilmez.)
    public static boolean idsTrustworthy(ScreenHandler handler) {
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (Slot s : handler.slots) if (!seen.add(s.id)) return false;
        return true;
    }
    // Creative envanter sekmesinde vanilla ClickSlot paketi GÖNDERMEZ: tıklamayı oyuncunun
    // handler'ında yerel yapar, sonucu creative paketiyle bildirir (sendContentUpdates ->
    // CreativeInventoryListener). ClickSlot'un sunucu ara-senkronu (çok adımlı öncelik
    // zinciri) dinleyicinin bayat izleme kopyasıyla karışıp kopya üretiyordu (#45).
    private static void click(MinecraftClient client, ScreenHandler handler, int id, int button, SlotActionType type) {
        if (handler instanceof CreativeInventoryScreen.CreativeScreenHandler && !idsTrustworthy(handler)) {
            client.player.playerScreenHandler.onSlotClick(id, button, type, client.player);
            client.player.playerScreenHandler.sendContentUpdates();
        } else {
            client.interactionManager.clickSlot(handler.syncId, id, button, type, client.player);
        }
    }
    // Evrensel stacklenebilirlik: aynı item + aynı NBT (max sayı item'dan okunur).
    private static boolean isSameStackable(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;
        return java.util.Objects.equals(a.getNbt(), b.getNbt());
    }
    // Oyuncu slotunun standart server pozisyonu (PlayerScreenHandler düzeni):
    // hotbar 36-44, main 9-35, zırh 36-39 -> 8,7,6,5, offhand 40 -> 45.
    private static int playerStandardId(int invIndex) {
        if (invIndex >= 0 && invIndex < 9) return 36 + invIndex;
        if (invIndex >= 9 && invIndex < 36) return invIndex;
        if (invIndex >= 36 && invIndex < 40) return 5 + (39 - invIndex);
        if (invIndex == 40) return 45;
        return -1;
    }
    // Envanter-sekmesi wrapper pozisyonu -> gerçek player index (tahmin yazımları için).
    // Wrapper indexleri kaynak pozisyondur: 5-8 zırh(39-36), 9-35 main, 36-44 hotbar(0-8), 45 offhand(40).
    private static int wrapperPosToTrueIdx(int pos) {
        if (pos >= 5 && pos <= 8) return 44 - pos;
        if (pos >= 9 && pos <= 35) return pos;
        if (pos >= 36 && pos <= 44) return pos - 36;
        if (pos == 45) return 40;
        return -1;
    }
    private static String snapshot(PlayerInventory inv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack s = inv.main.get(i);
            if (!s.isEmpty()) {
                try {
                    sb.append(i).append('=').append(s.getName().getString()).append('x').append(s.getCount()).append(';');
                } catch (Exception e) {
                    sb.append(i).append("=?;");
                }
            }
        }
        if (!inv.offHand.get(0).isEmpty()) {
            try {
                sb.append("off=").append(inv.offHand.get(0).getName().getString()).append(';');
            } catch (Exception e) {
                sb.append("off=?;");
            }
        }
        return sb.length() == 0 ? "empty" : sb.toString();
    }
    // Hotbar Önceliği: hedef hariç hedefe en yakın boş hotbar slotu. Eşitlikte küçük index.
    private static int nearestEmptyHotbar(PlayerInventory inv, int targetIndex) {
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            if (i == targetIndex || !inv.getStack(i).isEmpty()) continue;
            int dist = Math.abs(i - targetIndex);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }
    // Yerinden çıkan eşya (kullanıcı kuralı, 2026-10-21): "oyuncu bunu hotbar'da
    // tutuyorsa bir sebebi vardır" -> ENVANTERE GİTMEZ. Önce hotbar'daki aynı
    // eşya yığınlarıyla birleşir (hedefe yakın olan önce), sığmayan kalan en
    // yakın boş hotbar slotuna. Hotbar'da yer yoksa -1/-2 döner, çağıran eski
    // davranışına düşer. Dönüş: -2 = sığmaz, -1 = boş slot gerekmez, >=0 = kullanılacak boş slot.
    public static int planEvictWithinHotbar(PlayerInventory inv, int target) {
        ItemStack old = inv.getStack(target);
        if (old.isEmpty()) return -1;
        int room = 0;
        if (old.getMaxCount() > 1) {
            for (int j = 0; j < 9; j++) {
                if (j == target) continue;
                ItemStack s = inv.getStack(j);
                if (isSameStackable(s, old)) room += Math.max(0, s.getMaxCount() - s.getCount());
            }
        }
        if (room >= old.getCount()) return -1;
        int e = nearestEmptyHotbar(inv, target);
        return e == -1 ? -2 : e;
    }

    // planEvictWithinHotbar'ı uygular: hedef BOŞALIR, eski eşya hotbar'da kalır.
    // İmleç boş başlamalı; zincir sonunda imleç boştur (kapasite önceden hesaplı).
    // idOf: inv index -> paket slot id'si (güvenilmez-id düzeninde 36+i).
    public static boolean evictWithinHotbar(MinecraftClient client, ScreenHandler handler, PlayerInventory inv,
                                            int target, java.util.function.IntUnaryOperator idOf) {
        int plan = planEvictWithinHotbar(inv, target);
        if (plan == -2) return false;
        ItemStack old = inv.getStack(target).copy();
        if (old.isEmpty()) return true;
        int targetId = idOf.applyAsInt(target);
        if (targetId < 0) return false;
        java.util.List<Integer> merges = new java.util.ArrayList<>();
        int left = old.getCount();
        if (old.getMaxCount() > 1) {
            for (int d = 1; d < 9 && left > 0; d++) {
                for (int j : new int[]{target - d, target + d}) {
                    if (j < 0 || j > 8 || left <= 0) continue;
                    ItemStack s = inv.getStack(j);
                    int r = isSameStackable(s, old) ? s.getMaxCount() - s.getCount() : 0;
                    if (r <= 0 || idOf.applyAsInt(j) < 0) continue;
                    merges.add(j);
                    left -= Math.min(r, left);
                }
            }
        }
        if (left > 0 && (plan < 0 || idOf.applyAsInt(plan) < 0)) return false;
        if (merges.isEmpty()) {
            // Birleşme yok: tek SWAP (imleçsiz).
            click(client, handler, targetId, plan, SlotActionType.SWAP);
            inv.setStack(plan, old);
        } else {
            click(client, handler, targetId, 0, SlotActionType.PICKUP);
            int carry = old.getCount();
            for (int j : merges) {
                ItemStack s = inv.getStack(j).copy();
                int mv = Math.min(s.getMaxCount() - s.getCount(), carry);
                click(client, handler, idOf.applyAsInt(j), 0, SlotActionType.PICKUP);
                s.setCount(s.getCount() + mv);
                inv.setStack(j, s);
                carry -= mv;
            }
            if (carry > 0) {
                click(client, handler, idOf.applyAsInt(plan), 0, SlotActionType.PICKUP);
                ItemStack rest = old.copy();
                rest.setCount(carry);
                inv.setStack(plan, rest);
            }
        }
        inv.setStack(target, ItemStack.EMPTY);
        BHSDebug.log("EVICT hb" + (target + 1) + " x" + old.getCount() + " -> "
            + (merges.isEmpty() ? "" : "stack" + merges) + (left > 0 || merges.isEmpty() ? " bos hb" + (plan + 1) : ""));
        return true;
    }

    // Evict tasması: hotbar doluysa main 9->35 ilk boş (drain ile parite).
    private static int firstEmptyMain(PlayerInventory inv) {
        for (int i = 9; i < 36; i++) if (inv.getStack(i).isEmpty()) return i;
        return -1;
    }
    // Sol-el tasması: önce boş hb 0->8.
    private static int firstEmptyHotbar(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) if (inv.getStack(i).isEmpty()) return i;
        return -1;
    }
    // Zincir için ilk dolu hb 0->8 (main-evict ile boşaltılacak).
    private static int firstOccupiedHotbar(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) if (!inv.getStack(i).isEmpty()) return i;
        return -1;
    }
    // Sol-el kalan sırası: aynı-item hb 0->8, aynı-item main 9->35,
    // boş hb 0->8, boş main 9->35 (önce-hotbar-sonra-main kuralı).
    private static int findOffhandDest(PlayerInventory inv, ItemStack stack, int need, int exclude) {
        for (int i = 0; i < 9; i++) {
            if (i == exclude) continue;
            ItemStack s = inv.getStack(i);
            if (!isSameStackable(s, stack)) continue;
            if (s.getCount() + need <= s.getMaxCount()) return i;
        }
        for (int i = 9; i < 36; i++) {
            if (i == exclude) continue;
            ItemStack s = inv.getStack(i);
            if (!isSameStackable(s, stack)) continue;
            if (s.getCount() + need <= s.getMaxCount()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (i == exclude) continue;
            if (inv.getStack(i).isEmpty()) return i;
        }
        for (int i = 9; i < 36; i++) {
            if (i == exclude) continue;
            if (inv.getStack(i).isEmpty()) return i;
        }
        return -1;
    }
    // Bayrak-kapalı sol-el: sadece main (hotbar'a dokunulmaz).
    private static int findOffhandDestMain(PlayerInventory inv, ItemStack stack, int need, int exclude) {
        for (int i = 9; i < 36; i++) {
            if (i == exclude) continue;
            ItemStack s = inv.getStack(i);
            if (!isSameStackable(s, stack)) continue;
            if (s.getCount() + need <= s.getMaxCount()) return i;
        }
        for (int i = 9; i < 36; i++) {
            if (i == exclude) continue;
            if (inv.getStack(i).isEmpty()) return i;
        }
        return -1;
    }
    // Kısmi birleştirme için kalanın gideceği yer:
    // 1. aynı item+NBT'de yeterli boşluğu olan (hedef hariç, odakta hariç):
    //    hotbar yakınlık, sonra main 9->35
    // 2. boş: en yakın boş hotbar, sonra main 9->35 ilk boş
    // Yoksa -1 (fallback: eski zincir, imleç riske atılmaz).
    private static int findRemainderDest(PlayerInventory inv, ItemStack remainderStack, int remainderCount,
                                         int targetHotbarIdx, int excludeInvIdx) {
        // 1. aynı stackte yeterli boşluk
        for (int d = 1; d < 9; d++) {
            for (int cand : new int[]{targetHotbarIdx - d, targetHotbarIdx + d}) {
                if (cand < 0 || cand > 8 || cand == targetHotbarIdx || cand == excludeInvIdx) continue;
                ItemStack s = inv.getStack(cand);
                if (!isSameStackable(s, remainderStack)) continue;
                if (s.getCount() + remainderCount <= s.getMaxCount()) return cand;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (i == excludeInvIdx) continue;
            ItemStack s = inv.getStack(i);
            if (!isSameStackable(s, remainderStack)) continue;
            if (s.getCount() + remainderCount <= s.getMaxCount()) return i;
        }
        // 2. boş yer
        int emptyHb = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            if (i == targetHotbarIdx || i == excludeInvIdx || !inv.getStack(i).isEmpty()) continue;
            int dist = Math.abs(i - targetHotbarIdx);
            if (dist < bestDist) {
                bestDist = dist;
                emptyHb = i;
            }
        }
        if (emptyHb != -1) return emptyHb;
        for (int i = 9; i < 36; i++) {
            if (i == excludeInvIdx) continue;
            if (inv.getStack(i).isEmpty()) return i;
        }
        return -1;
    }
    // Hotbar Önceliği KAPALI anlamı: hotbar'a dokunma (hedef hariç), her şey
    // main'e (aynı-item boşluk 9->35, sonra boş 9->35). SWAP yasası gereği
    // kapalıyken vanilya-tek-SWAP dolu hedefte no-op olurdu (ölü ayar); main
    // düzeni ayarı çalışır kılar.
    static int findRemainderDestMainFirst(PlayerInventory inv, ItemStack remainderStack, int remainderCount,
                                          int excludeInvIdx) {
        for (int i = 9; i < 36; i++) {
            if (i == excludeInvIdx) continue;
            ItemStack s = inv.getStack(i);
            if (!isSameStackable(s, remainderStack)) continue;
            if (s.getCount() + remainderCount <= s.getMaxCount()) return i;
        }
        for (int i = 9; i < 36; i++) {
            if (i == excludeInvIdx) continue;
            if (inv.getStack(i).isEmpty()) return i;
        }
        return -1;
    }
    public static boolean assignSlotToHotbar(ScreenHandler handler, Slot focusedSlot, int hotbarIndex, boolean isFromCreativeList) {
        return assignSlotToHotbar(handler, focusedSlot, hotbarIndex, isFromCreativeList, false);
    }

    // Main-only alım ("0" modu): görünen stack main 35->9 sırasına alınır
    // (önce aynı-item whole-fit kısmi, sonra boş). Hotbar'a DOKUNULMAZ.
    // PICKUP çifti + mutlak tahmin (imleç-güvenli). Yer yoksa false.
    static boolean assignToMainOnly(ScreenHandler handler, Slot focusedSlot, int focusedSlotId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null) return false;
        PlayerInventory playerInv = client.player.getInventory();
        ItemStack focusedStack = focusedSlot.getStack().copy();
        if (focusedStack.isEmpty()) return false;
        int max = Math.max(1, focusedStack.getMaxCount());
        if (focusedStack.getCount() > max) return false;
        int dest = -1;
        for (int pass = 0; pass < 2 && dest == -1; pass++) {
            for (int i = 35; i >= 9; i--) {
                ItemStack s = playerInv.getStack(i);
                if (pass == 0) {
                    if (s.isEmpty() || !isSameStackable(s, focusedStack)) continue;
                    if (s.getCount() >= max) continue;
                    if (s.getCount() + focusedStack.getCount() > max) continue;
                } else {
                    if (!s.isEmpty()) continue;
                }
                int realId = getRealSlotId(handler, playerInv, i);
                if (realId == -1) continue;
                dest = i;
                break;
            }
        }
        if (dest == -1) return false;
        ItemStack destOld = playerInv.getStack(dest).copy();
        client.interactionManager.clickSlot(handler.syncId, focusedSlotId, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(handler.syncId, getRealSlotId(handler, playerInv, dest), 0, SlotActionType.PICKUP, client.player);
        ItemStack grown = destOld.isEmpty() ? focusedStack.copy() : destOld.copy();
        grown.setCount(destOld.isEmpty() ? focusedStack.getCount() : destOld.getCount() + focusedStack.getCount());
        playerInv.setStack(dest, grown);
        if (focusedSlot.inventory == playerInv) {
            playerInv.setStack(focusedSlot.getIndex(), ItemStack.EMPTY);
        }
        BHSDebug.log("MAIN-ONLY sync=" + handler.syncId + " slot=" + focusedSlotId
            + " +" + focusedStack.getCount() + " -> m" + dest);
        return true;
    }

    // Sol-el (offhand, inv 40) alımı ("F" modu): paket düğmesi 40'tır
    // (vanilla handleHotbarKeyPressed paritesi). Hotbar zinciriyle aynı sıra:
    // MERGE whole-fit -> MERGE_FILL kısmi -> OVERFLOW dolu-hedef -> PRIORITY
    // evict (önce boş hb 0->8, sonra main 9->35) -> boş-hedef SWAP.
    // Yer yoksa false (ölü paket atılmaz, vanilla no-op'a düşer).
    static boolean assignToOffhand(ScreenHandler handler, Slot focusedSlot, int focusedSlotId, boolean prio) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null) return false;
        PlayerInventory playerInv = client.player.getInventory();
        ItemStack focusedStack = focusedSlot.getStack().copy();
        if (focusedStack.isEmpty()) return false;
        int max = Math.max(1, focusedStack.getMaxCount());
        if (focusedStack.getCount() > max) return false;
        boolean fromPlayer = SlotClassifier.isPlayerInventorySlot(focusedSlot, client.player);
        ItemStack oldOff = playerInv.getStack(40).copy();
        int offRealId = getRealSlotId(handler, playerInv, 40);
        // Self: vanilla no-op paritesi.
        if (offRealId != -1 && focusedSlotId == offRealId) return false;
        // Slotsuz yol (tezgah/fırın/sandık: offhand slotu listede YOKTUR, düğme 40
        // server'da index-tabanlı çözülür - vanilla F paritesi).
        if (offRealId == -1) {
            return assignToOffhandSlotless(handler, focusedSlot, focusedSlotId, prio, fromPlayer, oldOff);
        }
        Slot target = handler.getSlot(focusedSlotId);
        int exclude = (target.inventory == playerInv) ? target.getIndex() : -1;
        if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] BEFORE {}", snapshot(playerInv));
        // Aynı item: MERGE whole-fit / MERGE_FILL kısmi / OVERFLOW dolu.
        if (!oldOff.isEmpty() && focusedStack.getMaxCount() > 1 && isSameStackable(focusedStack, oldOff)) {
            int space = oldOff.getMaxCount() - oldOff.getCount();
            if (space > 0) {
                int remainder = focusedStack.getCount() - Math.min(space, focusedStack.getCount());
                if (remainder == 0) {
                    ItemStack merged = oldOff.copy();
                    merged.setCount(oldOff.getCount() + focusedStack.getCount());
                    BHSDebug.log("OFFHAND MERGE sync=" + handler.syncId + " slot " + focusedSlotId
                        + " -> off x" + merged.getCount());
                    client.interactionManager.clickSlot(handler.syncId, focusedSlotId, 0, SlotActionType.PICKUP, client.player);
                    client.interactionManager.clickSlot(handler.syncId, offRealId, 0, SlotActionType.PICKUP, client.player);
                    playerInv.setStack(40, merged);
                    if (target.inventory == playerInv) playerInv.setStack(target.getIndex(), ItemStack.EMPTY);
                    if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
                    return true;
                }
                int dest = prio ? findOffhandDest(playerInv, focusedStack, remainder, exclude)
                    : findOffhandDestMain(playerInv, focusedStack, remainder, exclude);
                int destReal = dest == -1 ? -1 : getRealSlotId(handler, playerInv, dest);
                if (destReal != -1) {
                    ItemStack destExisting = playerInv.getStack(dest).copy();
                    ItemStack filledOff = oldOff.copy();
                    filledOff.setCount(filledOff.getMaxCount());
                    ItemStack destNew = destExisting.isEmpty() ? focusedStack.copy() : destExisting.copy();
                    destNew.setCount(destExisting.isEmpty() ? remainder : destExisting.getCount() + remainder);
                    BHSDebug.log("OFFHAND MERGE_FILL sync=" + handler.syncId + " slot " + focusedSlotId
                        + " -> off x" + filledOff.getCount() + " + kalan x" + remainder + " -> " + slotName(dest));
                    client.interactionManager.clickSlot(handler.syncId, focusedSlotId, 0, SlotActionType.PICKUP, client.player);
                    client.interactionManager.clickSlot(handler.syncId, offRealId, 0, SlotActionType.PICKUP, client.player);
                    client.interactionManager.clickSlot(handler.syncId, destReal, 0, SlotActionType.PICKUP, client.player);
                    playerInv.setStack(40, filledOff);
                    playerInv.setStack(dest, destNew);
                    if (target.inventory == playerInv && target.getIndex() != dest) {
                        playerInv.setStack(target.getIndex(), ItemStack.EMPTY);
                    }
                    if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
                    return true;
                }
            }
            if (oldOff.getCount() >= oldOff.getMaxCount()) {
                int dest = prio ? findOffhandDest(playerInv, focusedStack, focusedStack.getCount(), exclude)
                    : findOffhandDestMain(playerInv, focusedStack, focusedStack.getCount(), exclude);
                int destReal = dest == -1 ? -1 : getRealSlotId(handler, playerInv, dest);
                if (destReal != -1) {
                    ItemStack destExisting = playerInv.getStack(dest).copy();
                    ItemStack destNew = destExisting.isEmpty() ? focusedStack.copy() : destExisting.copy();
                    destNew.setCount(destExisting.isEmpty() ? focusedStack.getCount() : destExisting.getCount() + focusedStack.getCount());
                    BHSDebug.log("OFFHAND OVERFLOW sync=" + handler.syncId + " slot " + focusedSlotId
                        + " +" + focusedStack.getCount() + " -> " + slotName(dest) + " (off korundu)");
                    client.interactionManager.clickSlot(handler.syncId, focusedSlotId, 0, SlotActionType.PICKUP, client.player);
                    client.interactionManager.clickSlot(handler.syncId, destReal, 0, SlotActionType.PICKUP, client.player);
                    playerInv.setStack(dest, destNew);
                    if (target.inventory == playerInv && target.getIndex() != dest) {
                        playerInv.setStack(target.getIndex(), ItemStack.EMPTY);
                    }
                    if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
                    return true;
                }
            }
        }
        // PRIORITY evict: eski önce boş hb'ye, yoksa main'e; sonra alım.
        if (prio && !oldOff.isEmpty()) {
            int emptyHb = firstEmptyHotbar(playerInv);
            if (emptyHb != -1) {
                int emptyReal = getRealSlotId(handler, playerInv, emptyHb);
                if (emptyReal != -1) {
                    BHSDebug.log("OFFHAND PRIORITY sync=" + handler.syncId + " eski off -> hb" + (emptyHb + 1)
                        + ", sonra slot " + focusedSlotId + " -> off");
                    client.interactionManager.clickSlot(handler.syncId, offRealId, emptyHb, SlotActionType.SWAP, client.player);
                    client.interactionManager.clickSlot(handler.syncId, focusedSlotId, 40, SlotActionType.SWAP, client.player);
                    if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
                    return true;
                }
            }
            int mainEmpty = firstEmptyMain(playerInv);
            int mainReal = mainEmpty == -1 ? -1 : getRealSlotId(handler, playerInv, mainEmpty);
            if (mainReal != -1) {
                BHSDebug.log("OFFHAND PRIORITY sync=" + handler.syncId + " eski off -> m" + mainEmpty
                    + ", sonra slot " + focusedSlotId + " -> off");
                client.interactionManager.clickSlot(handler.syncId, mainReal, 40, SlotActionType.SWAP, client.player);
                client.interactionManager.clickSlot(handler.syncId, focusedSlotId, 40, SlotActionType.SWAP, client.player);
                if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
                return true;
            }
        }
        // Oyuncu-içi hamlede vanilya takas her zaman yasaldır; output->dolu
        // offhand'de ölü paket atılmaz.
        if (!oldOff.isEmpty() && !fromPlayer) {
            BHSDebug.log("OFFHAND FULL sync=" + handler.syncId + " (yer yok)");
            return false;
        }
        BHSDebug.log("OFFHAND SWAP sync=" + handler.syncId + " slot=" + focusedSlotId + " -> off");
        client.interactionManager.clickSlot(handler.syncId, focusedSlotId, 40, SlotActionType.SWAP, client.player);
        if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
        return true;
    }

    // Slotsuz offhand yolu (container ekranları): listedeki offhand slotu yoktur,
    // düğme 40 server'da envanter-indexiyle çözülür (vanilla F paritesi).
    // Hepsi yasal hamlelerdir: boş-hedef SWAP, oyuncu-ici SWAP, evict zinciri.
    static boolean assignToOffhandSlotless(ScreenHandler handler, Slot focusedSlot, int focusedSlotId,
                                           boolean prio, boolean fromPlayer, ItemStack oldOff) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null) return false;
        PlayerInventory playerInv = client.player.getInventory();
        if (focusedSlot.getStack().isEmpty()) return false;
        if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] BEFORE {}", snapshot(playerInv));
        if (oldOff.isEmpty() || fromPlayer) {
            BHSDebug.log("OFFHAND SWAP sync=" + handler.syncId + " slot=" + focusedSlotId + " -> off (slotsuz)");
            client.interactionManager.clickSlot(handler.syncId, focusedSlotId, 40, SlotActionType.SWAP, client.player);
            if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
            return true;
        }
        if (!prio) {
            BHSDebug.log("OFFHAND FULL sync=" + handler.syncId + " (bayrak kapalı, yer yok)");
            return false;
        }
        // Evict zinciri: önce boş hb'ye (2 paket), yoksa main-evict ile hb boşaltıp (3 paket).
        int emptyHb = firstEmptyHotbar(playerInv);
        if (emptyHb != -1) {
            int hbReal = getRealSlotId(handler, playerInv, emptyHb);
            if (hbReal == -1) return false;
            BHSDebug.log("OFFHAND CHAIN sync=" + handler.syncId + " slot " + focusedSlotId
                + " -> hb" + (emptyHb + 1) + " -> off");
            client.interactionManager.clickSlot(handler.syncId, focusedSlotId, emptyHb, SlotActionType.SWAP, client.player);
            client.interactionManager.clickSlot(handler.syncId, hbReal, 40, SlotActionType.SWAP, client.player);
            if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
            return true;
        }
        int mainEmpty = firstEmptyMain(playerInv);
        int mainReal = mainEmpty == -1 ? -1 : getRealSlotId(handler, playerInv, mainEmpty);
        int x = firstOccupiedHotbar(playerInv);
        int xReal = x == -1 ? -1 : getRealSlotId(handler, playerInv, x);
        if (mainReal == -1 || xReal == -1) {
            BHSDebug.log("OFFHAND FULL sync=" + handler.syncId + " (yer yok)");
            return false;
        }
        BHSDebug.log("OFFHAND CHAIN sync=" + handler.syncId + " hb" + (x + 1) + " -> m" + mainEmpty
            + ", slot " + focusedSlotId + " -> hb" + (x + 1) + " -> off");
        client.interactionManager.clickSlot(handler.syncId, mainReal, x, SlotActionType.SWAP, client.player);
        client.interactionManager.clickSlot(handler.syncId, focusedSlotId, x, SlotActionType.SWAP, client.player);
        client.interactionManager.clickSlot(handler.syncId, xReal, 40, SlotActionType.SWAP, client.player);
        if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
        return true;
    }

    private static String slotName(int idx) {
        if (idx == 40) return "off";
        return idx < 9 ? "hb" + (idx + 1) : "m" + idx;
    }

    // forcePriority: bulk drain kendi yerleşim sistemini dayatır (bayraktan
    // bağımsız). Normal alımlar false geçerek bayrağa uyar.
    public static boolean assignSlotToHotbar(ScreenHandler handler, Slot focusedSlot, int hotbarIndex, boolean isFromCreativeList, boolean forcePriority) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null) return false;
        if (isFromCreativeList) return false;
        if (focusedSlot == null) return false;
        // Modlu depo büyük yığını (count > item max): PICKUP/SWAP zincirleri
        // max varsayar, fazlası imleçte kalırdı -> vanilla yapsın.
        if (focusedSlot.getStack().getCount() > focusedSlot.getStack().getMaxCount()) {
            BHSDebug.log("VANILLA buyuk-yigin x" + focusedSlot.getStack().getCount());
            return false;
        }
        PlayerInventory playerInv = client.player.getInventory();
        // Bug #31: zirh (36-39) / offhand (40) kaynagi vanilla'ya birak (dupe vektoru).
        try {
            if (focusedSlot.inventory == playerInv) {
                int fIdx = focusedSlot.getIndex();
                if (fIdx >= 36 && fIdx <= 40) {
                    BHSDebug.log("VANILLA zirh/offhand kaynagi (vanilla yapsin)");
                    return false;
                }
            }
        } catch (Exception ignored) {}
        if (hotbarIndex < 0 || hotbarIndex > 10) return false;
        // Bayrak kapsamı (kullanıcı kararı): Hotbar Önceliği SADECE envanter-içi
        // alımlarda (oyuncu slotu -> hotbar) geçerlidir. Container'dan hotbar'a
        // atamada (output/ingredient/depo) HER ZAMAN tam sistem çalışır:
        // dolu hotbar'a kısayolla atama bayraktan bağımsızdır.
        boolean fromPlayerInv = SlotClassifier.isPlayerInventorySlot(focusedSlot, client.player);
        boolean prio = forcePriority || !fromPlayerInv || BHSConfig.hotbarPriority;
        // Odak slotun id'sine güvenme: handler'daki karşılığını (inventory, index) ile bul.
        // (Creative envanter sekmesinde focusedSlot.id handler ile uyuşmayabiliyor, bug #9.)
        int focusedSlotId = getMappedSlotId(handler, focusedSlot);
        if (focusedSlotId < 0 || focusedSlotId >= handler.slots.size()) {
            BHSDebug.log("NOMAP " + BHSDebug.describeSlot(focusedSlot, client.player)
                + " handler=" + handler.getClass().getSimpleName() + " slots=" + handler.slots.size() + " sync=" + handler.syncId);
            return false;
        }
        if (focusedSlot.id != focusedSlotId) {
            BHSDebug.log("REMAP rawId=" + focusedSlot.id + " -> " + focusedSlotId
                + " handler=" + handler.getClass().getSimpleName());
        }
        Slot target = handler.getSlot(focusedSlotId);
        // "0" modu (main-only): hotbar'a DOKUNULMAZ, main 35->9 sırasına alınır.
        // Evrensel max ile (getMaxCount), imleç-güvenli (whole-fit/boş).
        if (hotbarIndex == 9) return assignToMainOnly(handler, focusedSlot, focusedSlotId);
        // "F" modu (offhand): düğme 40 ile aynı zincir (MERGE/FILL/OVERFLOW/evict).
        if (hotbarIndex == 10) return assignToOffhand(handler, focusedSlot, focusedSlotId, prio);
        // Stack'i HER ZAMAN canlı odaktan oku (handler listesindeki karşılık bayat olabilir).
        ItemStack focusedStack = focusedSlot.getStack().copy();
        if (focusedStack.isEmpty()) {
            BHSDebug.log("EMPTY-FOCUS " + BHSDebug.describeSlot(focusedSlot, client.player));
            return false;
        }
        ItemStack oldHotbarStack = playerInv.getStack(hotbarIndex).copy();
        // Shift + Kısayol (bulk): output slotta Shift basılıyken görünen stack alınır,
        // taşan aynı-item stacklere / boş hotbar / main sırasına dağıtılır.
        // Config kapalıysa normal davranışa düşer. Yaratıcı + survival hepsi.
        boolean shiftBulk = false;
        try {
            shiftBulk = BHSConfig.shiftHotbarPickup && HotbarInputController.isShiftDown()
                && SlotClassifier.isProbableOutputSlot(focusedSlot, client.player,
                    handler instanceof CreativeInventoryScreen.CreativeScreenHandler);
        } catch (Exception ignored) {
        }

        // Güvenilmez-id düzeni (creative envanter sekmesi): odaktaki index zaten
        // server pozisyonudur. Vanilla-identical SWAP paketleri (+öncelik zinciri).
        // NOT: hotbar aramasından ÖNCE gelir (o arama bu düzende bulamaz).
        if (focusedSlot.inventory == playerInv && !idsTrustworthy(handler)) {
            int focusedPos = focusedSlot.getIndex();
            if (focusedPos < 0 || focusedPos > 45) {
                BHSDebug.log("BAD-POS idx=" + focusedPos + " " + BHSDebug.describeSlot(focusedSlot, client.player));
                return false;
            }
            int hotbarPos = 36 + hotbarIndex;
            int focusedTrue = wrapperPosToTrueIdx(focusedPos);
            if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] BEFORE {}", snapshot(playerInv));
            // Aynı item: önce BİRLEŞTİR (F3). Tamamı sığıyorsa 2 PICKUP ile tek
            // yığın; kısmen sığıyorsa hedef max'a tamamlanır + kalan dağıtılır
            // (60+6 -> 64 + kalan 2). Kalan için yer yoksa eski zincire düşer
            // (imleç asla dolu kalmaz). Shift+bulk aynı yolu kullanır.
            if (!oldHotbarStack.isEmpty() && focusedPos != hotbarPos
                && focusedStack.getMaxCount() > 1 && oldHotbarStack.getMaxCount() > 1
                && isSameStackable(focusedStack, oldHotbarStack)) {
                int space = oldHotbarStack.getMaxCount() - oldHotbarStack.getCount();
                if (space > 0) {
                    int transfer = Math.min(space, focusedStack.getCount());
                    int remainder = focusedStack.getCount() - transfer;
                    if (remainder == 0) {
                        ItemStack merged = oldHotbarStack.copy();
                        merged.setCount(oldHotbarStack.getCount() + focusedStack.getCount());
                        BHSDebug.log((shiftBulk ? "SHIFT_BULK " : "") + "MERGE sync=" + handler.syncId + " pos " + focusedPos + " + hb" + (hotbarIndex + 1)
                            + " -> hb" + (hotbarIndex + 1) + " x" + merged.getCount());
                        click(client, handler, focusedPos, 0, SlotActionType.PICKUP);
                        click(client, handler, hotbarPos, 0, SlotActionType.PICKUP);
                        playerInv.setStack(hotbarIndex, merged);
                        if (focusedTrue != -1) playerInv.setStack(focusedTrue, ItemStack.EMPTY);
                        if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
                        return true;
                    }
                    int destInv = findRemainderDest(playerInv, focusedStack, remainder, hotbarIndex, focusedTrue);
                    if (destInv != -1) {
                        int destPos = (destInv >= 0 && destInv < 9) ? 36 + destInv : destInv;
                        ItemStack destExisting = playerInv.getStack(destInv).copy();
                        ItemStack filledTarget = oldHotbarStack.copy();
                        filledTarget.setCount(filledTarget.getMaxCount());
                        ItemStack destNew = destExisting.isEmpty() ? focusedStack.copy() : destExisting.copy();
                        destNew.setCount(destExisting.isEmpty() ? remainder : destExisting.getCount() + remainder);
                        BHSDebug.log((shiftBulk ? "SHIFT_BULK " : "") + "MERGE_FILL sync=" + handler.syncId + " pos " + focusedPos
                            + " -> hb" + (hotbarIndex + 1) + " x" + filledTarget.getCount()
                            + " + kalan x" + remainder + " -> " + (destInv < 9 ? "hb" + (destInv + 1) : "m" + destInv));
                        click(client, handler, focusedPos, 0, SlotActionType.PICKUP);
                        click(client, handler, hotbarPos, 0, SlotActionType.PICKUP);
                        click(client, handler, destPos, 0, SlotActionType.PICKUP);
                        playerInv.setStack(hotbarIndex, filledTarget);
                        playerInv.setStack(destInv, destNew);
                        if (focusedTrue != -1 && focusedTrue != destInv) playerInv.setStack(focusedTrue, ItemStack.EMPTY);
                        if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
                        return true;
                    }
                }
            }
            // Odak zaten hotbardaysa (hb5 -> hb1 gibi): bu işlemde ZATEN boşalacağı
            // için eski direkt oraya gider, boş aranmaz (delik bırakılmaz, bug #16).
            boolean focusedIsHotbar = focusedPos >= 36 && focusedPos <= 44;
            // Hedef AYNI item'la DOLUysa: eskiyi oynatma (ping-pong yok), alımı
            // doğrudan taşma hedefine ver. PICKUP+CLICK, imleç-güvenli.
            if (focusedPos != hotbarPos && !focusedIsHotbar
                && focusedStack.getMaxCount() > 1
                && isSameStackable(focusedStack, oldHotbarStack)
                && oldHotbarStack.getCount() >= oldHotbarStack.getMaxCount()) {
                int destInv = prio
                    ? findRemainderDest(playerInv, focusedStack, focusedStack.getCount(), hotbarIndex, focusedTrue)
                    : findRemainderDestMainFirst(playerInv, focusedStack, focusedStack.getCount(), focusedTrue);
                if (destInv != -1) {
                    int destPos = (destInv >= 0 && destInv < 9) ? 36 + destInv : destInv;
                    ItemStack destExisting = playerInv.getStack(destInv).copy();
                    ItemStack destNew = destExisting.isEmpty() ? focusedStack.copy() : destExisting.copy();
                    destNew.setCount(destExisting.isEmpty() ? focusedStack.getCount() : destExisting.getCount() + focusedStack.getCount());
                    BHSDebug.log("OVERFLOW sync=" + handler.syncId + " pos " + focusedPos
                        + " +" + focusedStack.getCount() + " -> " + (destInv < 9 ? "hb" + (destInv + 1) : "m" + destInv)
                        + " (hb" + (hotbarIndex + 1) + " x" + oldHotbarStack.getMaxCount() + " korundu)");
                    click(client, handler, focusedPos, 0, SlotActionType.PICKUP);
                    click(client, handler, destPos, 0, SlotActionType.PICKUP);
                    playerInv.setStack(destInv, destNew);
                    if (focusedTrue != -1 && focusedTrue != destInv) playerInv.setStack(focusedTrue, ItemStack.EMPTY);
                    if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
                    return true;
                }
            }
            if (prio && focusedPos != hotbarPos && !focusedIsHotbar && !oldHotbarStack.isEmpty()) {
                // Eski: hotbar'daki aynı yığına / en yakın boş hotbar'a (envantere değil).
                if (evictWithinHotbar(client, handler, playerInv, hotbarIndex, i -> 36 + i)) {
                    BHSDebug.log("PRIORITY sync=" + handler.syncId + " eski hb" + (hotbarIndex + 1)
                        + " hotbar'da kaldi, sonra pos " + focusedPos + " -> hb" + (hotbarIndex + 1));
                    click(client, handler, focusedPos, hotbarIndex, SlotActionType.SWAP);
                    // Tahmin: sonraki basış senkronu beklemeden tutarlı okusun (bug #15).
                    playerInv.setStack(hotbarIndex, focusedStack);
                    if (focusedTrue != -1) playerInv.setStack(focusedTrue, ItemStack.EMPTY);
                    if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
                    return true;
                }
            }
            BHSDebug.log("SEND SWAP sync=" + handler.syncId + " pos=" + focusedPos + " -> hotbar" + (hotbarIndex + 1));
            click(client, handler, focusedPos, hotbarIndex, SlotActionType.SWAP);
            playerInv.setStack(hotbarIndex, focusedStack);
            if (focusedTrue != -1) playerInv.setStack(focusedTrue, oldHotbarStack);
            if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
            return true;
        }

        int hotbarRealSlotId = getRealSlotId(handler, playerInv, hotbarIndex);
        if (hotbarRealSlotId == -1) return false;

        if (!(handler instanceof CreativeInventoryScreen.CreativeScreenHandler)) {
            // Aynı item: önce BİRLEŞTİR (F3). Tamamı sığıyorsa 2 PICKUP ile tek
            // yığın; kısmen sığıyorsa hedef max'a tamamlanır + kalan aynı-item /
            // boş hotbar / main sırasına dağıtılır (60+6 -> 64 + 2). Kalan için
            // yer yoksa eski öncelik zincirine düşer (imleç riske atılmaz).
            if (!oldHotbarStack.isEmpty() && focusedSlotId != hotbarRealSlotId
                && focusedStack.getMaxCount() > 1 && oldHotbarStack.getMaxCount() > 1
                && isSameStackable(focusedStack, oldHotbarStack)) {
                int space = oldHotbarStack.getMaxCount() - oldHotbarStack.getCount();
                if (space > 0) {
                    int transfer = Math.min(space, focusedStack.getCount());
                    int remainder = focusedStack.getCount() - transfer;
                    if (remainder == 0) {
                        ItemStack merged = oldHotbarStack.copy();
                        merged.setCount(oldHotbarStack.getCount() + focusedStack.getCount());
                        BHSDebug.log((shiftBulk ? "SHIFT_BULK " : "") + "MERGE sync=" + handler.syncId + " slot " + focusedSlotId + " + hb" + (hotbarIndex + 1)
                            + " -> hb" + (hotbarIndex + 1) + " x" + merged.getCount());
                        if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] BEFORE {}", snapshot(playerInv));
                        client.interactionManager.clickSlot(handler.syncId, focusedSlotId, 0, SlotActionType.PICKUP, client.player);
                        client.interactionManager.clickSlot(handler.syncId, hotbarRealSlotId, 0, SlotActionType.PICKUP, client.player);
                        playerInv.setStack(hotbarIndex, merged);
                        if (target.inventory == playerInv) {
                            playerInv.setStack(target.getIndex(), ItemStack.EMPTY);
                        }
                        if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
                        return true;
                    }
                    int focusedExclude = (target.inventory == playerInv) ? target.getIndex() : -1;
                    int destInv = prio
                        ? findRemainderDest(playerInv, focusedStack, remainder, hotbarIndex, focusedExclude)
                        : findRemainderDestMainFirst(playerInv, focusedStack, remainder, focusedExclude);
                    if (destInv != -1) {
                        int destRealId = getRealSlotId(handler, playerInv, destInv);
                        if (destRealId != -1) {
                            ItemStack destExisting = playerInv.getStack(destInv).copy();
                            ItemStack filledTarget = oldHotbarStack.copy();
                            filledTarget.setCount(filledTarget.getMaxCount());
                            ItemStack destNew = destExisting.isEmpty() ? focusedStack.copy() : destExisting.copy();
                            destNew.setCount(destExisting.isEmpty() ? remainder : destExisting.getCount() + remainder);
                            BHSDebug.log((shiftBulk ? "SHIFT_BULK " : "") + "MERGE_FILL sync=" + handler.syncId + " slot " + focusedSlotId
                                + " -> hb" + (hotbarIndex + 1) + " x" + filledTarget.getCount()
                                + " + kalan x" + remainder + " -> " + (destInv < 9 ? "hb" + (destInv + 1) : "m" + destInv));
                            if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] BEFORE {}", snapshot(playerInv));
                            client.interactionManager.clickSlot(handler.syncId, focusedSlotId, 0, SlotActionType.PICKUP, client.player);
                            client.interactionManager.clickSlot(handler.syncId, hotbarRealSlotId, 0, SlotActionType.PICKUP, client.player);
                            client.interactionManager.clickSlot(handler.syncId, destRealId, 0, SlotActionType.PICKUP, client.player);
                            playerInv.setStack(hotbarIndex, filledTarget);
                            playerInv.setStack(destInv, destNew);
                            if (target.inventory == playerInv && target.getIndex() != destInv) {
                                playerInv.setStack(target.getIndex(), ItemStack.EMPTY);
                            }
                            if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
                            return true;
                        }
                    }
                }
            }
            if (shiftBulk) {
                BHSDebug.log("SHIFT_BULK sync=" + handler.syncId + " slot=" + focusedSlotId + " -> hb" + (hotbarIndex + 1)
                    + " x" + focusedStack.getCount() + " (gorunen stack, dagitim MERGE_FILL/PRIORITY ile)");
            }
            // Hotbar Önceliği AÇIK + hedef dolu + hotbar'da boş var:
            // önce eskiyi en yakın boş hotbar'a kaydır, sonra focused'u hedefe al.
            // Hotbar doluysa veya hedef boşsa: vanilla tek SWAP (yer değiştirme).
            // Odak zaten hotbardaysa bu işlemde ZATEN boşalacağı için direkt
            // takas olur, boş aranmaz (delik bırakılmaz, bug #16).
            boolean focusedIsHotbar = target.inventory == playerInv
                && target.getIndex() >= 0 && target.getIndex() < 9;
            int focusedExcludeS = (target.inventory == playerInv) ? target.getIndex() : -1;
            // Hedef AYNI item'la DOLUysa (örn. hb3=64, alım=6): eskiyi oynatma,
            // alımı doğrudan taşma hedefine ver (önce aynı-item boşluk, sonra boş
            // slot; evrensel max ile). PING-PONG YOK: 64'lük stack yerinde kalır.
            // PICKUP+CLICK, imleç-güvenli (kısmi hedefe tamamı sığma önkoşullu).
            if (focusedSlotId != hotbarRealSlotId && !focusedIsHotbar
                && focusedStack.getMaxCount() > 1
                && isSameStackable(focusedStack, oldHotbarStack)
                && oldHotbarStack.getCount() >= oldHotbarStack.getMaxCount()) {
                int destInv = prio
                    ? findRemainderDest(playerInv, focusedStack, focusedStack.getCount(), hotbarIndex, focusedExcludeS)
                    : findRemainderDestMainFirst(playerInv, focusedStack, focusedStack.getCount(), focusedExcludeS);
                if (destInv != -1) {
                    int destRealId = getRealSlotId(handler, playerInv, destInv);
                    if (destRealId != -1) {
                        ItemStack destExisting = playerInv.getStack(destInv).copy();
                        ItemStack destNew = destExisting.isEmpty() ? focusedStack.copy() : destExisting.copy();
                        destNew.setCount(destExisting.isEmpty() ? focusedStack.getCount() : destExisting.getCount() + focusedStack.getCount());
                        BHSDebug.log((shiftBulk ? "SHIFT_BULK " : "") + "OVERFLOW sync=" + handler.syncId + " slot " + focusedSlotId
                            + " +" + focusedStack.getCount() + " -> " + (destInv < 9 ? "hb" + (destInv + 1) : "m" + destInv)
                            + " (hb" + (hotbarIndex + 1) + " x" + oldHotbarStack.getMaxCount() + " korundu)");
                        if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] BEFORE {}", snapshot(playerInv));
                        client.interactionManager.clickSlot(handler.syncId, focusedSlotId, 0, SlotActionType.PICKUP, client.player);
                        client.interactionManager.clickSlot(handler.syncId, destRealId, 0, SlotActionType.PICKUP, client.player);
                        playerInv.setStack(destInv, destNew);
                        if (target.inventory == playerInv && target.getIndex() != destInv) {
                            playerInv.setStack(target.getIndex(), ItemStack.EMPTY);
                        }
                        if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
                        return true;
                    }
                }
            }
            if (prio && focusedSlotId != hotbarRealSlotId && !focusedIsHotbar && !playerInv.getStack(hotbarIndex).isEmpty()) {
                // Eski: hotbar'daki aynı yığına / en yakın boş hotbar'a (envantere değil).
                if (evictWithinHotbar(client, handler, playerInv, hotbarIndex, i -> getRealSlotId(handler, playerInv, i))) {
                    BHSDebug.log("PRIORITY sync=" + handler.syncId + " eski hb" + (hotbarIndex + 1)
                        + " hotbar'da kaldi, sonra slot " + focusedSlotId + " -> hb" + (hotbarIndex + 1));
                    client.interactionManager.clickSlot(handler.syncId, focusedSlotId, hotbarIndex, SlotActionType.SWAP, client.player);
                    return true;
                }
                // Main-evict SADECE container/output: oyuncu-ici hamlede hotbar
                // doluysa vanilla takas olur (F4-194; #27 daraltmasi, #29).
                boolean fromPlayerMove = target.inventory == playerInv;
                if (!fromPlayerMove) {
                    int mainEmpty = firstEmptyMain(playerInv);
                    int mainRealId = mainEmpty == -1 ? -1 : getRealSlotId(handler, playerInv, mainEmpty);
                    if (mainRealId != -1) {
                        BHSDebug.log("PRIORITY sync=" + handler.syncId + " eski hb" + (hotbarIndex + 1)
                            + " -> m" + mainEmpty + ", sonra slot " + focusedSlotId + " -> hb" + (hotbarIndex + 1));
                        client.interactionManager.clickSlot(handler.syncId, mainRealId, hotbarIndex, SlotActionType.SWAP, client.player);
                        client.interactionManager.clickSlot(handler.syncId, focusedSlotId, hotbarIndex, SlotActionType.SWAP, client.player);
                        return true;
                    }
                }
            }
            BHSDebug.log("SEND SWAP sync=" + handler.syncId + " slot=" + focusedSlotId + " -> hotbar" + (hotbarIndex + 1));
            client.interactionManager.clickSlot(handler.syncId, focusedSlotId, hotbarIndex, SlotActionType.SWAP, client.player);
            return true;
        }
        // Creative liste sekmesi hotbar satırı (ulaşılması zor, yedek yol):
        // paketler survival-layout id ile gider (bug #5 dersi).
        if (target.getIndex() < 0 || target.getIndex() >= 36) {
            BHSDebug.log("PASS creative-player-slot idx=" + target.getIndex() + " (vanilla yapsin)");
            return false;
        }
        int hotbarStdId = playerStandardId(hotbarIndex);
        int focusedStdId = playerStandardId(target.getIndex());
        if (hotbarStdId == -1 || focusedStdId == -1) return false;
        playerInv.setStack(hotbarIndex, focusedStack);
        playerInv.setStack(target.getIndex(), oldHotbarStack);
        client.interactionManager.clickCreativeStack(focusedStack, hotbarStdId);
        BHSDebug.log("SEND creative-stack " + focusedStack.getName().getString() + "x" + focusedStack.getCount()
            + " -> hotbar" + (hotbarIndex + 1) + " (stdSlot=" + hotbarStdId + ")");
        final int actionId = ++creativeActionSeq;
        runNextTick(() -> {
            // Eski basışın gecikmiş gönderisi yeni durumu ezmesin.
            if (actionId != creativeActionSeq) {
                BHSDebug.log("SKIP stale next-tick id=" + actionId);
                return;
            }
            if (client.player == null || client.interactionManager == null) return;
            if (handler != client.player.currentScreenHandler) return;
            // Bug #32: hedef slot kullanici tarafindan degistirildiyse (Mouse Tweaks
            // dahil hizli tasma) eskiyi geri yazma.
            try {
                ItemStack curT = client.player.getInventory().getStack(target.getIndex());
                if (!curT.isEmpty() && (curT.getItem() != oldHotbarStack.getItem()
                    || !java.util.Objects.equals(curT.getNbt(), oldHotbarStack.getNbt()))) {
                    BHSDebug.log("SKIP stale next-tick replaced id=" + actionId);
                    return;
                }
                if (curT.isEmpty() && oldHotbarStack.isEmpty()) return;
                if (curT.isEmpty() && !oldHotbarStack.isEmpty()) {
                    BHSDebug.log("SKIP next-tick moved-away id=" + actionId);
                    return;
                }
            } catch (Exception ignored) {}
            int secondStdId = playerStandardId(target.getIndex());
            if (secondStdId != -1) {
                client.player.getInventory().setStack(target.getIndex(), oldHotbarStack);
                client.interactionManager.clickCreativeStack(oldHotbarStack, secondStdId);
            }
        });
        return true;
    }
}