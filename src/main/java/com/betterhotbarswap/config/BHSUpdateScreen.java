package com.betterhotbarswap.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.util.List;

// Güncelleme sorusu (UpdateChecker). Evet = indirme sayfası, Şimdi değil = sonraki
// açılışta yine sorar, Hayır = bir daha hiç sormaz (config). ESC = Şimdi değil.
public final class BHSUpdateScreen extends Screen {
    private static final int BTN_W = 100;
    // Özel ad çevrilmez; başlıkta altın sarısı, mesajda düz beyaz (kullanıcı isteği).
    private static final Text NAME = Text.literal("BetterHotbarSwap").formatted(Formatting.GOLD);

    private final Screen parent;
    private final String url;
    private final Text message;
    // 1.21.11+: MultilineText.draw DrawnTextConsumer ister; satırlar elle bölünüp ortalanır (26.1 ile aynı).
    private List<OrderedText> lines = List.of();

    public BHSUpdateScreen(Screen parent, String version, String url) {
        super(Text.translatable("betterhotbarswap.update.title", NAME));
        this.parent = parent;
        this.url = url;
        this.message = Text.translatable("betterhotbarswap.update.message", NAME.getString(), version);
    }

    @Override
    protected void init() {
        lines = textRenderer.wrapLines(message, width - 50);
        int y = Math.min(90 + lines.size() * 9 + 20, height - 24);
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
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 70, 0xFFFFFFFF);
        for (int i = 0; i < lines.size(); i++) context.drawCenteredTextWithShadow(textRenderer, lines.get(i), width / 2, 90 + i * 9, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
