package com.betterhotbarswap.client;

import com.betterhotbarswap.BetterHotbarSwap;
import com.betterhotbarswap.config.BHSConfig;
import com.betterhotbarswap.config.BHSUpdateScreen;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

// Güncelleme bildirimi: Modrinth'te BU Minecraft sürümüne çıkmış, yüklü olandan
// YÜKSEK bir mod sürümü varsa ana menüde bir kez sorar. Port'lar mod sürümünü
// değiştirmez (her MC'de 1.0.0), bu yüzden sadece yeni fikir sürümleri (1.1.0) sorulur.
// Hayır -> config updateCheck=false: istek bile atılmaz.
public final class UpdateChecker {
    // İki sitede de proje adı bu olmalı (Modrinth ve CurseForge adresi).
    private static final String SLUG = "betterhotbarswap";
    private static volatile String newVersion;
    private static boolean shown;

    private UpdateChecker() {
    }

    public static void start() {
        if (!BHSConfig.updateCheck) return;
        FabricLoader loader = FabricLoader.getInstance();
        String installed = loader.getModContainer("betterhotbarswap").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse(null);
        String mc = loader.getModContainer("minecraft").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse(null);
        if (installed == null || mc == null) return;
        String query = "loaders=" + URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8)
            + "&game_versions=" + URLEncoder.encode("[\"" + mc + "\"]", StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/project/" + SLUG + "/version?" + query))
            .header("User-Agent", "BEEandRandomNames/BetterHotbarSwap/" + installed + " (github.com/BEEandRandomNames/BetterHotbarSwap)")
            .timeout(Duration.ofSeconds(10))
            .build();
        // 404 = henüz yayında değil; ağ hatası da sessiz geçer.
        HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(response -> {
                if (response.statusCode() != 200) return;
                newVersion = newest(response.body(), installed);
                if (newVersion != null) BetterHotbarSwap.LOGGER.info("[BHS] update available: {}", newVersion);
            })
            .exceptionally(e -> null);
    }

    // Ana menü görününce (istek geç dönerse de) bir kez sorar.
    public static void onTick(Minecraft client) {
        if (shown || newVersion == null || !(client.screen instanceof TitleScreen)) return;
        shown = true;
        client.setScreen(new BHSUpdateScreen(client.screen, newVersion, pageUrl()));
    }

    // Yanıttaki en yüksek mod sürümü yüklüden büyükse onu döner, değilse null.
    static String newest(String json, String installed) {
        try {
            SemanticVersion best = SemanticVersion.parse(core(installed));
            String found = null;
            for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
                String v = core(e.getAsJsonObject().get("version_number").getAsString());
                try {
                    SemanticVersion candidate = SemanticVersion.parse(v);
                    if (candidate.compareTo(best) > 0) {
                        best = candidate;
                        found = v;
                    }
                } catch (VersionParsingException ignored) {
                }
            }
            return found;
        } catch (Exception e) {
            return null;
        }
    }

    // "v1.1.0+1.20.4" -> "1.1.0": sitedeki adın MC eki karşılaştırmaya girmez.
    static String core(String v) {
        v = v.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        int plus = v.indexOf('+');
        return plus >= 0 ? v.substring(0, plus) : v;
    }

    // Nereden indirildi (kullanıcı kararı: tek jar, başlatıcıdan tahmin): CurseForge
    // uygulaması oyun klasörüne minecraftinstance.json koyar; Prism siteden kurduğu
    // modu mods/.index/*.pw.toml'a yazar. İz yoksa Modrinth.
    static String pageUrl() {
        Path dir = FabricLoader.getInstance().getGameDir();
        boolean curseForge = Files.exists(dir.resolve("minecraftinstance.json"));
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir.resolve("mods").resolve(".index"), "*.pw.toml")) {
            for (Path p : files) {
                String t = Files.readString(p).toLowerCase(Locale.ROOT);
                if (t.contains(SLUG) && t.contains("[update.curseforge]")) curseForge = true;
            }
        } catch (Exception ignored) {
        }
        return curseForge ? "https://www.curseforge.com/minecraft/mc-mods/" + SLUG : "https://modrinth.com/mod/" + SLUG;
    }
}
