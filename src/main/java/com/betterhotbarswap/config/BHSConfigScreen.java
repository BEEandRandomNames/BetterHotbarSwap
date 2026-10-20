package com.betterhotbarswap.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class BHSConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int ROW_HEIGHT = 24;
    private static final int LABEL_COLOR = 0xAAAAAA;
    private static final int TOGGLE_WIDTH = 72;

    private final Screen parent;
    private final List<ToggleRow> toggleRows = new ArrayList<>();

    public BHSConfigScreen(Screen parent) {
        super(Text.translatable("betterhotbarswap.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int y = height / 2 - 58;
        toggleRows.clear();
        addToggleRow(centerX, y, "betterhotbarswap.settings.hotbar_priority", BHSConfig.hotbarPriority, value -> BHSConfig.hotbarPriority = value);
        addToggleRow(centerX, y + ROW_HEIGHT, "betterhotbarswap.settings.shift_hotbar_pickup", BHSConfig.shiftHotbarPickup, value -> BHSConfig.shiftHotbarPickup = value);
        addToggleRow(centerX, y + ROW_HEIGHT * 2, "betterhotbarswap.settings.debug", BHSConfig.debugEnabled, value -> BHSConfig.debugEnabled = value);
        addToggleRow(centerX, y + ROW_HEIGHT * 3, "betterhotbarswap.settings.tidy_collect", BHSConfig.tidyCollect, value -> BHSConfig.tidyCollect = value);
        addDrawableChild(ButtonWidget.builder(Text.translatable("betterhotbarswap.settings.close"), button -> close())
            .dimensions(centerX - 100, y + ROW_HEIGHT * 4, 200, 20)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 90, 0xFFFFFF);
        for (ToggleRow toggleRow : toggleRows) {
            toggleRow.render(context, textRenderer);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        BHSConfig.save();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    private void addToggleRow(int centerX, int y, String translationKey, boolean initialValue, ToggleConsumer consumer) {
        int left = centerX - PANEL_WIDTH / 2;
        ButtonWidget button = ButtonWidget.builder(toggleLabel(initialValue), widget -> {
            ToggleRow row = toggleRows.stream()
                .filter(toggleRow -> toggleRow.button == widget)
                .findFirst()
                .orElse(null);
            if (row == null) {
                return;
            }
            row.value = !row.value;
            consumer.accept(row.value);
            widget.setMessage(toggleLabel(row.value));
            BHSConfig.save();
        }).dimensions(left + PANEL_WIDTH - TOGGLE_WIDTH, y - 2, TOGGLE_WIDTH, 20).build();
        ToggleRow row = new ToggleRow(left, y, translationKey, button, initialValue);
        toggleRows.add(row);
        addDrawableChild(button);
    }

    private static Text toggleLabel(boolean value) {
        return Text.literal(value ? "ON" : "OFF").styled(style -> style.withColor(value ? 0x55FF55 : 0xFF5555));
    }

    private static final class ToggleRow {
        private final int x;
        private final int y;
        private final String translationKey;
        private final ButtonWidget button;
        private boolean value;

        private ToggleRow(int x, int y, String translationKey, ButtonWidget button, boolean value) {
            this.x = x;
            this.y = y;
            this.translationKey = translationKey;
            this.button = button;
            this.value = value;
        }

        private void render(DrawContext context, net.minecraft.client.font.TextRenderer textRenderer) {
            context.drawTextWithShadow(textRenderer, Text.translatable(translationKey), x, y + 4, LABEL_COLOR);
        }
    }

    @FunctionalInterface
    private interface ToggleConsumer {
        void accept(boolean value);
    }
}
