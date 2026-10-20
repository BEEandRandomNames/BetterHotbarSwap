package com.betterhotbarswap.mixin;

import com.betterhotbarswap.BetterHotbarSwap;
import com.betterhotbarswap.client.debug.BHSDebug;
import com.betterhotbarswap.client.input.HotbarInputController;
import com.betterhotbarswap.client.input.VanillaClickExecutor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Mixin(CreativeInventoryScreen.class)
public class CreativeInventoryScreenHotbarMixin {
    private static int creativeActionSeq = 0;
    // 1.20.1'de static alan (bug #2 dersi: instance shadow crash verir).
    @Shadow private static ItemGroup selectedTab;
    private int findFirstEmptyMain(PlayerInventory inv) {
        for (int i = 9; i <= 35; i++) if (inv.getStack(i).isEmpty()) return i;
        return -1;
    }
    // Sunucu CreativeInventoryAction paketlerindeki slot id'yi survival-layout
    // (PlayerScreenHandler) olarak okur: hotbar 36-44, main 9-35, offhand 45.
    // Creative handler id'leri (liste sekmesinde hotbar 45-53) PAKETTE KULLANILMAZ;
    // kullanılırsa id 45 sunucuda offhand'e denk gelir (bug #5). Client tahmini
    // (setStack) aynen playerInv üstünde yapılır, sadece paketteki id çevrilir.
    private static int survivalSlotId(int invIndex) {
        if (invIndex >= 0 && invIndex < 9) return 36 + invIndex;
        if (invIndex >= 9 && invIndex < 36) return invIndex;
        return -1;
    }
    // Tüm envanterin kompakt fotoğrafı (sadece dolu slotlar). latest.log'a [BHS-INV] ile düşer.
    private static String snapshot(PlayerInventory inv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack s = inv.main.get(i);
            if (!s.isEmpty()) sb.append(i).append('=').append(shortStack(s)).append(';');
        }
        for (int i = 0; i < inv.armor.size(); i++) {
            ItemStack s = inv.armor.get(i);
            if (!s.isEmpty()) sb.append('a').append(i).append('=').append(shortStack(s)).append(';');
        }
        for (int i = 0; i < inv.offHand.size(); i++) {
            ItemStack s = inv.offHand.get(i);
            if (!s.isEmpty()) sb.append("off=").append(shortStack(s)).append(';');
        }
        return sb.length() == 0 ? "empty" : sb.toString();
    }
    // Hotbar Önceliği: basılan slot hariç, basılana en yakın boş hotbar slotu. Eşitlikte küçük index kazanır.
    private int findNearestEmptyHotbar(PlayerInventory inv, int pressedIndex) {
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            if (i == pressedIndex || !inv.getStack(i).isEmpty()) continue;
            int dist = Math.abs(i - pressedIndex);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }
    // Evrensel stacklenebilirlik: aynı item + aynı NBT. Max sayı item'dan okunur (64/16/modded).
    private static boolean isSameStackable(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;
        return Objects.equals(a.getNbt(), b.getNbt());
    }
    // Stack sırası: hotbar'da basılana yakınlık (basılan hariç), sonra main 9->35.
    private static List<Integer> stackOrder(int pressedIndex) {
        List<Integer> order = new ArrayList<>();
        for (int d = 1; d < 9; d++) {
            if (pressedIndex - d >= 0) order.add(pressedIndex - d);
            if (pressedIndex + d < 9) order.add(pressedIndex + d);
        }
        for (int i = 9; i < 36; i++) order.add(i);
        return order;
    }
    // Arama sekmesi mi? (Kabul edilen sınır: harfler orada yazıya aittir, bug #13.)
    private static boolean isSearchTab() {
        try {
            return Registries.ITEM_GROUP.getKey(selectedTab).map(k -> k.equals(ItemGroups.SEARCH)).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }
    private static boolean isDigitKey(int keyCode) {
        return (keyCode >= 49 && keyCode <= 57) || (keyCode >= 320 && keyCode <= 328);
    }
    // Eşya Sil (X) kutusu: envanter sekmesindeki SimpleInventory slot (liste sekmelerindeki
    // liste slotlarıyla karışmaması için güvenilmez-id şartı var).
    private static boolean isTrashSlot(ScreenHandler handler, Slot slot) {
        return slot.inventory instanceof SimpleInventory
            && slot.getIndex() == 0
            && !VanillaClickExecutor.idsTrustworthy(handler);
    }
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void betterhotbarswap$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null) return;
        // Oyuncunun gerçek bağları (V/R gibi remap'ler dahil, bug #13).
        int hotbarIndex = HotbarInputController.hotbarIndex(client, keyCode, scanCode);
        if (hotbarIndex < 0 || hotbarIndex > 8) return;
        // Basılı tutma tekrarı: yut (sessiz), makinalı-tüfek aksiyonu yok (bug #17).
        if (HotbarInputController.isRepeatPress(keyCode)) {
            cir.setReturnValue(true);
            return;
        }
        CreativeInventoryScreen screen = (CreativeInventoryScreen)(Object)this;
        int prevSelMixed = -1;
        try { prevSelMixed = client.player.getInventory().selectedSlot; } catch (Exception ignored) {}
        Slot focusedSlot = ((HandledScreenAccessor) screen).betterhotbarswap$getFocusedSlot();
        BHSDebug.update(focusedSlot, hotbarIndex);
        if (focusedSlot == null) {
            BHSDebug.log("PASS creative key=" + keyCode + " reason=no-item-slot (vanilla yazar)");
            return;
        }
        PlayerInventory playerInv = client.player.getInventory();
        ScreenHandler handler = screen.getScreenHandler();
        // Eşya Sil (X) kutusu + kısayol: hotbar'daki eşyayı sil (F5).
        // NOT: X kutusu hep BOŞTUR, o yüzden hasStack kontrolünden ÖNCE bakılır.
        if (isTrashSlot(handler, focusedSlot)) {
            ItemStack condemned = playerInv.getStack(hotbarIndex).copy();
            if (!condemned.isEmpty()) {
                playerInv.setStack(hotbarIndex, ItemStack.EMPTY);
                client.interactionManager.clickCreativeStack(ItemStack.EMPTY, survivalSlotId(hotbarIndex));
                BHSDebug.log("DELETE hb" + (hotbarIndex + 1) + " " + shortStack(condemned));
            } else {
                BHSDebug.log("DELETE hb" + (hotbarIndex + 1) + " (zaten boş)");
            }
            HotbarInputController.restoreSelectionIfChanged(client, prevSelMixed);
            HotbarInputController.markPressed(keyCode);
            cir.setReturnValue(true);
            return;
        }
        // Slot boşsa (ör. arama kutusu üstünde) tuş vanilla'ya gider: arama yazmaya devam eder.
        if (!focusedSlot.hasStack()) {
            BHSDebug.log("PASS creative key=" + keyCode + " reason=no-item-slot (vanilla yazar)");
            return;
        }
        // Arama sekmesinde harf tuşları yazıya aittir (kabul edilen sınır, bug #13).
        if (isSearchTab() && !isDigitKey(keyCode)) return;
        // Oyuncu slotları: envanter sekmesinde (güvenilmez id'ler) bizim zincir + iptal
        // (vanilla ile çift işlemeyi önler, bug #12). Liste sekmelerinde vanilla
        // (sekme değiştirme vb.) çalışsın diye dokunulmaz.
        // Bug #31: zirh/offhand kaynagi vanilla'ya birak (dupe vektoru).
        try {
            if (focusedSlot.inventory instanceof PlayerInventory) {
                int fIdx = focusedSlot.getIndex();
                if (fIdx >= 36 && fIdx <= 40) {
                    BHSDebug.log("VANILLA zirh/offhand kaynagi idx=" + fIdx + " (vanilla yapsin)");
                    return;
                }
            }
        } catch (Exception ignored) {}
        if (focusedSlot.inventory instanceof PlayerInventory) {
            if (!VanillaClickExecutor.idsTrustworthy(screen.getScreenHandler())) {
                boolean ok = VanillaClickExecutor.assignSlotToHotbar(screen.getScreenHandler(), focusedSlot, hotbarIndex, false);
                HotbarInputController.restoreSelectionIfChanged(client, prevSelMixed);
                if (ok) {
                    HotbarInputController.markPressed(keyCode);
                    HotbarInputController.lastSwapNanos = System.nanoTime();
                    cir.setReturnValue(true);
                } else {
                    BHSDebug.log("VANILLA assign-false " + BHSDebug.describeSlot(focusedSlot, client.player));
                }
            } else {
                BHSDebug.log("VANILLA trustworthy-player-slot " + BHSDebug.describeSlot(focusedSlot, client.player));
            }
            return;
        }
        BHSDebug.log("CTX " + handler.getClass().getSimpleName() + " sync=" + handler.syncId
            + " slots=" + handler.slots.size() + " current=" + (handler == client.player.currentScreenHandler));
        BetterHotbarSwap.LOGGER.info("[BHS-INV] BEFORE {}", snapshot(playerInv));
        ItemStack newStack = focusedSlot.getStack().copy();
        if (newStack.isEmpty()) return;
        // Creative liste stack'i 1'li görünür; hotbar'a tam deste ver.
        newStack.setCount(newStack.getMaxCount());
        ItemStack oldHotbarStack = playerInv.getStack(hotbarIndex).copy();
        int hotbarSurvivalId = survivalSlotId(hotbarIndex);
        if (hotbarSurvivalId == -1) {
            BHSDebug.log("CREATIVE list->hotbar" + (hotbarIndex + 1) + " IPTAL bad-index " + BHSDebug.describeSlot(focusedSlot, client.player));
            BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
            return;
        }
        String what = BHSDebug.describeSlot(focusedSlot, client.player);
        if (oldHotbarStack.isEmpty()) {
            playerInv.setStack(hotbarIndex, newStack);
            client.interactionManager.clickCreativeStack(newStack, hotbarSurvivalId);
            BHSDebug.log("CREATIVE " + what + " -> hotbar" + (hotbarIndex + 1) + " x" + newStack.getCount() + " (bos sid=" + hotbarSurvivalId + ")" + hands(client));
        } else {
            // Verimlilik zinciri (F2) - sıra önemli, paketler aynı tick'te sırayla gider:
            // 1. eski: kısmi stackleri tamamla (evrensel: item+NBT eşitliği, getMaxCount)
            int remaining = oldHotbarStack.getCount();
            StringBuilder moves = new StringBuilder();
            for (int idx : stackOrder(hotbarIndex)) {
                if (remaining <= 0) break;
                ItemStack s = playerInv.getStack(idx);
                if (!isSameStackable(s, oldHotbarStack)) continue;
                int max = s.getMaxCount();
                if (s.getCount() >= max) continue;
                int move = Math.min(remaining, max - s.getCount());
                ItemStack updated = s.copy();
                updated.setCount(s.getCount() + move);
                playerInv.setStack(idx, updated);
                client.interactionManager.clickCreativeStack(updated, survivalSlotId(idx));
                remaining -= move;
                moves.append(" +").append(move).append("->").append(idx < 9 ? "hb" + (idx + 1) : "m" + idx);
            }
            // 2. kalan: en yakın boş hotbar, yoksa main 9->35
            String overflowWhere = null;
            if (remaining > 0) {
                int overflowInvIndex = findNearestEmptyHotbar(playerInv, hotbarIndex);
                if (overflowInvIndex == -1) overflowInvIndex = findFirstEmptyMain(playerInv);
                if (overflowInvIndex != -1) {
                    ItemStack rest = oldHotbarStack.copy();
                    rest.setCount(remaining);
                    playerInv.setStack(overflowInvIndex, rest);
                    client.interactionManager.clickCreativeStack(rest, survivalSlotId(overflowInvIndex));
                    overflowWhere = overflowInvIndex < 9 ? "hb" + (overflowInvIndex + 1) : "m" + overflowInvIndex;
                    remaining = 0;
                }
            }
            // 3. hâlâ kalan (her yer dolu): dışarı at. H'de sunucunda hâlâ eski durduğu
            // için önce düşür; new paketi sonra gelir ve H'ye yazar.
            // Drop BAŞARISIZ olursa eski korunur (sessiz silme YOK).
            boolean dropped = false;
            if (remaining > 0) {
                int prevSel = playerInv.selectedSlot;
                try {
                    playerInv.selectedSlot = hotbarIndex;
                    client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(hotbarIndex));
                    dropped = client.player.dropSelectedItem(true);
                } finally {
                    playerInv.selectedSlot = prevSel;
                    try { client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(prevSel)); } catch (Exception ignored) {}
                }
                if (!dropped) {
                    BHSDebug.log("CREATIVE " + what + " -> hotbar" + (hotbarIndex + 1)
                        + " VAZGEÇİLDİ (drop başarısız, eski korundu)");
                    BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
                    HotbarInputController.markPressed(keyCode);
                    cir.setReturnValue(true);
                    return;
                }
                remaining = 0;
            }
            // 4. yeni her zaman basılan slota
            playerInv.setStack(hotbarIndex, newStack);
            client.interactionManager.clickCreativeStack(newStack, hotbarSurvivalId);
            final int finalHotbarIndex = hotbarIndex;
            final ItemStack finalNewStack = newStack;
            final int finalHotbarSurvivalId = hotbarSurvivalId;
            final int actionId = ++creativeActionSeq;
            client.execute(() -> {
                // Eski basışın gecikmiş gönderisi yeni durumu ezmesin.
                if (actionId != creativeActionSeq) {
                    BHSDebug.log("SKIP stale next-tick id=" + actionId);
                    return;
                }
                if (client.player == null || client.interactionManager == null) return;
                // Ekran değiştiyse yazma (başka sekmene/ekrana taşma olmasın).
                if (handler != client.player.currentScreenHandler) {
                    BHSDebug.log("SKIP stale-screen next-tick id=" + actionId);
                    return;
                }
                // Bug #32: kullanici esyayi giydiyse/tasidiysa geri koyma (dupe).
                // Mouse Tweaks ile hizli giyme ayni tick'e denk gelir; kor resend
                // hem ayakta hem hotbar'da kopya birakir.
                try {
                    ItemStack cur = client.player.getInventory().getStack(finalHotbarIndex);
                    if (cur.isEmpty()) {
                        BHSDebug.log("SKIP next-tick moved-away id=" + actionId);
                        return;
                    }
                    if (cur.getItem() != finalNewStack.getItem()
                        || !Objects.equals(cur.getNbt(), finalNewStack.getNbt())) {
                        BHSDebug.log("SKIP next-tick replaced id=" + actionId);
                        return;
                    }
                } catch (Exception ignored) {}
                client.player.getInventory().setStack(finalHotbarIndex, finalNewStack);
                client.interactionManager.clickCreativeStack(finalNewStack, finalHotbarSurvivalId);
            });
            BHSDebug.log("CREATIVE " + what + " -> hotbar" + (hotbarIndex + 1) + " x" + newStack.getCount()
                + " (stack:" + (moves.length() == 0 ? "-" : moves)
                + (overflowWhere != null ? "; eski -> " + overflowWhere : "")
                + (dropped ? "; kalan disari" : "") + ")" + hands(client));
        }
        HotbarInputController.restoreSelectionIfChanged(client, prevSelMixed);
        cir.setReturnValue(true); cir.cancel();
        HotbarInputController.markPressed(keyCode);
        HotbarInputController.lastSwapNanos = System.nanoTime();
        BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void betterhotbarswap$charTyped(char chr, int modifiers, CallbackInfoReturnable<Boolean> cir) {        // GLFW tuş olayından bağımsız karakter olayı da gelir; keyPressed'i iptal etmek
        // aramaya sızmasını engellemez. Item slotu üstündeyken + (rakam VEYA az önce
        // hotkey takası olduysa) gelen karakter = hotkey'dir, yutulur.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        CreativeInventoryScreen screen = (CreativeInventoryScreen)(Object)this;
        int prevSelMixed = -1;
        try { prevSelMixed = client.player.getInventory().selectedSlot; } catch (Exception ignored) {}
        Slot focusedSlot = ((HandledScreenAccessor) screen).betterhotbarswap$getFocusedSlot();
        boolean onListItem = focusedSlot != null && focusedSlot.hasStack()
            && !(focusedSlot.inventory instanceof PlayerInventory);
        if (!onListItem) return;
        boolean isDigit = chr >= '1' && chr <= '9';
        boolean recentSwap = System.nanoTime() - HotbarInputController.lastSwapNanos < 300_000_000L;
        if (isDigit || recentSwap) {
            BHSDebug.log("SWALLOW char '" + chr + "' (hotkey, arama yazilmadi)");
            cir.setReturnValue(true);
        }
    }

    private static String hands(MinecraftClient client) {
        try {
            return " el=" + shortStack(client.player.getMainHandStack()) + " off=" + shortStack(client.player.getOffHandStack());
        } catch (Exception e) {
            return "";
        }
    }

    private static String shortStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "bos";
        try {
            return stack.getName().getString() + "x" + stack.getCount();
        } catch (Exception e) {
            return "?";
        }
    }
}
