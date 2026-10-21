package com.betterhotbarswap.config;

import net.minecraft.client.font.MultilineText;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

// Güncelleme sorusu (UpdateChecker). Evet = indirme sayfası, Şimdi değil = sonraki
// açılışta yine sorar, Hayır = bir daha hiç sormaz (config). ESC = Şimdi değil.
public final class BHSUpdateScreen extends Screen {
    private static final int BTN_W = 100;
    // Özel ad çevrilmez; altın sarısı (kullanıcı isteği). Dil metinleri onu argüman alır.
    private static final Text NAME = Text.literal("BetterHotbarSwap").formatted(Formatting.GOLD);

    private final Screen parent;
    private final String url;
    private final Text message;
    private MultilineText lines = MultilineText.EMPTY;

    public BHSUpdateScreen(Screen parent, String version, String url) {
        super(Text.translatable("betterhotbarswap.update.title", NAME));
        this.parent = parent;
        this.url = url;
        this.message = Text.translatable("betterhotbarswap.update.message", NAME, version);
    }

    @Override
    protected void init() {
        lines = MultilineText.create(textRenderer, message, width - 50);
        int y = Math.min(90 + lines.count() * 9 + 20, height - 24);
        int x = width / 2 - (BTN_W * 3 + 8) / 2;
        addDrawableChild(ButtonWidget.builder(ScreenTexts.YES.copy().formatted(Formatting.GREEN), b -> {
            Util.getOperatingSystem().open(url);
            close();
        }).dimensions(x, y, BTN_W, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("betterhotbarswap.update.later").formatted(Formatting.YELLOW), b -> close())
            .dimensions(x + BTN_W + 4, y, BTN_W, 20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.NO.copy().formatted(Formatting.RED), b -> {
            BHSConfig.updateCheck = false;
            BHSConfig.save();
            close();
        }).dimensions(x + (BTN_W + 4) * 2, y, BTN_W, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta); // 1.20.2+: Screen.render arka planı çizer; içerik üstüne
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 70, 0xFFFFFF);
        lines.drawCenterWithShadow(context, width / 2, 90);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
