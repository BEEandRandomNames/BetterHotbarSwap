package com.betterhotbarswap.config;

import com.betterhotbarswap.client.toolbar.SurvivalToolbars;
import com.betterhotbarswap.client.toolbar.ToolbarIcons;
import com.betterhotbarswap.client.toolbar.ToolbarStorage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

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
        super(Text.translatable("betterhotbarswap.toolbar.edit_title", index + 1));
        this.parent = parent;
        this.index = index;
        List<ItemStack> tpl = SurvivalToolbars.storage(net.minecraft.client.MinecraftClient.getInstance()).get(index);
        for (int t = 0; t < ToolbarStorage.SIZE; t++) slots[t] = tpl.get(t).copy();
    }

    @Override
    protected void init() {
        px = width / 2 - PANEL_W / 2;
        py = Math.max(40, height / 2 - PANEL_H / 2 - 10);
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> {
            dropCursor();
            List<ItemStack> tpl = SurvivalToolbars.storage(client).get(index);
            for (int t = 0; t < ToolbarStorage.SIZE; t++) tpl.set(t, slots[t].copy());
            SurvivalToolbars.storage(client).save();
            BHSToolbarScreen.savedAtMs = System.currentTimeMillis();
            BHSToolbarScreen.savedIndex = index;
            close();
        }).dimensions(width / 2 - 155, height - 28, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close())
            .dimensions(width / 2 + 5, height - 28, 150, 20).build());
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
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0 && button != 1) return false;
        int t = slotAt(mx, my);
        if (t == -1) {
            dropCursor();
            return true;
        }
        clickSlot(t);
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta); // 1.20.2+: Screen.render arka plan + düğmeleri çizer; içerik üstüne
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        int ty = 24;
        for (OrderedText line : textRenderer.wrapLines(Text.translatable("betterhotbarswap.toolbar.edit_help"), width - 40)) {
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, ty, 0xA0A0A0);
            ty += 10;
        }
        panel(context, px, py, PANEL_W, PANEL_H);
        // Sol el slotu için küçük panel (vanilla HUD'daki gibi hotbar'ın solunda).
        panel(context, px + OFF_X - 4, py + 71, 26, 26);
        context.drawText(textRenderer, Text.translatable("container.inventory"), px + 8, py + 6, 0x404040, false);
        PlayerInventory inv = client.player != null ? client.player.getInventory() : null;
        int hovered = slotAt(mouseX, mouseY);
        for (int t = 0; t < ToolbarStorage.SIZE; t++) {
            int x = sx(t);
            int y = sy(t);
            slot(context, x, y);
            ItemStack st = slots[t];
            // Boş sol el: vanilla'nın kalkan silüeti (kaynak paketine de uyar).
            if (t == ToolbarStorage.OFFHAND && st.isEmpty()) {
                // 1.21.4+: boş slot simgeleri GUI sprite'ı (container/slot/shield).
                context.drawGuiTexture(net.minecraft.client.render.RenderLayer::getGuiTextured,
                    net.minecraft.screen.PlayerScreenHandler.EMPTY_OFF_HAND_SLOT_TEXTURE, x + 1, y + 1, 16, 16);
            }
            if (!ToolbarIcons.draw(context, textRenderer, st, x + 1, y + 1)) {
                context.drawItem(st, x + 1, y + 1);
                context.drawStackOverlay(textRenderer, st, x + 1, y + 1);
            }
            if (inv != null && !st.isEmpty() && !has(inv, st)) context.fill(x + 1, y + 1, x + 17, y + 17, 300, 0x66FF0000);
            if (t == hovered) context.fill(x + 1, y + 1, x + 17, y + 17, 300, 0x80FFFFFF);
        }
        if (!cursor.isEmpty()) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 400);
            if (!ToolbarIcons.draw(context, textRenderer, cursor, mouseX - 8, mouseY - 8)) {
                context.drawItem(cursor, mouseX - 8, mouseY - 8);
                context.drawStackOverlay(textRenderer, cursor, mouseX - 8, mouseY - 8);
            }
            context.getMatrices().pop();
        } else if (hovered >= 0 && !slots[hovered].isEmpty()) {
            // Yabancı eşya: kayıttaki adı + hangi sürümde kaydedildiği.
            List<Text> foreign = ToolbarIcons.tooltip(slots[hovered]);
            if (foreign != null) context.drawTooltip(textRenderer, foreign, mouseX, mouseY);
            else context.drawItemTooltip(textRenderer, slots[hovered], mouseX, mouseY);
        }
    }

    private static boolean has(PlayerInventory inv, ItemStack want) {
        for (int i = 0; i < 41; i++) if (inv.getStack(i).getItem() == want.getItem()) return true;
        return false;
    }

    // Vanilla konteyner paneli: siyah çerçeve, beyaz üst-sol / koyu alt-sağ kabartma, C6C6C6 gövde.
    private static void panel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, 0xFF000000);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFFFFFFF);
        c.fill(x + 3, y + 3, x + w - 1, y + h - 1, 0xFF555555);
        c.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFC6C6C6);
    }

    // Vanilla çökük slot (18x18).
    static void slot(DrawContext c, int x, int y) {
        c.fill(x, y, x + 18, y + 18, 0xFF373737);
        c.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        c.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
