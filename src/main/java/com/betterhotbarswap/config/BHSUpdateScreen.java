package com.betterhotbarswap.config;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

// Güncelleme sorusu (UpdateChecker). Evet = indirme sayfası, Şimdi değil = sonraki
// açılışta yine sorar, Hayır = bir daha hiç sormaz (config). ESC = Şimdi değil.
public final class BHSUpdateScreen extends Screen {
    private static final int BTN_W = 100;
    // Özel ad çevrilmez; başlıkta altın sarısı, mesajda düz beyaz (kullanıcı isteği).
    private static final Component NAME = Component.literal("BetterHotbarSwap").withStyle(ChatFormatting.GOLD);

    private final Screen parent;
    private final String url;
    private final Component message;
    private MultiLineLabel lines = MultiLineLabel.EMPTY;

    public BHSUpdateScreen(Screen parent, String version, String url) {
        super(Component.translatable("betterhotbarswap.update.title", NAME));
        this.parent = parent;
        this.url = url;
        this.message = Component.translatable("betterhotbarswap.update.message", NAME.getString(), version);
    }

    @Override
    protected void init() {
        lines = MultiLineLabel.create(font, message, width - 50);
        int y = Math.min(90 + lines.getLineCount() * 9 + 20, height - 24);
        int x = width / 2 - (BTN_W * 3 + 8) / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_YES.copy().withStyle(ChatFormatting.GREEN), b -> {
            Util.getPlatform().openUri(url);
            onClose();
        }).bounds(x, y, BTN_W, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("betterhotbarswap.update.later").withStyle(ChatFormatting.YELLOW), b -> onClose())
            .bounds(x + BTN_W + 4, y, BTN_W, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_NO.copy().withStyle(ChatFormatting.RED), b -> {
            BHSConfig.updateCheck = false;
            BHSConfig.save();
            onClose();
        }).bounds(x + (BTN_W + 4) * 2, y, BTN_W, 20).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta); // 1.20.2+: Screen.render arka planı çizer; içerik üstüne
        context.drawCenteredString(font, title, width / 2, 70, 0xFFFFFF);
        lines.renderCentered(context, width / 2, 90);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
