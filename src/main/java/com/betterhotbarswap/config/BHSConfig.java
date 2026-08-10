package com.betterhotbarswap.config;

import com.betterhotbarswap.BetterHotbarSwap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BHSConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("betterhotbarswap.json");

    public static boolean hotbarPriority = true;
    public static boolean shiftHotbarPickup = true;
    public static boolean craftingOutputQuickSwap = true;
    public static boolean stackToInventoryFirst = true;

    private BHSConfig() {
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                hotbarPriority = getBoolean(json, "hotbarPriority", true);
                shiftHotbarPickup = getBoolean(json, "shiftHotbarPickup", true);
                craftingOutputQuickSwap = getBoolean(json, "craftingOutputQuickSwap", true);
                stackToInventoryFirst = getBoolean(json, "stackToInventoryFirst", true);
            } catch (Exception exception) {
                BetterHotbarSwap.LOGGER.warn("Failed to load config, using defaults.", exception);
            }
        }
        save();
    }

    public static void save() {
        JsonObject json = new JsonObject();
        json.addProperty("hotbarPriority", hotbarPriority);
        json.addProperty("shiftHotbarPickup", shiftHotbarPickup);
        json.addProperty("craftingOutputQuickSwap", craftingOutputQuickSwap);
        json.addProperty("stackToInventoryFirst", stackToInventoryFirst);

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException exception) {
            BetterHotbarSwap.LOGGER.warn("Failed to save config.", exception);
        }
    }

    private static boolean getBoolean(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }
}
