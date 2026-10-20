package com.betterhotbarswap.client;

import com.betterhotbarswap.config.BHSConfig;
import com.betterhotbarswap.config.BHSConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import com.betterhotbarswap.client.debug.BHSDebug;

public final class BetterHotbarSwapClient implements ClientModInitializer {
    // Açılış damgası: HANGİ build'in çalıştığını latest.log'dan kanıtlar
    // (bayat-build testlerini bitirir). Her dağıtımdan önce BUMP edilir.
    private static final String BUILD_ID = "2026-10-21-a";
    private static KeyBinding settingsKey;
    private static KeyBinding hotbar10Key;

    public static KeyBinding getHotbar10Key() {
        return hotbar10Key;
    }

    @Override
    public void onInitializeClient() {
        com.betterhotbarswap.BetterHotbarSwap.LOGGER.info("[BHS] init build={}", BUILD_ID);
        BHSConfig.load();
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.betterhotbarswap.settings",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "category.betterhotbarswap"
        ));
        hotbar10Key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.betterhotbarswap.hotbar10",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "category.betterhotbarswap"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(BetterHotbarSwapClient::onClientTick);
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> BHSDebug.render(drawContext));
    }

    private static void onClientTick(MinecraftClient client) {
        while (settingsKey.wasPressed()) {
            client.setScreen(new BHSConfigScreen(client.currentScreen));
        }
        com.betterhotbarswap.client.input.ShiftBulkQuickMove.onTick(client);
    }
}
