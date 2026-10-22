package com.betterhotbarswap.client;

import com.betterhotbarswap.config.BHSConfig;
import com.betterhotbarswap.config.BHSConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.betterhotbarswap.client.debug.BHSDebug;

public final class BetterHotbarSwapClient implements ClientModInitializer {
    // Açılış damgası: HANGİ build'in çalıştığını latest.log'dan kanıtlar
    // (bayat-build testlerini bitirir). Her dağıtımdan önce BUMP edilir.
    private static final String BUILD_ID = "2026-10-22-26.3a";
    // 26.1+: tuş kategorisi kayıtlı nesne; adı "key.category.betterhotbarswap.main" (dil dosyaları).
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("betterhotbarswap", "main"));
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
        settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.betterhotbarswap.settings",
            InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
        ));
        hotbar10Key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.betterhotbarswap.hotbar10",
            InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
        ));
        // Araç çubukları ekranı: varsayılan H (vanilla'da boş), Kontroller'den değişir.
        toolbarsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.betterhotbarswap.toolbars",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_H,
            CATEGORY
        ));
        // Varsayılan N/M: vanilla'da boş.
        toolbarSaveKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.betterhotbarswap.toolbar_save",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_N,
            CATEGORY
        ));
        toolbarLoadKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.betterhotbarswap.toolbar_load",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_M,
            CATEGORY
        ));

        ClientTickEvents.START_CLIENT_TICK.register(com.betterhotbarswap.client.toolbar.SurvivalToolbars::onStartTick);
        ClientTickEvents.END_CLIENT_TICK.register(BetterHotbarSwapClient::onClientTick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betterhotbarswap", "debug"), (graphics, delta) -> BHSDebug.render(graphics));
    }

    private static void onClientTick(Minecraft client) {
        while (settingsKey.consumeClick()) {
            client.gui.setScreen(new BHSConfigScreen(client.gui.screen()));
        }
        while (toolbarsKey.consumeClick()) {
            client.gui.setScreen(new com.betterhotbarswap.config.BHSToolbarScreen(client.gui.screen()));
        }
        com.betterhotbarswap.client.input.ShiftBulkQuickMove.onTick(client);
        UpdateChecker.onTick(client);
    }
}
