package com.betterhotbarswap.client;

import com.betterhotbarswap.BetterHotbarSwap;
import net.minecraft.client.MinecraftClient;

import java.util.Set;

public final class BHSLanguageSupport {
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of(
        "en_us",
        "tr_tr",
        "de_de",
        "es_es",
        "fr_fr",
        "it_it",
        "pt_br",
        "ru_ru",
        "zh_cn",
        "ja_jp",
        "ko_kr"
    );

    private static String lastLanguage = "";

    private BHSLanguageSupport() {
    }

    public static void tick(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }

        String currentLanguage = client.options.language;
        if (currentLanguage == null || currentLanguage.equals(lastLanguage)) {
            return;
        }

        lastLanguage = currentLanguage;
        if (!SUPPORTED_LANGUAGES.contains(currentLanguage)) {
            BetterHotbarSwap.LOGGER.info("Language '{}' is not bundled by BetterHotbarSwap; Minecraft will use English fallback text for this mod.", currentLanguage);
        }
    }
}