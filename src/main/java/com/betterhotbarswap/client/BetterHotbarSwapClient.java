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

public final class BetterHotbarSwapClient implements ClientModInitializer {
    private static KeyBinding settingsKey;

    @Override
    public void onInitializeClient() {
        BHSConfig.load();
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.betterhotbarswap.settings",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "category.betterhotbarswap"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(BetterHotbarSwapClient::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        while (settingsKey.wasPressed()) {
            client.setScreen(new BHSConfigScreen(client.currentScreen));
        }
        com.betterhotbarswap.client.input.HotbarInputController.tick(client);
    }
}
