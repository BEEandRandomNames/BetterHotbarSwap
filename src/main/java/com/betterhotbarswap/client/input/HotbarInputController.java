package com.betterhotbarswap.client.input;

import com.betterhotbarswap.BetterHotbarSwap;
import com.betterhotbarswap.client.debug.BHSDebug;
import com.betterhotbarswap.config.BHSConfig;
import com.betterhotbarswap.mixin.HandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public final class HotbarInputController {
    public static long lastSwapNanos = 0L;
    // Basılı tutma tekrarlarını yut (bug #17): basış başına TEK aksiyon.
    // Sadece KENDİ işlediğimiz tuşlar kayda girer; vanilla'ya bırakılanlar etkilenmez.
    private static final java.util.HashMap<Integer, Long> downKeys = new java.util.HashMap<>();
    private static final long DOWN_KEY_TTL_NANOS = 5_000_000_000L;
    // TidyClick latch'leri (olay-güdümlü, poll YOK): Shift basılınca kurulur,
    // bırakılınca düşer; son hotkey + slotu hatırlanır (tıklama yolu için).
    private static boolean shiftLatch;
    private static int lastHotkey = -1;
    private static int lastHotbar = -1;

    private static void purgeDownKeys() {
        long now = System.nanoTime();
        downKeys.entrySet().removeIf(e -> now - e.getValue() > DOWN_KEY_TTL_NANOS);
    }

    public static boolean isRepeatPress(int keyCode) {
        purgeDownKeys();
        return downKeys.containsKey(keyCode);
    }

    public static void markPressed(int keyCode) {
        purgeDownKeys();
        downKeys.put(keyCode, System.nanoTime());
    }

    public static void releasePressed(int keyCode) {
        downKeys.remove(keyCode);
        heldKeys.remove(keyCode);
        if (isShiftKey(keyCode)) {
            shiftLatch = heldKeys.contains(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                || heldKeys.contains(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
        }
    }

    // Fiziksel basılı tuşlar (KeyboardReleaseMixin PRESS/RELEASE). TTL YOK:
    // OS tuş-tekrarı sadece SON basılan tuşu tekrarlar (N sonra Shift = N tekrarsız),
    // bu yüzden "basılı mı" bilgisi tekrar olaylarından değil bas/bırak'tan gelir.
    private static final java.util.HashSet<Integer> heldKeys = new java.util.HashSet<>();

    // Ekran açılmadan önce basılan Shift/kısayol da yakalanır (latch ekran-bağımsız).
    public static void physicalPress(int keyCode, int scanCode) {
        heldKeys.add(keyCode);
        if (isShiftKey(keyCode)) {
            shiftLatch = true;
            return;
        }
        int hb = hotbarIndex(MinecraftClient.getInstance(), keyCode, scanCode);
        if (hb >= 0 && hb <= 9) {
            lastHotkey = keyCode;
            lastHotbar = hb;
        }
    }

    private static boolean isShiftKey(int keyCode) {
        return keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
            || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
    }

    public static int latchedHotbar() {
        return lastHotbar;
    }

    // Düzenli toplama modu: Shift + kısayol (0-9) şu an FİZİKSEL olarak basılı.
    public static boolean isTidyHeld() {
        return shiftLatch && lastHotkey >= 0 && lastHotbar >= 0 && lastHotbar <= 9
            && heldKeys.contains(lastHotkey);
    }

    // Bug #30: secili slot ASLA degismez. SWAP/PICKUP/drop yan etkisiyle
    // selectedSlot oynamissa geri alinir (evrensel, bayraktan bagimsiz).
    public static void restoreSelectionIfChanged(MinecraftClient client, int prevSel) {
        try {
            if (client == null || client.player == null) return;
            PlayerInventory inv = client.player.getInventory();
            if (prevSel < 0 || prevSel > 8) return;
            if (inv.selectedSlot != prevSel) {
                BHSDebug.log("SEL-RESTORE prev=" + prevSel + " cur=" + inv.selectedSlot + " -> geri alindi");
                inv.selectedSlot = prevSel;
                try {
                    client.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(prevSel));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static String layoutDumpKey = "";

    private static void dumpLayout(ScreenHandler handler) {
        String key = handler.getClass().getName() + "#" + handler.slots.size() + "#" + handler.syncId;
        if (layoutDumpKey.equals(key)) return;
        layoutDumpKey = key;
        BetterHotbarSwap.LOGGER.info("[BHS-LAYOUT] handler={} slots={} sync={}",
            handler.getClass().getName(), handler.slots.size(), handler.syncId);
        for (Slot s : handler.slots) {
            String stack;
            try {
                stack = s.getStack().isEmpty() ? "empty" : s.getStack().getName().getString();
            } catch (Exception e) {
                stack = "?";
            }
            BetterHotbarSwap.LOGGER.info("[BHS-LAYOUT] id={} inv={} idx={} stack={}",
                s.id, s.inventory == null ? "null" : s.inventory.getClass().getName(), s.getIndex(), stack);
        }
    }

    // Oyuncunun GERÇEK hotbar bağları (remap uyumlu) + numpad yedeği.
    // Menü-özel kod YOK: hangi menü olursa olsun aynı eşleşme (evrensellik, bug #13).
    public static int hotbarIndex(MinecraftClient client, int keyCode, int scanCode) {
        // Sol-el (F) en once: vanilla paritesi (swapHands her seyi yener).
        try {
            if (client != null && client.options != null && client.options.swapHandsKey != null
                && client.options.swapHandsKey.matchesKey(keyCode, scanCode)) return 10;
        } catch (Exception ignored) {
        }
        // Özel 10. yuva (main-only) önce: açık kullanıcı seçimi her zaman kazanır.
        try {
            net.minecraft.client.option.KeyBinding hotbar10 = com.betterhotbarswap.client.BetterHotbarSwapClient.getHotbar10Key();
            if (hotbar10 != null && hotbar10.matchesKey(keyCode, scanCode)) return 9;
        } catch (Exception ignored) {
        }
        if (client != null && client.options != null && client.options.hotbarKeys != null) {
            KeyBinding[] keys = client.options.hotbarKeys;
            for (int i = 0; i < keys.length && i < 9; i++) {
                try {
                    if (keys[i] != null && keys[i].matchesKey(keyCode, scanCode)) return i;
                } catch (Exception ignored) {
                }
            }
        }
        if (keyCode == 48 || keyCode == 320) return 9;
        if (keyCode >= 321 && keyCode <= 328) return keyCode - 321;
        return -1;
    }

    // Shift algısı: Screen.hasShiftDown() yerine doğrudan GLFW durumu okunur
    // (ara katmanda sessiz istisna şüphesi, 2026-09-12). Hata olursa loga düşer,
    // asla sessiz yutulmaz.
    public static boolean isShiftDown() {
        try {
            long win = MinecraftClient.getInstance().getWindow().getHandle();
            return org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        } catch (Exception e) {
            BHSDebug.log("SHIFT-ERR " + e);
            return false;
        }
    }

    // Shift durumu ASLA poll'lanmaz (GLFW anlık okuma dalgalanıyordu):
    // tuş OLAYININ modifiers biti kullanılır (o an için authoritative).
    // Tick'lerde Shift koşulu YOKTUR; oturum ömrü = hotkey basılı (olay-güdümlü
    // downKeys). Harita ilkesi: belirsizse vazgeç, ama yoklama yok.
    public static boolean onKeyPressed(HandledScreen<?> screen, int keyCode, int scanCode, int modifiers) {
        // Shift latch: basışta kurulur, bırakışta düşer (KeyboardReleaseMixin).
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
            || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) {
            shiftLatch = true;
            return false;
        }
        int hotbarIndex = hotbarIndex(MinecraftClient.getInstance(), keyCode, scanCode);
        if (hotbarIndex < 0 || hotbarIndex > 10) return false;
        lastHotkey = keyCode;
        lastHotbar = hotbarIndex;
        int prevSel = -1;
        try { prevSel = MinecraftClient.getInstance().player.getInventory().selectedSlot; } catch (Exception ignored) {}
        // Canlılık kaydı: basılı tuş HER olayda tazelenir (boş slotta gezerken
        // bile). Yoksa TTL dolar, tutuş "taze basış" sanılır, latch'ler ölür.
        // Taze/tekrar ayrımı için ÖNCE okunur.
        boolean repeat = isRepeatPress(keyCode);
        markPressed(keyCode);
        boolean shiftHeld = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
        // Latch seeding: Shift GUI DIŞINDA basılı tutulup sonra hotkey'e
        // basılırsa press-olayı hiç gelmez; canlı modifiers bilgisinden öğren.
        if (shiftHeld) shiftLatch = true;
        Slot focusedSlot = ((HandledScreenAccessor) screen).betterhotbarswap$getFocusedSlot();
        BHSDebug.update(focusedSlot, hotbarIndex);
        ScreenHandler handler = screen.getScreenHandler();
        // İmleçte eşya varken HİÇBİR alım yapılmaz (harita ilkesi #4; imleç artığını
        // yapısal olarak kapatır). Sürükleme + elde tutma ikisi de dahildir.
        if (focusedSlot == null || ((HandledScreenAccessor) screen).betterhotbarswap$isCursorDragging()
            || !handler.getCursorStack().isEmpty()) {
            // Tekrarlar sessiz yutulur (30Hz PASS spami yok); taze basış loglanır.
            if (repeat) return true;
            String reason = focusedSlot == null ? "no-focused-slot"
                : (((HandledScreenAccessor) screen).betterhotbarswap$isCursorDragging() ? "cursor-dragging" : "cursor-not-empty");
            BHSDebug.log("PASS key=" + keyCode + " screen=" + screen.getClass().getSimpleName()
                + " reason=" + reason);
            return false;
        }
        // Basılı-tutma tekrarı: SADECE output tarafında + creative-dışı ekranda
        // akışa devam et (hızlı üretim: bırakana kadar +R+R; refill yoksa sessiz
        // no-op). Oyuncu slotlarında ve creative ekranda YUT (ping-pong, #17).
        // Shift her tekrarda yeniden okunur: tutarken Shift'e basılırsa o tekrarda
        // bulk ateşlenir, bırakınca akış durur.
        if (repeat) {
            boolean holdFlow = !(screen instanceof CreativeInventoryScreen)
                && !(focusedSlot.inventory instanceof PlayerInventory);
            if (!holdFlow) return true;
        }
        boolean isFromCreativeList = !(focusedSlot.inventory instanceof PlayerInventory);
        if (screen instanceof CreativeInventoryScreen) {
            // "0" modu creative sekmelerde yok (vanilla davranır).
            if (hotbarIndex > 8) return false;
            dumpLayout(handler);
            if (SlotClassifier.isAssignableSlot(focusedSlot, MinecraftClient.getInstance().player)) {
                boolean ok = VanillaClickExecutor.assignSlotToHotbar(handler, focusedSlot, hotbarIndex, isFromCreativeList);
                restoreSelectionIfChanged(MinecraftClient.getInstance(), prevSel);
                if (ok) {
                    markPressed(keyCode);
                    lastSwapNanos = System.nanoTime();
                }
                BHSDebug.log("CREATIVE-TAB " + BHSDebug.describeSlot(focusedSlot, MinecraftClient.getInstance().player)
                    + " key->hotbar" + (hotbarIndex + 1) + " result=" + ok);
                return ok;
            }
            BHSDebug.log("PASS creative-tab not-assignable " + BHSDebug.describeSlot(focusedSlot, MinecraftClient.getInstance().player));
            return false;
        }
        // Düzenli toplama (F6): Shift + kısayol ile GÖSTERİLEN depo yığını
        // alınır (fare-güdümlü; oto-drain YOK). Her olay bağımsız alımdır:
        // bas, gezdirerek tara, bırak. Duvar kuralı ziyaret kapsamlıdır.
        // Yerleşmezse yutulur + mesaj (vanilla'ya düşülmez, imleç temizdir).
        if (BHSConfig.tidyCollect && hotbarIndex <= 9 && shiftHeld && TidyCollect.isTidyScreen(handler)) {
            if (TidyCollect.canTake(handler, focusedSlot)) {
                int takeResult = TidyCollect.tryTake(handler, focusedSlot, hotbarIndex);
                if (takeResult == TidyCollect.TAKE_PLACED) {
                    restoreSelectionIfChanged(MinecraftClient.getInstance(), prevSel);
                    markPressed(keyCode);
                    lastSwapNanos = System.nanoTime();
                    BHSDebug.log("SWAP-TRY " + BHSDebug.describeSlot(focusedSlot, MinecraftClient.getInstance().player)
                        + " key->hotbar" + (hotbarIndex + 1) + " result=tidy -> " + TidyCollect.slotName(TidyCollect.lastDest()));
                    return true;
                }
                if (takeResult == TidyCollect.TAKE_FAILED) {
                    TidyCollect.tellFull(MinecraftClient.getInstance());
                }
                markPressed(keyCode);
                return true;
            }
            // Mod ARMED, ama fare alınabilir depo yığınında değil (boş sandık
            // slotu / oyuncu slotu): vanilla takası YUTULUR. Yoksa hareketi
            // başlatan basış hotbar[N] eşyasını sandığa iter / envanteri karıştırır.
            // "0" (main-only) bu kuraldan muaf: kendi akışına devam eder.
            if (hotbarIndex <= 8) {
                if (!repeat) BHSDebug.log("TIDY armed -> vanilla takas yutuldu " + BHSDebug.describeSlot(focusedSlot, MinecraftClient.getInstance().player));
                return true;
            }
        }
        if (SlotClassifier.isAssignableSlot(focusedSlot, MinecraftClient.getInstance().player)) {
            // Shift + Kısayol bulk: output slotta Shift ile basılınca ilk alım +
            // tick-tekrarlayıcı (output boşalana kadar). Config kapalıysa normal tek alım.
            boolean shiftBulkTake = false;
            try {
                shiftBulkTake = BHSConfig.shiftHotbarPickup && shiftHeld
                    && SlotClassifier.isProbableOutputSlot(focusedSlot, MinecraftClient.getInstance().player, screen instanceof CreativeInventoryScreen);
            } catch (Exception e) {
                BHSDebug.log("SHIFT-ERR bulk-hesap " + e);
            }
            ShiftBulkQuickMove.cancel();
            // Bulk çapası alımdan ÖNCE yakalanır (sonrası boş olur).
            net.minecraft.item.ItemStack bulkAnchor = focusedSlot.getStack().copy();
            try {
                if (SlotClassifier.isProbableOutputSlot(focusedSlot, MinecraftClient.getInstance().player, screen instanceof CreativeInventoryScreen)) {
                    BHSDebug.log("OUT shift=" + shiftHeld + " cfg=" + BHSConfig.shiftHotbarPickup + " bulk=" + shiftBulkTake);
                }
            } catch (Exception e) {
                BHSDebug.log("SHIFT-ERR out-log " + e);
            }
            // Oturumsuz + boş-slot tekrarları sessiz yutulur (biten hold'un
            // artçıları; taze basış yukarıda loglanır).
            if (repeat && !focusedSlot.hasStack()) return true;
            // Shift + Kısayol burst: N craft simüle edilir, tüm paketler AYNI
            // tick'te gider (0.06-0.1sn algısı). Uygun değilse tek alım + drain.
            // İmleç doluysa sadece tek alım yapılır.
            if (shiftBulkTake && handler.getCursorStack().isEmpty()) {
                MinecraftClient client = MinecraftClient.getInstance();
                BurstTakes.BurstPlan burst = BurstTakes.plan(client, handler, focusedSlot, hotbarIndex, bulkAnchor);
                if (burst != null && burst.crafts >= 2) {
                    BurstTakes.fire(client, handler, burst, hotbarIndex);
                    restoreSelectionIfChanged(client, prevSel);
                    ShiftBulkQuickMove.start(handler, focusedSlot, hotbarIndex, bulkAnchor, burst.crafts);
                    markPressed(keyCode);
                    lastSwapNanos = System.nanoTime();
                    BHSDebug.log("SWAP-TRY " + BHSDebug.describeSlot(focusedSlot, MinecraftClient.getInstance().player)
                        + " key->hotbar" + (hotbarIndex + 1) + " result=burst crafts=" + burst.crafts);
                    return true;
                }
            }
            boolean ok = VanillaClickExecutor.assignSlotToHotbar(handler, focusedSlot, hotbarIndex, false);
            restoreSelectionIfChanged(MinecraftClient.getInstance(), prevSel);
            if (ok) {
                markPressed(keyCode);
                lastSwapNanos = System.nanoTime();
                // Drain hotbar/main-endekslidir (0-9): offhand (10) tekil alimdir.
                if (shiftBulkTake && hotbarIndex != 10 && handler.getCursorStack().isEmpty()) {
                    ShiftBulkQuickMove.start(handler, focusedSlot, hotbarIndex, bulkAnchor, 1);
                }
            }
            BHSDebug.log("SWAP-TRY " + BHSDebug.describeSlot(focusedSlot, MinecraftClient.getInstance().player)
                + " key->hotbar" + (hotbarIndex + 1) + " result=" + ok);
            return ok;
        }
        BHSDebug.log("PASS not-assignable " + BHSDebug.describeSlot(focusedSlot, MinecraftClient.getInstance().player));
        return false;
    }
}