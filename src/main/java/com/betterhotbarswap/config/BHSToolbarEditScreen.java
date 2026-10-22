package com.betterhotbarswap.config;

import com.mojang.blaze3d.platform.InputConstants;
import com.betterhotbarswap.client.toolbar.SurvivalToolbars;
import com.betterhotbarswap.client.toolbar.ToolbarStorage;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// Kayıtlı düzen düzenleyici (sadece şablon; gerçek envantere DOKUNMAZ, paket yok).
// Vanilla envanter paneli: envanter 3 sıra + hotbar + solda sol el slotu. Şablonun
// kendisi gösterilir ve SADECE yer değiştirme yapılır (permütasyon): hiçbir eşya
// çoğalmaz ya da kaybolmaz. SADECE tıkla-al / tıkla-bırak (sürükle-bırak kullanıcı isteğiyle yok); dolu slota
// bırakmak takas. İmleçteki eşya boşluğa tıklanınca/ekran kapanınca alındığı yere döner.
// Envanterde olmayan eşyalar kırmızı tonlu (yüklemede atlanır). Bitti kaydeder.
public final class BHSToolbarEditScreen extends Screen {
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 100;
    private static final int OFF_X = -29; // sol el slotu: panelin solunda, hotbar hizasında

    private final Screen parent;
    private final int index;
    private final ItemStack[] slots = new ItemStack[ToolbarStorage.SIZE];
    private ItemStack cursor = ItemStack.EMPTY;
    private int cursorFrom = -1; // imleçteki eşyanın geldiği slot (geri koymak için)
    private int px;
    private int py;

    public BHSToolbarEditScreen(Screen parent, int index) {
        super(Component.translatable("betterhotbarswap.toolbar.edit_title", index + 1));
        this.parent = parent;
        this.index = index;
        List<ItemStack> tpl = SurvivalToolbars.storage(net.minecraft.client.Minecraft.getInstance()).get(index);
        for (int t = 0; t < ToolbarStorage.SIZE; t++) slots[t] = tpl.get(t).copy();
    }

    @Override
    protected void init() {
        px = width / 2 - PANEL_W / 2;
        py = Math.max(40, height / 2 - PANEL_H / 2 - 10);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            dropCursor();
            List<ItemStack> tpl = SurvivalToolbars.storage(minecraft).get(index);
            for (int t = 0; t < ToolbarStorage.SIZE; t++) tpl.set(t, slots[t].copy());
            SurvivalToolbars.storage(minecraft).save();
            BHSToolbarScreen.savedAtMs = System.currentTimeMillis();
            BHSToolbarScreen.savedIndex = index;
            onClose();
        }).bounds(width / 2 - 155, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
            .bounds(width / 2 + 5, height - 28, 150, 20).build());
    }

    // Slot konumu (sol-üst köşe, 18x18). Vanilla envanter ölçüleri:
    // envanter (9-35) y+17/35/53, hotbar (0-8) y+75, sol el hotbar hizasında panel solunda.
    private int sx(int t) {
        if (t == ToolbarStorage.OFFHAND) return px + OFF_X;
        return px + 7 + (t % 9) * 18;
    }

    private int sy(int t) {
        if (t < 9 || t == ToolbarStorage.OFFHAND) return py + 75;
        return py + 17 + (t / 9 - 1) * 18;
    }

    private int slotAt(double mx, double my) {
        for (int t = 0; t < ToolbarStorage.SIZE; t++) {
            if (mx >= sx(t) && mx < sx(t) + 18 && my >= sy(t) && my < sy(t) + 18) return t;
        }
        return -1;
    }

    // İmleçteki eşyayı geldiği yere geri koy (o sırada boş olmalı; değilse ilk boşa).
    private void dropCursor() {
        if (cursor.isEmpty()) return;
        int to = cursorFrom >= 0 && slots[cursorFrom].isEmpty() ? cursorFrom : -1;
        for (int t = 0; to == -1 && t < ToolbarStorage.SIZE; t++) if (slots[t].isEmpty()) to = t;
        if (to == -1) return; // yer yoksa imleçte kalır (asla silinmez)
        slots[to] = cursor;
        cursor = ItemStack.EMPTY;
        cursorFrom = -1;
    }

    // Tek kural: tıklanan slot ile imleç yer değiştirir (al / bırak / takas).
    private void clickSlot(int t) {
        ItemStack old = slots[t];
        slots[t] = cursor;
        cursor = old;
        cursorFrom = cursor.isEmpty() ? -1 : t;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        if (button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT) return false;
        int t = slotAt(mx, my);
        if (t == -1) {
            dropCursor();
            return true;
        }
        clickSlot(t);
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // 26.1+: render -> extractRenderState; çizim sırası katmanı belirler (z yok), renkler ARGB.
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        int ty = 24;
        for (FormattedCharSequence line : font.split(Component.translatable("betterhotbarswap.toolbar.edit_help"), width - 40)) {
            context.centeredText(font, line, width / 2, ty, 0xFFA0A0A0);
            ty += 10;
        }
        panel(context, px, py, PANEL_W, PANEL_H);
        // Sol el slotu için küçük panel (vanilla HUD'daki gibi hotbar'ın solunda).
        panel(context, px + OFF_X - 4, py + 71, 26, 26);
        context.text(font, Component.translatable("container.inventory"), px + 8, py + 6, 0xFF404040, false);
        Inventory inv = minecraft.player != null ? minecraft.player.getInventory() : null;
        int hovered = slotAt(mouseX, mouseY);
        for (int t = 0; t < ToolbarStorage.SIZE; t++) {
            int x = sx(t);
            int y = sy(t);
            slot(context, x, y);
            ItemStack st = slots[t];
            // Boş sol el: vanilla'nın kalkan silüeti (GUI sprite'ı; kaynak paketine de uyar).
            if (t == ToolbarStorage.OFFHAND && st.isEmpty()) {
                context.blitSprite(RenderPipelines.GUI_TEXTURED,
                    net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD, x + 1, y + 1, 16, 16);
            }
            context.item(st, x + 1, y + 1);
            context.itemDecorations(font, st, x + 1, y + 1);
            if (inv != null && !st.isEmpty() && !has(inv, st)) context.fill(x + 1, y + 1, x + 17, y + 17, 0x66FF0000);
            if (t == hovered) context.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
        }
        if (!cursor.isEmpty()) {
            context.item(cursor, mouseX - 8, mouseY - 8);
            context.itemDecorations(font, cursor, mouseX - 8, mouseY - 8);
        } else if (hovered >= 0 && !slots[hovered].isEmpty()) {
            context.setTooltipForNextFrame(font, slots[hovered], mouseX, mouseY);
        }
    }

    private static boolean has(Inventory inv, ItemStack want) {
        for (int i = 0; i < 41; i++) if (inv.getItem(i).getItem() == want.getItem()) return true;
        return false;
    }

    // Vanilla konteyner paneli: siyah çerçeve, beyaz üst-sol / koyu alt-sağ kabartma, C6C6C6 gövde.
    private static void panel(GuiGraphicsExtractor c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, 0xFF000000);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFFFFFFF);
        c.fill(x + 3, y + 3, x + w - 1, y + h - 1, 0xFF555555);
        c.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFC6C6C6);
    }

    // Vanilla çökük slot (18x18).
    static void slot(GuiGraphicsExtractor c, int x, int y) {
        c.fill(x, y, x + 18, y + 18, 0xFF373737);
        c.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        c.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
