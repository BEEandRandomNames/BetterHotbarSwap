package com.betterhotbarswap.config;

import com.betterhotbarswap.client.toolbar.SurvivalToolbars;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// Kayıtlı araç çubukları: SADECE dolu olanlar listelenir (vanilla envanter stili
// görünümü + [Yükle] [Sil]). [Ekle] şu anki hotbar'ı ilk boş yuvaya kaydeder.
// Satır numarası = kayıt yuvası = dünyadaki "Yükle tuşu + o kısayol".
// Survival'da envanterde OLMAYAN eşyalar kırmızı tonla gösterilir.
public final class BHSToolbarScreen extends Screen {
    private static final net.minecraft.resources.ResourceLocation HOTBAR = net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/hotbar");
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
        super(Component.translatable("betterhotbarswap.toolbar.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        shown.clear();
        for (int i = 0; i < 9; i++) if (!SurvivalToolbars.isEmpty(minecraft, i)) shown.add(i);
        left = width / 2 - ROW_W / 2;
        top = 32;
        for (int r = 0; r < shown.size(); r++) {
            int idx = shown.get(r);
            int y = top + r * ROW_H;
            int bx = left + 14 + HOTBAR_W + 4;
            addRenderableWidget(Button.builder(Component.translatable("betterhotbarswap.toolbar.load"),
                b -> SurvivalToolbars.load(minecraft, idx)).bounds(bx, y + 1, BTN_W, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("betterhotbarswap.toolbar.delete"), b -> {
                SurvivalToolbars.delete(minecraft, idx);
                rebuildWidgets();
            }).bounds(bx + BTN_W + 2, y + 1, BTN_W, 20).build());
        }
        int free = SurvivalToolbars.firstFree(minecraft);
        Button add = addRenderableWidget(Button.builder(Component.translatable("betterhotbarswap.toolbar.add"), b -> {
            SurvivalToolbars.save(minecraft, free, com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarLoadKey());
            rebuildWidgets();
        }).bounds(width / 2 - 155, height - 28, 150, 20)
            .tooltip(Tooltip.create(Component.translatable(free == -1 ? "betterhotbarswap.toolbar.full" : "betterhotbarswap.toolbar.add.desc")))
            .build());
        add.active = free != -1;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .bounds(width / 2 + 5, height - 28, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta); // 1.20.2+: Screen.render arka plan + düğmeleri çizer; içerik üstüne
        context.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        if (!shown.isEmpty()) {
            context.drawCenteredString(font, Component.translatable("betterhotbarswap.toolbar.edit_hint"),
                width / 2, 19, 0xA0A0A0);
        }
        if (shown.isEmpty()) {
            context.drawCenteredString(font, Component.translatable("betterhotbarswap.toolbar.none"),
                width / 2, height / 2 - 10, 0xA0A0A0);
        }
        boolean hoveredBar = false;
        Inventory inv = minecraft.player != null ? minecraft.player.getInventory() : null;
        for (int r = 0; r < shown.size(); r++) {
            int idx = shown.get(r);
            int y = top + r * ROW_H;
            context.drawString(font, String.valueOf(idx + 1), left + 2, y + 7, 0xFFFFFF);
            int hx = left + 14;
            // Oyunun KENDİ hotbar görseli (oyundakiyle birebir, kaynak paketine uyar).
            // Adı/API'si sürümle değişir: 1.20.1 widgets.png, 1.20.2+ "hud/hotbar" sprite'ı
            // (javap: InGameHud aynısını çiziyor). Port prosedüründe her sürüm kontrol edilir.
            context.blitSprite(HOTBAR, hx, y, HOTBAR_W, 22);
            boolean over = mouseX >= hx && mouseX < hx + HOTBAR_W && mouseY >= y && mouseY < y + 22;
            java.util.List<ItemStack> entry = SurvivalToolbars.storage(minecraft).get(idx);
            for (int s = 0; s < 9; s++) {
                ItemStack st = entry.get(s);
                if (st.isEmpty()) continue;
                int ix = hx + 3 + s * 20;
                int iy = y + 3;
                context.renderItem(st, ix, iy);
                context.renderItemDecorations(font, st, ix, iy);
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
        Component hint = Component.translatable("betterhotbarswap.toolbar.hint",
            com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarSaveKey().getTranslatedKeyMessage(),
            com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarLoadKey().getTranslatedKeyMessage());
        context.drawCenteredString(font, hint, width / 2, height - 42, 0xA0A0A0);
        long since = System.currentTimeMillis() - savedAtMs;
        int savedRow = shown.indexOf(savedIndex);
        if (since < SAVED_SHOW_MS && savedRow >= 0) {
            // Son 500 ms'de solar (alfa 4'ün altında font hiç çizmez).
            int alpha = (int) (255 * Math.min(1.0, (SAVED_SHOW_MS - since) / 500.0));
            Component saved = Component.translatable("betterhotbarswap.toolbar.saved");
            int x = Math.max(2, left - 4 - font.width(saved));
            if (alpha > 4) context.drawString(font, saved, x, top + savedRow * ROW_H + 7, (alpha << 24) | 0x55FF55);
        }
        // Vanilla'nın "Düzenle" çevirisi: her dilde hazır.
        if (hoveredBar) context.renderTooltip(font, Component.translatable("selectWorld.edit"), mouseX, mouseY);
    }

    private static boolean has(Inventory inv, ItemStack want) {
        for (int i = 0; i < 36; i++) if (inv.getItem(i).getItem() == want.getItem()) return true;
        return false;
    }

    // Çubuğun üzerine tıklayınca düzenleyici açılır.
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (int r = 0; r < shown.size(); r++) {
            int y = top + r * ROW_H;
            if (button == 0 && mx >= left + 14 && mx < left + 14 + HOTBAR_W && my >= y && my < y + 22) {
                minecraft.setScreen(new BHSToolbarEditScreen(this, shown.get(r)));
                return true;
            }
        }
        return false;
    }

    // Açan tuşa tekrar basınca kapanır (vanilla envanter tuşu gibi).
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        var key = com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarsKey();
        if (key != null && key.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
