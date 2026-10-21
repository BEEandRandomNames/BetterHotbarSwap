package com.betterhotbarswap.client;

import com.betterhotbarswap.config.BHSConfig;
import com.betterhotbarswap.config.BHSConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.betterhotbarswap.client.debug.BHSDebug;

public final class BetterHotbarSwapClient implements ClientModInitializer {
    // Açılış damgası: HANGİ build'in çalıştığını latest.log'dan kanıtlar
    // (bayat-build testlerini bitirir). Her dağıtımdan önce BUMP edilir.
    private static final String BUILD_ID = "2026-10-21-1.21a";
    private static KeyMapping settingsKey;
    private static KeyMapping hotbar10Key;
    private static KeyMapping toolbarsKey;
    // Modun kendi araç çubuğu kaydet/yükle tuşları (her modda, sadece taşır).
    // Creative'de vanilla C/X vanilla'ya kalır; bu yüzden ayrı tuş şart.
    private static KeyMapping toolbarSaveKey;
    private static KeyMapping toolbarLoadKey;

    public static KeyMapping getToolbarSaveKey() {
        return toolbarSaveKey;
    }

    public static KeyMapping getToolbarLoadKey() {
        return toolbarLoadKey;
    }

    public static KeyMapping getHotbar10Key() {
        return hotbar10Key;
    }

    public static KeyMapping getToolbarsKey() {
        return toolbarsKey;
    }

    @Override
    public void onInitializeClient() {
        com.betterhotbarswap.BetterHotbarSwap.LOGGER.info("[BHS] init build={}", BUILD_ID);
        BHSConfig.load();
        UpdateChecker.start();
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.betterhotbarswap.settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "category.betterhotbarswap"
        ));
        hotbar10Key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.betterhotbarswap.hotbar10",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "category.betterhotbarswap"
        ));
        // Araç çubukları ekranı: varsayılan H (vanilla'da boş), Kontroller'den değişir.
        toolbarsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.betterhotbarswap.toolbars",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "category.betterhotbarswap"
        ));
        // Varsayılan N/M: vanilla'da boş.
        toolbarSaveKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.betterhotbarswap.toolbar_save",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "category.betterhotbarswap"
        ));
        toolbarLoadKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.betterhotbarswap.toolbar_load",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "category.betterhotbarswap"
        ));

        ClientTickEvents.START_CLIENT_TICK.register(com.betterhotbarswap.client.toolbar.SurvivalToolbars::onStartTick);
        ClientTickEvents.END_CLIENT_TICK.register(BetterHotbarSwapClient::onClientTick);
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> BHSDebug.render(drawContext));
    }

    private static void onClientTick(Minecraft client) {
        while (settingsKey.consumeClick()) {
            client.setScreen(new BHSConfigScreen(client.screen));
        }
        while (toolbarsKey.consumeClick()) {
            client.setScreen(new com.betterhotbarswap.config.BHSToolbarScreen(client.screen));
        }
        com.betterhotbarswap.client.input.ShiftBulkQuickMove.onTick(client);
        UpdateChecker.onTick(client);
    }
}
