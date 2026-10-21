package com.betterhotbarswap.client.input;

import com.betterhotbarswap.BetterHotbarSwap;
import com.betterhotbarswap.client.debug.BHSDebug;
import com.betterhotbarswap.config.BHSConfig;
import com.betterhotbarswap.mixin.HandledScreenAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

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
        int hb = hotbarIndex(Minecraft.getInstance(), keyCode, scanCode);
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
    public static void restoreSelectionIfChanged(Minecraft client, int prevSel) {
        try {
            if (client == null || client.player == null) return;
            Inventory inv = client.player.getInventory();
            if (prevSel < 0 || prevSel > 8) return;
            if (inv.selected != prevSel) {
                BHSDebug.log("SEL-RESTORE prev=" + prevSel + " cur=" + inv.selected + " -> geri alindi");
                inv.selected = prevSel;
                try {
                    client.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(prevSel));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static String layoutDumpKey = "";

    private static void dumpLayout(AbstractContainerMenu handler) {
        String key = handler.getClass().getName() + "#" + handler.slots.size() + "#" + handler.containerId;
        if (layoutDumpKey.equals(key)) return;
        layoutDumpKey = key;
        if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-LAYOUT] handler={} slots={} sync={}",
            handler.getClass().getName(), handler.slots.size(), handler.containerId);
        for (Slot s : handler.slots) {
            String stack;
            try {
                stack = s.getItem().isEmpty() ? "empty" : s.getItem().getHoverName().getString();
            } catch (Exception e) {
                stack = "?";
            }
            if (com.betterhotbarswap.config.BHSConfig.debugEnabled) BetterHotbarSwap.LOGGER.info("[BHS-LAYOUT] id={} inv={} idx={} stack={}",
                s.index, s.container == null ? "null" : s.container.getClass().getName(), s.getContainerSlot(), stack);
        }
    }

    // Oyuncunun GERÇEK hotbar bağları (remap uyumlu) + numpad yedeği.
    // Menü-özel kod YOK: hangi menü olursa olsun aynı eşleşme (evrensellik, bug #13).
    public static int hotbarIndex(Minecraft client, int keyCode, int scanCode) {
        // Sol-el (F) en once: vanilla paritesi (swapHands her seyi yener).
        try {
            if (client != null && client.options != null && client.options.keySwapOffhand != null
                && client.options.keySwapOffhand.matches(keyCode, scanCode)) return 10;
        } catch (Exception ignored) {
        }
        // Özel 10. yuva (main-only) önce: açık kullanıcı seçimi her zaman kazanır.
        try {
            net.minecraft.client.KeyMapping hotbar10 = com.betterhotbarswap.client.BetterHotbarSwapClient.getHotbar10Key();
            if (hotbar10 != null && hotbar10.matches(keyCode, scanCode)) return 9;
        } catch (Exception ignored) {
        }
        if (client != null && client.options != null && client.options.keyHotbarSlots != null) {
            KeyMapping[] keys = client.options.keyHotbarSlots;
            for (int i = 0; i < keys.length && i < 9; i++) {
                try {
                    if (keys[i] != null && keys[i].matches(keyCode, scanCode)) return i;
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
            long win = Minecraft.getInstance().getWindow().getWindow();
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
    public static boolean onKeyPressed(AbstractContainerScreen<?> screen, int keyCode, int scanCode, int modifiers) {
        // Shift latch: basışta kurulur, bırakışta düşer (KeyboardReleaseMixin).
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
            || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) {
            shiftLatch = true;
            return false;
        }
        int hotbarIndex = hotbarIndex(Minecraft.getInstance(), keyCode, scanCode);
        if (hotbarIndex < 0 || hotbarIndex > 10) return false;
        // Odaklı öğe (tarif kitabı araması, modların arama kutuları) ÖNCE:
        // vanilla da tuşu önce odaklı öğeye verir (javap: HandledScreen.keyPressed
        // ilk iş Screen.keyPressed). Yazı yazılıyorsa takas YOK.
        try {
            net.minecraft.client.gui.components.events.GuiEventListener focused = screen.getFocused();
            if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) return true;
        } catch (Exception ignored) {
        }
        lastHotkey = keyCode;
        lastHotbar = hotbarIndex;
        return handleHotkey(screen, keyCode, hotbarIndex, modifiers);
    }

    // Fareye bağlı kısayollar (Mouse4/5 vb.): vanilla bunları mouseClicked içinde
    // onMouseClick(int) ile çözer. Aynı sisteme sokulur; fare tekrarı yoktur
    // (her tık taze basış). Shift fiziksel latch'ten okunur.
    public static boolean onMouseHotkey(AbstractContainerScreen<?> screen, int button) {
        int hotbarIndex = mouseHotbarIndex(Minecraft.getInstance(), button);
        if (hotbarIndex < 0) return false;
        int code = MOUSE_CODE_BASE - button;
        releasePressed(code);
        return handleHotkey(screen, code, hotbarIndex, shiftLatch ? org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT : 0);
    }

    // Klavye kodlarıyla çakışmayan sahte kod (downKeys kaydı için).
    private static final int MOUSE_CODE_BASE = -1000;

    private static int mouseHotbarIndex(Minecraft client, int button) {
        if (client == null || client.options == null) return -1;
        try {
            if (client.options.keySwapOffhand.matchesMouse(button)) return 10;
            KeyMapping hotbar10 = com.betterhotbarswap.client.BetterHotbarSwapClient.getHotbar10Key();
            if (hotbar10 != null && hotbar10.matchesMouse(button)) return 9;
            KeyMapping[] keys = client.options.keyHotbarSlots;
            for (int i = 0; i < keys.length && i < 9; i++) {
                if (keys[i] != null && keys[i].matchesMouse(button)) return i;
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static boolean handleHotkey(AbstractContainerScreen<?> screen, int keyCode, int hotbarIndex, int modifiers) {
        int prevSel = -1;
        try { prevSel = Minecraft.getInstance().player.getInventory().selected; } catch (Exception ignored) {}
        // Canlılık kaydı: basılı tuş HER olayda tazelenir (boş slotta gezerken
        // bile). Yoksa TTL dolar, tutuş "taze basış" sanılır, latch'ler ölür.
        // Taze/tekrar ayrımı için ÖNCE okunur.
        boolean repeat = isRepeatPress(keyCode);
        markPressed(keyCode);
        // Tekrar yutma (#17) SADECE Düzenli Toplama açıkken (kullanıcı kararı
        // 2026-10-21). Kapalıyken yutulan tekrarlar vanilla'ya gider: tuşu basılı
        // tutunca eşya slot <-> hotbar arasında vanilla gibi gidip gelir.
        boolean swallowRepeat = BHSConfig.tidyCollect;
        boolean shiftHeld = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
        // Latch seeding: Shift GUI DIŞINDA basılı tutulup sonra hotkey'e
        // basılırsa press-olayı hiç gelmez; canlı modifiers bilgisinden öğren.
        if (shiftHeld) shiftLatch = true;
        Slot focusedSlot = ((HandledScreenAccessor) screen).betterhotbarswap$getFocusedSlot();
        BHSDebug.update(focusedSlot, hotbarIndex);
        AbstractContainerMenu handler = screen.getMenu();
        // İmleçte eşya varken HİÇBİR alım yapılmaz (harita ilkesi #4; imleç artığını
        // yapısal olarak kapatır). Sürükleme + elde tutma ikisi de dahildir.
        if (focusedSlot == null || ((HandledScreenAccessor) screen).betterhotbarswap$isCursorDragging()
            || !handler.getCarried().isEmpty()) {
            // Tekrarlar sessiz yutulur (30Hz PASS spami yok); taze basış loglanır.
            if (repeat) return swallowRepeat;
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
            boolean holdFlow = !(screen instanceof CreativeModeInventoryScreen)
                && !(focusedSlot.container instanceof Inventory);
            if (!holdFlow) return swallowRepeat;
        }
        boolean isFromCreativeList = !(focusedSlot.container instanceof Inventory);
        if (screen instanceof CreativeModeInventoryScreen) {
            // "0" modu creative sekmelerde yok (vanilla davranır).
            if (hotbarIndex > 8) return false;
            dumpLayout(handler);
            if (SlotClassifier.isAssignableSlot(focusedSlot, Minecraft.getInstance().player)) {
                boolean ok = VanillaClickExecutor.assignSlotToHotbar(handler, focusedSlot, hotbarIndex, isFromCreativeList);
                restoreSelectionIfChanged(Minecraft.getInstance(), prevSel);
                if (ok) {
                    markPressed(keyCode);
                    lastSwapNanos = System.nanoTime();
                }
                BHSDebug.log("CREATIVE-TAB " + BHSDebug.describeSlot(focusedSlot, Minecraft.getInstance().player)
                    + " key->hotbar" + (hotbarIndex + 1) + " result=" + ok);
                return ok;
            }
            BHSDebug.log("PASS creative-tab not-assignable " + BHSDebug.describeSlot(focusedSlot, Minecraft.getInstance().player));
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
                    restoreSelectionIfChanged(Minecraft.getInstance(), prevSel);
                    markPressed(keyCode);
                    lastSwapNanos = System.nanoTime();
                    BHSDebug.log("SWAP-TRY " + BHSDebug.describeSlot(focusedSlot, Minecraft.getInstance().player)
                        + " key->hotbar" + (hotbarIndex + 1) + " result=tidy -> " + TidyCollect.slotName(TidyCollect.lastDest()));
                    return true;
                }
                if (takeResult == TidyCollect.TAKE_FAILED) {
                    TidyCollect.tellFull(Minecraft.getInstance());
                }
                markPressed(keyCode);
                return true;
            }
            // Mod ARMED, ama fare alınabilir depo yığınında değil (boş sandık
            // slotu / oyuncu slotu): tuş YUTULUR. Yoksa hareketi başlatan basış
            // hotbar[N] eşyasını sandığa iter / envanteri karıştırır. "0"/10. slot
            // DAHİL (bug #43: muafken Shift+10 hotbar eşyasını envantere taşıyordu).
            if (!repeat) BHSDebug.log("TIDY armed -> tus yutuldu " + BHSDebug.describeSlot(focusedSlot, Minecraft.getInstance().player));
            return true;
        }
        if (SlotClassifier.isAssignableSlot(focusedSlot, Minecraft.getInstance().player)) {
            // Shift + Kısayol bulk: output slotta Shift ile basılınca ilk alım +
            // tick-tekrarlayıcı (output boşalana kadar). Config kapalıysa normal tek alım.
            boolean shiftBulkTake = false;
            try {
                shiftBulkTake = BHSConfig.shiftHotbarPickup && shiftHeld
                    && SlotClassifier.isProbableOutputSlot(focusedSlot, Minecraft.getInstance().player, screen instanceof CreativeModeInventoryScreen);
            } catch (Exception e) {
                BHSDebug.log("SHIFT-ERR bulk-hesap " + e);
            }
            ShiftBulkQuickMove.cancel();
            // Bulk çapası alımdan ÖNCE yakalanır (sonrası boş olur).
            net.minecraft.world.item.ItemStack bulkAnchor = focusedSlot.getItem().copy();
            try {
                if (SlotClassifier.isProbableOutputSlot(focusedSlot, Minecraft.getInstance().player, screen instanceof CreativeModeInventoryScreen)) {
                    BHSDebug.log("OUT shift=" + shiftHeld + " cfg=" + BHSConfig.shiftHotbarPickup + " bulk=" + shiftBulkTake);
                }
            } catch (Exception e) {
                BHSDebug.log("SHIFT-ERR out-log " + e);
            }
            // Oturumsuz + boş-slot tekrarları sessiz yutulur (biten hold'un
            // artçıları; taze basış yukarıda loglanır).
            if (repeat && !focusedSlot.hasItem()) return swallowRepeat;
            // Shift + Kısayol burst: N craft simüle edilir, tüm paketler AYNI
            // tick'te gider (0.06-0.1sn algısı). Uygun değilse tek alım + drain.
            // İmleç doluysa sadece tek alım yapılır.
            if (shiftBulkTake && handler.getCarried().isEmpty()) {
                Minecraft client = Minecraft.getInstance();
                BurstTakes.BurstPlan burst = BurstTakes.plan(client, handler, focusedSlot, hotbarIndex, bulkAnchor);
                if (burst != null && burst.crafts >= 2) {
                    BurstTakes.fire(client, handler, burst, hotbarIndex);
                    restoreSelectionIfChanged(client, prevSel);
                    ShiftBulkQuickMove.start(handler, focusedSlot, hotbarIndex, bulkAnchor, burst.crafts);
                    markPressed(keyCode);
                    lastSwapNanos = System.nanoTime();
                    BHSDebug.log("SWAP-TRY " + BHSDebug.describeSlot(focusedSlot, Minecraft.getInstance().player)
                        + " key->hotbar" + (hotbarIndex + 1) + " result=burst crafts=" + burst.crafts);
                    return true;
                }
            }
            boolean ok = VanillaClickExecutor.assignSlotToHotbar(handler, focusedSlot, hotbarIndex, false);
            restoreSelectionIfChanged(Minecraft.getInstance(), prevSel);
            if (ok) {
                markPressed(keyCode);
                lastSwapNanos = System.nanoTime();
                // Drain hotbar/main-endekslidir (0-9): offhand (10) tekil alimdir.
                if (shiftBulkTake && hotbarIndex != 10 && handler.getCarried().isEmpty()) {
                    ShiftBulkQuickMove.start(handler, focusedSlot, hotbarIndex, bulkAnchor, 1);
                }
            }
            BHSDebug.log("SWAP-TRY " + BHSDebug.describeSlot(focusedSlot, Minecraft.getInstance().player)
                + " key->hotbar" + (hotbarIndex + 1) + " result=" + ok);
            return ok;
        }
        BHSDebug.log("PASS not-assignable " + BHSDebug.describeSlot(focusedSlot, Minecraft.getInstance().player));
        return false;
    }
}