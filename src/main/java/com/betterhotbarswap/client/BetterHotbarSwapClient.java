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
    private static final String BUILD_ID = "2026-10-22-1.20.6d";
    private static KeyBinding settingsKey;
    private static KeyBinding hotbar10Key;
    private static KeyBinding toolbarsKey;
    // Modun kendi araç çubuğu kaydet/yükle tuşları (her modda, sadece taşır).
    // Creative'de vanilla C/X vanilla'ya kalır; bu yüzden ayrı tuş şart.
    private static KeyBinding toolbarSaveKey;
    private static KeyBinding toolbarLoadKey;

    public static KeyBinding getToolbarSaveKey() {
        return toolbarSaveKey;
    }

    public static KeyBinding getToolbarLoadKey() {
        return toolbarLoadKey;
    }

    public static KeyBinding getHotbar10Key() {
        return hotbar10Key;
    }

    public static KeyBinding getToolbarsKey() {
        return toolbarsKey;
    }

    @Override
    public void onInitializeClient() {
        com.betterhotbarswap.BetterHotbarSwap.LOGGER.info("[BHS] init build={}", BUILD_ID);
        BHSConfig.load();
        UpdateChecker.start();
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
        // Araç çubukları ekranı: varsayılan H (vanilla'da boş), Kontroller'den değişir.
        toolbarsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.betterhotbarswap.toolbars",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "category.betterhotbarswap"
        ));
        // Varsayılan N/M: vanilla'da boş.
        toolbarSaveKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.betterhotbarswap.toolbar_save",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "category.betterhotbarswap"
        ));
        toolbarLoadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.betterhotbarswap.toolbar_load",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "category.betterhotbarswap"
        ));

        ClientTickEvents.START_CLIENT_TICK.register(com.betterhotbarswap.client.toolbar.SurvivalToolbars::onStartTick);
        ClientTickEvents.END_CLIENT_TICK.register(BetterHotbarSwapClient::onClientTick);
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> BHSDebug.render(drawContext));
    }

    private static void onClientTick(MinecraftClient client) {
        while (settingsKey.wasPressed()) {
            client.setScreen(new BHSConfigScreen(client.currentScreen));
        }
        while (toolbarsKey.wasPressed()) {
            client.setScreen(new com.betterhotbarswap.config.BHSToolbarScreen(client.currentScreen));
        }
        com.betterhotbarswap.client.input.ShiftBulkQuickMove.onTick(client);
        UpdateChecker.onTick(client);
    }
}
