package com.betterhotbarswap.client.toolbar;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Araç çubukları sürümden ve başlatıcı örneğinden bağımsız TEK ORTAK dosyada (kullanıcı 2026-10-23:
// "sürüm değişince kaybolmasın"): Windows %APPDATA%\BetterHotbarSwap\toolbars.nbt, diğer OS
// ~/.betterhotbarswap/toolbars.nbt. Klasör oluşturulamazsa örneğin config klasörüne düşer (kayıt
// kaybolmasın). Minecraft sınıfı kullanmaz: bütün sürümlerde birebir aynı dosya.
final class ToolbarFiles {
    static final Path FILE = pick();

    private ToolbarFiles() {
    }

    private static Path pick() {
        String appData = System.getenv("APPDATA");
        Path dir = appData != null ? Path.of(appData, "BetterHotbarSwap") : Path.of(System.getProperty("user.home"), ".betterhotbarswap");
        try {
            Files.createDirectories(dir);
            return dir.resolve("toolbars.nbt");
        } catch (Exception e) {
            return FabricLoader.getInstance().getConfigDir().resolve("betterhotbarswap_toolbars_shared.nbt");
        }
    }

    // 2026-10-23 deneme jar'larının sürüm başına dosyaları (toolbars-<DataVersion>.nbt, vanilla hotbar
    // biçimi): bir kez içe alınır ki o sırada kaydedilenler kaybolmasın.
    static List<Path> testFiles() {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(FILE.getParent(), "toolbars-*.nbt")) {
            for (Path p : files) out.add(p);
        } catch (Exception ignored) {
        }
        return out;
    }

    static long mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return 0;
        }
    }
}
