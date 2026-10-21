package com.betterhotbarswap.config;

import com.betterhotbarswap.client.toolbar.SurvivalToolbars;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

// Kayıtlı araç çubukları: SADECE dolu olanlar listelenir (vanilla hotbar
// görünümü + [Yükle] [Sil]). [Ekle] şu anki hotbar'ı ilk boş yuvaya kaydeder.
// Satır numarası = kayıt yuvası = dünyadaki "Yükle tuşu + o kısayol".
// Survival'da envanterde OLMAYAN eşyalar kırmızı tonla gösterilir.
public final class BHSToolbarScreen extends Screen {
    private static final Identifier WIDGETS = new Identifier("textures/gui/widgets.png");
    private static final int HOTBAR_W = 182;
    private static final int ROW_H = 24;
    private static final int BTN_W = 50;
    private static final int ROW_W = 14 + HOTBAR_W + 4 + BTN_W * 2 + 2;
    // Düzenleyicide "Bitti" anı + yuva: o çubuğun solunda kısa süre "Kaydedildi".
    static long savedAtMs;
    static int savedIndex = -1;
    private static final long SAVED_SHOW_MS = 2500;

    private final Screen parent;
    private final List<Integer> shown = new ArrayList<>();
    private int left;
    private int top;

    public BHSToolbarScreen(Screen parent) {
        super(Text.translatable("betterhotbarswap.toolbar.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        shown.clear();
        for (int i = 0; i < 9; i++) if (!SurvivalToolbars.isEmpty(client, i)) shown.add(i);
        left = width / 2 - ROW_W / 2;
        top = 32;
        for (int r = 0; r < shown.size(); r++) {
            int idx = shown.get(r);
            int y = top + r * ROW_H;
            int bx = left + 14 + HOTBAR_W + 4;
            addDrawableChild(ButtonWidget.builder(Text.translatable("betterhotbarswap.toolbar.load"),
                b -> SurvivalToolbars.load(client, idx)).dimensions(bx, y + 1, BTN_W, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("betterhotbarswap.toolbar.delete"), b -> {
                SurvivalToolbars.delete(client, idx);
                clearAndInit();
            }).dimensions(bx + BTN_W + 2, y + 1, BTN_W, 20).build());
        }
        int free = SurvivalToolbars.firstFree(client);
        ButtonWidget add = addDrawableChild(ButtonWidget.builder(Text.translatable("betterhotbarswap.toolbar.add"), b -> {
            SurvivalToolbars.save(client, free, com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarLoadKey());
            clearAndInit();
        }).dimensions(width / 2 - 155, height - 28, 150, 20)
            .tooltip(Tooltip.of(Text.translatable(free == -1 ? "betterhotbarswap.toolbar.full" : "betterhotbarswap.toolbar.add.desc")))
            .build());
        add.active = free != -1;
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
            .dimensions(width / 2 + 5, height - 28, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
        if (!shown.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("betterhotbarswap.toolbar.edit_hint"),
                width / 2, 19, 0xA0A0A0);
        }
        if (shown.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("betterhotbarswap.toolbar.none"),
                width / 2, height / 2 - 10, 0xA0A0A0);
        }
        boolean hoveredBar = false;
        PlayerInventory inv = client.player != null ? client.player.getInventory() : null;
        for (int r = 0; r < shown.size(); r++) {
            int idx = shown.get(r);
            int y = top + r * ROW_H;
            context.drawTextWithShadow(textRenderer, String.valueOf(idx + 1), left + 2, y + 7, 0xFFFFFF);
            int hx = left + 14;
            context.drawTexture(WIDGETS, hx, y, 0, 0, HOTBAR_W, 22);
            boolean over = mouseX >= hx && mouseX < hx + HOTBAR_W && mouseY >= y && mouseY < y + 22;
            java.util.List<ItemStack> entry = SurvivalToolbars.storage(client).get(idx);
            for (int s = 0; s < 9; s++) {
                ItemStack st = entry.get(s);
                if (st.isEmpty()) continue;
                int ix = hx + 3 + s * 20;
                int iy = y + 3;
                context.drawItem(st, ix, iy);
                context.drawItemInSlot(textRenderer, st, ix, iy);
                if (inv != null && !has(inv, st)) {
                    context.fill(ix, iy, ix + 16, iy + 16, 300, 0x66FF0000);
                }
            }
            // Tıklanabilir hissi: üstüne gelince tüm çubuk beyazlaşır.
            if (over) {
                context.fill(hx, y, hx + HOTBAR_W, y + 22, 310, 0x60FFFFFF);
                hoveredBar = true;
            }
        }
        Text hint = Text.translatable("betterhotbarswap.toolbar.hint",
            com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarSaveKey().getBoundKeyLocalizedText(),
            com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarLoadKey().getBoundKeyLocalizedText());
        context.drawCenteredTextWithShadow(textRenderer, hint, width / 2, height - 42, 0xA0A0A0);
        long since = System.currentTimeMillis() - savedAtMs;
        int savedRow = shown.indexOf(savedIndex);
        if (since < SAVED_SHOW_MS && savedRow >= 0) {
            // Son 500 ms'de solar (alfa 4'ün altında font hiç çizmez).
            int alpha = (int) (255 * Math.min(1.0, (SAVED_SHOW_MS - since) / 500.0));
            Text saved = Text.translatable("betterhotbarswap.toolbar.saved");
            int x = Math.max(2, left - 4 - textRenderer.getWidth(saved));
            if (alpha > 4) context.drawTextWithShadow(textRenderer, saved, x, top + savedRow * ROW_H + 7, (alpha << 24) | 0x55FF55);
        }
        super.render(context, mouseX, mouseY, delta);
        // Vanilla'nın "Düzenle" çevirisi: her dilde hazır.
        if (hoveredBar) context.drawTooltip(textRenderer, Text.translatable("selectWorld.edit"), mouseX, mouseY);
    }

    private static boolean has(PlayerInventory inv, ItemStack want) {
        for (int i = 0; i < 36; i++) if (inv.getStack(i).getItem() == want.getItem()) return true;
        return false;
    }

    // Çubuğun üzerine tıklayınca düzenleyici açılır.
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (int r = 0; r < shown.size(); r++) {
            int y = top + r * ROW_H;
            if (button == 0 && mx >= left + 14 && mx < left + 14 + HOTBAR_W && my >= y && my < y + 22) {
                client.setScreen(new BHSToolbarEditScreen(this, shown.get(r)));
                return true;
            }
        }
        return false;
    }

    // Açan tuşa tekrar basınca kapanır (vanilla envanter tuşu gibi).
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        var key = com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarsKey();
        if (key != null && key.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
