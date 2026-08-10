package com.betterhotbarswap.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class BHSConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int ROW_HEIGHT = 28;
    private static final int NAME_COLOR = 0xFF9A9A9A;
    private static final int ON_COLOR = 0xFF55FF55;
    private static final int OFF_COLOR = 0xFFFF5555;

    private final Screen parent;
    private final List<ToggleRow> rows = new ArrayList<>();

    public BHSConfigScreen(Screen parent) {
        super(Text.translatable("betterhotbarswap.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        int centerX = width / 2;
        int top = height / 2 - 82;
        addToggle(centerX, top, "betterhotbarswap.settings.hotbar_priority", BHSConfig.hotbarPriority, value -> BHSConfig.hotbarPriority = value);
        addToggle(centerX, top + ROW_HEIGHT, "betterhotbarswap.settings.shift_hotbar_pickup", BHSConfig.shiftHotbarPickup, value -> BHSConfig.shiftHotbarPickup = value);
        addToggle(centerX, top + ROW_HEIGHT * 2, "betterhotbarswap.settings.crafting_output_swap", BHSConfig.craftingOutputQuickSwap, value -> BHSConfig.craftingOutputQuickSwap = value);
        addToggle(centerX, top + ROW_HEIGHT * 3, "betterhotbarswap.settings.stack_first", BHSConfig.stackToInventoryFirst, value -> BHSConfig.stackToInventoryFirst = value);
        addDrawableChild(ButtonWidget.builder(Text.translatable("betterhotbarswap.settings.close"), button -> close())
            .dimensions(centerX - 70, top + ROW_HEIGHT * 5, 140, 20)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 118, 0xFFFFFF);
        for (ToggleRow row : rows) {
            row.render(context);
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
        return false;
    }

    private void addToggle(int centerX, int y, String translationKey, boolean initialValue, ToggleConsumer consumer) {
        ToggleRow row = new ToggleRow(centerX - PANEL_WIDTH / 2, y, translationKey, initialValue, consumer);
        rows.add(row);
        addDrawableChild(row.button);
    }

    private static final class ToggleRow {
        private final int x;
        private final int y;
        private final String translationKey;
        private final ToggleConsumer consumer;
        private boolean value;
        private final ButtonWidget button;

        private ToggleRow(int x, int y, String translationKey, boolean initialValue, ToggleConsumer consumer) {
            this.x = x;
            this.y = y;
            this.translationKey = translationKey;
            this.value = initialValue;
            this.consumer = consumer;
            this.button = ButtonWidget.builder(label(initialValue), clicked -> toggle())
                .dimensions(x + PANEL_WIDTH - 62, y + 4, 62, 20)
                .build();
        }

        private void toggle() {
            value = !value;
            consumer.accept(value);
            button.setMessage(label(value));
            BHSConfig.save();
        }

        private void render(DrawContext context) {
            context.drawTextWithShadow(MinecraftText.textRenderer(), Text.translatable(translationKey), x, y + 9, NAME_COLOR);
            context.fill(x, y + ROW_HEIGHT - 3, x + PANEL_WIDTH, y + ROW_HEIGHT - 2, 0x55222222);
        }

        private static Text label(boolean value) {
            return Text.literal(value ? "ON" : "OFF").styled(style -> style.withColor(value ? ON_COLOR : OFF_COLOR));
        }
    }

    private static final class MinecraftText {
        private static net.minecraft.client.font.TextRenderer textRenderer() {
            return net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        }
    }

    @FunctionalInterface
    private interface ToggleConsumer {
        void accept(boolean value);
    }
}