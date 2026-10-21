package com.betterhotbarswap.mixin;

import com.betterhotbarswap.BetterHotbarSwap;
import com.betterhotbarswap.client.debug.BHSDebug;
import com.betterhotbarswap.client.input.HotbarInputController;
import com.betterhotbarswap.client.input.VanillaClickExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

@Mixin(CreativeModeInventoryScreen.class)
public class CreativeInventoryScreenHotbarMixin {
    private static int creativeActionSeq = 0;
    // 1.20.1'de static alan (bug #2 dersi: instance shadow crash verir).
    @Shadow private static CreativeModeTab selectedTab;
    private int findFirstEmptyMain(Inventory inv) {
        for (int i = 9; i <= 35; i++) if (inv.getItem(i).isEmpty()) return i;
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
    private static String snapshot(Inventory inv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (!s.isEmpty()) sb.append(i).append('=').append(shortStack(s)).append(';');
        }
        for (int i = 0; i < inv.armor.size(); i++) {
            ItemStack s = inv.armor.get(i);
            if (!s.isEmpty()) sb.append('a').append(i).append('=').append(shortStack(s)).append(';');
        }
        for (int i = 0; i < inv.offhand.size(); i++) {
            ItemStack s = inv.offhand.get(i);
            if (!s.isEmpty()) sb.append("off=").append(shortStack(s)).append(';');
        }
        return sb.length() == 0 ? "empty" : sb.toString();
    }
    // Hotbar Önceliği: basılan slot hariç, basılana en yakın boş hotbar slotu. Eşitlikte küçük index kazanır.
    private int findNearestEmptyHotbar(Inventory inv, int pressedIndex) {
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            if (i == pressedIndex || !inv.getItem(i).isEmpty()) continue;
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
        return ItemStack.isSameItemSameComponents(a, b);
    }
    // Stack sırası: SADECE hotbar, basılana yakınlık (basılan hariç). Envanter
    // ancak hotbar'da hiç yer yoksa denenir (kural 2026-10-21: hotbar'da kalır).
    private static List<Integer> stackOrder(int pressedIndex) {
        List<Integer> order = new ArrayList<>();
        for (int d = 1; d < 9; d++) {
            if (pressedIndex - d >= 0) order.add(pressedIndex - d);
            if (pressedIndex + d < 9) order.add(pressedIndex + d);
        }
        return order;
    }
    // Kısmi aynı yığına ekle (mutlak tahmin + paket). Dönüş: taşınan miktar.
    private static int mergeInto(Minecraft client, Inventory inv, int idx, ItemStack old, int remaining, StringBuilder moves) {
        ItemStack s = inv.getItem(idx);
        if (!isSameStackable(s, old) || s.getCount() >= s.getMaxStackSize()) return 0;
        int move = Math.min(remaining, s.getMaxStackSize() - s.getCount());
        ItemStack updated = s.copy();
        updated.setCount(s.getCount() + move);
        inv.setItem(idx, updated);
        client.gameMode.handleCreativeModeItemAdd(updated, survivalSlotId(idx));
        moves.append(" +").append(move).append("->").append(idx < 9 ? "hb" + (idx + 1) : "m" + idx);
        return move;
    }
    // Arama sekmesi mi? (Kabul edilen sınır: harfler orada yazıya aittir, bug #13.)
    private static boolean isSearchTab() {
        try {
            return BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(selectedTab).map(k -> k.equals(CreativeModeTabs.SEARCH)).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }
    private static boolean isDigitKey(int keyCode) {
        return (keyCode >= 49 && keyCode <= 57) || (keyCode >= 320 && keyCode <= 328);
    }
    // Eşya Sil (X) kutusu: envanter sekmesindeki SimpleInventory slot (liste sekmelerindeki
    // liste slotlarıyla karışmaması için güvenilmez-id şartı var).
    private static boolean isTrashSlot(AbstractContainerMenu handler, Slot slot) {
        return slot.container instanceof SimpleContainer
            && slot.getContainerSlot() == 0
            && !VanillaClickExecutor.idsTrustworthy(handler);
    }
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void betterhotbarswap$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) return;
        // Oyuncunun gerçek bağları (V/R gibi remap'ler dahil, bug #13).
        int hotbarIndex = HotbarInputController.hotbarIndex(client, keyCode, scanCode);
        if (hotbarIndex < 0 || hotbarIndex > 8) return;
        // Basılı tutma tekrarı: Düzenli Toplama açıkken yut (#17); kapalıyken
        // vanilla'ya bırak (vanilla gibi gidip gelme, kullanıcı kararı 2026-10-21).
        if (HotbarInputController.isRepeatPress(keyCode)) {
            if (com.betterhotbarswap.config.BHSConfig.tidyCollect) cir.setReturnValue(true);
            return;
        }
        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen)(Object)this;
        int prevSelMixed = -1;
        try { prevSelMixed = client.player.getInventory().selected; } catch (Exception ignored) {}
        Slot focusedSlot = ((HandledScreenAccessor) screen).betterhotbarswap$getFocusedSlot();
        BHSDebug.update(focusedSlot, hotbarIndex);
        if (focusedSlot == null) {
            BHSDebug.log("PASS creative key=" + keyCode + " reason=no-item-slot (vanilla yazar)");
            return;
        }
        Inventory playerInv = client.player.getInventory();
        AbstractContainerMenu handler = screen.getMenu();
        // Eşya Sil (X) kutusu + kısayol: hotbar'daki eşyayı sil (F5).
        // NOT: X kutusu hep BOŞTUR, o yüzden hasStack kontrolünden ÖNCE bakılır.
        if (isTrashSlot(handler, focusedSlot)) {
            ItemStack condemned = playerInv.getItem(hotbarIndex).copy();
            if (!condemned.isEmpty()) {
                playerInv.setItem(hotbarIndex, ItemStack.EMPTY);
                client.gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, survivalSlotId(hotbarIndex));
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
        if (!focusedSlot.hasItem()) {
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
            if (focusedSlot.container instanceof Inventory) {
                int fIdx = focusedSlot.getContainerSlot();
                if (fIdx >= 36 && fIdx <= 40) {
                    BHSDebug.log("VANILLA zirh/offhand kaynagi idx=" + fIdx + " (vanilla yapsin)");
                    return;
                }
            }
        } catch (Exception ignored) {}
        if (focusedSlot.container instanceof Inventory) {
            if (!VanillaClickExecutor.idsTrustworthy(screen.getMenu())) {
                boolean ok = VanillaClickExecutor.assignSlotToHotbar(screen.getMenu(), focusedSlot, hotbarIndex, false);
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
        BHSDebug.log("CTX " + handler.getClass().getSimpleName() + " sync=" + handler.containerId
            + " slots=" + handler.slots.size() + " current=" + (handler == client.player.containerMenu));
        if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] BEFORE {}", snapshot(playerInv));
        ItemStack newStack = focusedSlot.getItem().copy();
        if (newStack.isEmpty()) return;
        // Creative liste stack'i 1'li görünür; hotbar'a tam deste ver.
        newStack.setCount(newStack.getMaxStackSize());
        ItemStack oldHotbarStack = playerInv.getItem(hotbarIndex).copy();
        int hotbarSurvivalId = survivalSlotId(hotbarIndex);
        if (hotbarSurvivalId == -1) {
            BHSDebug.log("CREATIVE list->hotbar" + (hotbarIndex + 1) + " IPTAL bad-index " + BHSDebug.describeSlot(focusedSlot, client.player));
            if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
            return;
        }
        String what = BHSDebug.describeSlot(focusedSlot, client.player);
        if (oldHotbarStack.isEmpty()) {
            playerInv.setItem(hotbarIndex, newStack);
            client.gameMode.handleCreativeModeItemAdd(newStack, hotbarSurvivalId);
            BHSDebug.log("CREATIVE " + what + " -> hotbar" + (hotbarIndex + 1) + " x" + newStack.getCount() + " (bos sid=" + hotbarSurvivalId + ")" + hands(client));
        } else {
            // Verimlilik zinciri (F2) - sıra önemli, paketler aynı tick'te sırayla gider:
            // 1. eski: kısmi stackleri tamamla (evrensel: item+NBT eşitliği, getMaxCount)
            int remaining = oldHotbarStack.getCount();
            StringBuilder moves = new StringBuilder();
            for (int idx : stackOrder(hotbarIndex)) {
                if (remaining <= 0) break;
                remaining -= mergeInto(client, playerInv, idx, oldHotbarStack, remaining, moves);
            }
            // 2. kalan: en yakın boş hotbar; hotbar'da yer yoksa ancak şimdi
            // envanter (önce kısmi aynı yığınlar 9->35, sonra ilk boş).
            String overflowWhere = null;
            if (remaining > 0) {
                int overflowInvIndex = findNearestEmptyHotbar(playerInv, hotbarIndex);
                if (overflowInvIndex == -1) {
                    for (int idx = 9; idx < 36 && remaining > 0; idx++) {
                        remaining -= mergeInto(client, playerInv, idx, oldHotbarStack, remaining, moves);
                    }
                    if (remaining > 0) overflowInvIndex = findFirstEmptyMain(playerInv);
                }
                if (overflowInvIndex != -1 && remaining > 0) {
                    ItemStack rest = oldHotbarStack.copy();
                    rest.setCount(remaining);
                    playerInv.setItem(overflowInvIndex, rest);
                    client.gameMode.handleCreativeModeItemAdd(rest, survivalSlotId(overflowInvIndex));
                    overflowWhere = overflowInvIndex < 9 ? "hb" + (overflowInvIndex + 1) : "m" + overflowInvIndex;
                    remaining = 0;
                }
            }
            // 3. hâlâ kalan (her yer dolu): dışarı at. H'de sunucunda hâlâ eski durduğu
            // için önce düşür; new paketi sonra gelir ve H'ye yazar.
            // Drop BAŞARISIZ olursa eski korunur (sessiz silme YOK).
            boolean dropped = false;
            if (remaining > 0) {
                int prevSel = playerInv.selected;
                try {
                    playerInv.selected = hotbarIndex;
                    client.player.connection.send(new ServerboundSetCarriedItemPacket(hotbarIndex));
                    dropped = client.player.drop(true);
                } finally {
                    playerInv.selected = prevSel;
                    try { client.player.connection.send(new ServerboundSetCarriedItemPacket(prevSel)); } catch (Exception ignored) {}
                }
                if (!dropped) {
                    BHSDebug.log("CREATIVE " + what + " -> hotbar" + (hotbarIndex + 1)
                        + " VAZGEÇİLDİ (drop başarısız, eski korundu)");
                    if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
                    HotbarInputController.markPressed(keyCode);
                    cir.setReturnValue(true);
                    return;
                }
                remaining = 0;
            }
            // 4. yeni her zaman basılan slota
            playerInv.setItem(hotbarIndex, newStack);
            client.gameMode.handleCreativeModeItemAdd(newStack, hotbarSurvivalId);
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
                if (client.player == null || client.gameMode == null) return;
                // Ekran değiştiyse yazma (başka sekmene/ekrana taşma olmasın).
                if (handler != client.player.containerMenu) {
                    BHSDebug.log("SKIP stale-screen next-tick id=" + actionId);
                    return;
                }
                // Bug #32: kullanici esyayi giydiyse/tasidiysa geri koyma (dupe).
                // Mouse Tweaks ile hizli giyme ayni tick'e denk gelir; kor resend
                // hem ayakta hem hotbar'da kopya birakir.
                try {
                    ItemStack cur = client.player.getInventory().getItem(finalHotbarIndex);
                    if (cur.isEmpty()) {
                        BHSDebug.log("SKIP next-tick moved-away id=" + actionId);
                        return;
                    }
                    if (cur.getItem() != finalNewStack.getItem()
                        || !ItemStack.isSameItemSameComponents(cur, finalNewStack)) {
                        BHSDebug.log("SKIP next-tick replaced id=" + actionId);
                        return;
                    }
                } catch (Exception ignored) {}
                client.player.getInventory().setItem(finalHotbarIndex, finalNewStack);
                client.gameMode.handleCreativeModeItemAdd(finalNewStack, finalHotbarSurvivalId);
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
        if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-INV] AFTER {}", snapshot(playerInv));
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void betterhotbarswap$charTyped(char chr, int modifiers, CallbackInfoReturnable<Boolean> cir) {        // GLFW tuş olayından bağımsız karakter olayı da gelir; keyPressed'i iptal etmek
        // aramaya sızmasını engellemez. Item slotu üstündeyken + (rakam VEYA az önce
        // hotkey takası olduysa) gelen karakter = hotkey'dir, yutulur.
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen)(Object)this;
        int prevSelMixed = -1;
        try { prevSelMixed = client.player.getInventory().selected; } catch (Exception ignored) {}
        Slot focusedSlot = ((HandledScreenAccessor) screen).betterhotbarswap$getFocusedSlot();
        boolean onListItem = focusedSlot != null && focusedSlot.hasItem()
            && !(focusedSlot.container instanceof Inventory);
        if (!onListItem) return;
        boolean isDigit = chr >= '1' && chr <= '9';
        boolean recentSwap = System.nanoTime() - HotbarInputController.lastSwapNanos < 300_000_000L;
        if (isDigit || recentSwap) {
            BHSDebug.log("SWALLOW char '" + chr + "' (hotkey, arama yazilmadi)");
            cir.setReturnValue(true);
        }
    }

    private static String hands(Minecraft client) {
        try {
            return " el=" + shortStack(client.player.getMainHandItem()) + " off=" + shortStack(client.player.getOffhandItem());
        } catch (Exception e) {
            return "";
        }
    }

    private static String shortStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "bos";
        try {
            return stack.getHoverName().getString() + "x" + stack.getCount();
        } catch (Exception e) {
            return "?";
        }
    }
}
