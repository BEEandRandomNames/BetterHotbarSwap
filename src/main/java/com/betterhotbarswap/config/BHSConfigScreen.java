package com.betterhotbarswap.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class BHSConfigScreen extends Screen {
    private final Screen parent;

    public BHSConfigScreen(Screen parent) {
        super(Text.translatable("betterhotbarswap.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int y = height / 2 - 60;
        addDrawableChild(toggle(centerX - 100, y, "betterhotbarswap.settings.hotbar_priority", BHSConfig.hotbarPriority, value -> BHSConfig.hotbarPriority = value));
        addDrawableChild(toggle(centerX - 100, y + 24, "betterhotbarswap.settings.shift_hotbar_pickup", BHSConfig.shiftHotbarPickup, value -> BHSConfig.shiftHotbarPickup = value));
        addDrawableChild(toggle(centerX - 100, y + 48, "betterhotbarswap.settings.crafting_output_swap", BHSConfig.craftingOutputQuickSwap, value -> BHSConfig.craftingOutputQuickSwap = value));
        addDrawableChild(toggle(centerX - 100, y + 72, "betterhotbarswap.settings.stack_first", BHSConfig.stackToInventoryFirst, value -> BHSConfig.stackToInventoryFirst = value));
        addDrawableChild(ButtonWidget.builder(Text.translatable("betterhotbarswap.settings.close"), button -> close())
            .dimensions(centerX - 100, y + 108, 200, 20)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 92, 0xFFFFFF);
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

    private static ButtonWidget toggle(int x, int y, String translationKey, boolean initialValue, ToggleConsumer consumer) {
        boolean[] value = {initialValue};
        return ButtonWidget.builder(label(translationKey, value[0]), button -> {
            value[0] = !value[0];
            consumer.accept(value[0]);
            button.setMessage(label(translationKey, value[0]));
            BHSConfig.save();
        }).dimensions(x, y, 200, 20).build();
    }

    private static Text label(String translationKey, boolean value) {
        return Text.literal(value ? "ON  " : "OFF  ").append(Text.translatable(translationKey));
    }

    @FunctionalInterface
    private interface ToggleConsumer {
        void accept(boolean value);
    }
}
