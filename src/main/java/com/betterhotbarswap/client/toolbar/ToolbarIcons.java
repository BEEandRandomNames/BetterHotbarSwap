package com.betterhotbarswap.client.toolbar;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.random.Random;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Yabancı araç çubuğu eşyasının görünümü (kullanıcı 2026-10-23: "o anki eşyanın görünümü ve
// verisi kaydediliyor gibi"): kaydederken eşyanın dokusu (parçacık sprite'ının PNG'si; kaynak
// paketi ve modlar dahil) ve atlastaki yeri slota yazılır, okunamayan slotta bu resim çizilir.
// Bloklar tek yüzün düz resmi (kullanıcı kararı). Büyülüyse vanilla parlaması BİREBİR her karede
// hesaplanır: setupGlintTexturing (zaman x parlama hızı x 8, kaydırma 110000/30000, 10° dönme,
// arayüz ölçeği 8, atlas UV'si üzerinde) + rendertype_glint.fsh (renk x parlama gücü) + GLINT
// harmanlama (SRC_COLOR, ONE: rengin karesi eklenir); parlama dokusu doğrusal örneklenir.
public final class ToolbarIcons {
    private static final float ROT = 0.17453292F;
    private static final float COS = (float) Math.cos(ROT);
    private static final float SIN = (float) Math.sin(ROT);
    // Atlas yeri kaydedilmemiş eski kayıtlar: 1024'lük atlasta 16 piksellik eşya varsayılır.
    private static final float[] DEFAULT_UV = {0, 0, 16 / 1024f, 16 / 1024f};
    private static final Map<Item, Capture> CAPTURED = new HashMap<>();
    private static final Map<String, Icon> ICONS = new HashMap<>();
    private static NativeImage glintTexture;

    // Kaydedilen görünüm: PNG + atlastaki yer (u0, v0, genişlik, yükseklik; 0-1).
    public record Capture(byte[] png, float[] uv) {
    }

    private static final class Icon {
        Identifier id;
        int size;
        NativeImageBackedTexture texture;
        NativeImage base; // sadece parlamalıda: her karede bunun üstüne hesaplanır
        int itemSize;
        float[] uv;
        float strength;
        float speed;
        long drawnAt = -1;
    }

    private ToolbarIcons() {
    }

    // Eşyanın dokusu ve atlas yeri; bulunamazsa null (yabancıyken bariyer çizilir).
    // shortcut: eşya başına önbellek; NBT'ye göre değişen doku (özel model) ilk görüleni alır.
    static Capture capture(ItemStack s) {
        if (CAPTURED.containsKey(s.getItem())) return CAPTURED.get(s.getItem());
        Capture cap = null;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            // 1.21.4+: eşya modeli ItemRenderState üzerinden (parçacık sprite'ı).
            ItemRenderState state = new ItemRenderState();
            mc.getItemModelManager().update(state, s, ItemDisplayContext.GUI, mc.world, null, 0);
            Sprite sp = state.getParticleSprite(Random.create());
            Identifier id = sp.getContents().getId();
            try (InputStream in = mc.getResourceManager().open(Identifier.of(id.getNamespace(), "textures/" + id.getPath() + ".png"))) {
                cap = new Capture(in.readAllBytes(), new float[]{sp.getMinU(), sp.getMinV(), sp.getMaxU() - sp.getMinU(), sp.getMaxV() - sp.getMinV()});
            }
        } catch (Exception ignored) {
        }
        CAPTURED.put(s.getItem(), cap);
        return cap;
    }

    // Yabancı slotu çizer ve true döner; gerçek eşyada false (çağıran vanilla çizer).
    public static boolean draw(DrawContext g, TextRenderer font, ItemStack st, int x, int y) {
        ToolbarStorage.Foreign f = ToolbarStorage.foreign(st);
        if (f == null) return false;
        Icon icon = icon(f);
        if (icon == null) {
            g.drawItem(st, x, y);
        } else {
            if (icon.base != null) animate(icon);
            g.drawTexture(RenderPipelines.GUI_TEXTURED, icon.id, x, y, 0, 0, 16, 16, icon.size, icon.size, icon.size, icon.size);
        }
        if (f.count() > 1) {
            String c = String.valueOf(f.count());
            g.drawText(font, c, x + 17 - font.getWidth(c), y + 9, 0xFFFFFFFF, true);
        }
        return true;
    }

    // Yabancı slotun ipucu: kayıttaki eşya adı + nerede kaydedildiği; gerçek eşyada null.
    public static List<Text> tooltip(ItemStack st) {
        ToolbarStorage.Foreign f = ToolbarStorage.foreign(st);
        if (f == null) return null;
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal(f.name())); // kayıttaki veri, arayüz metni değil
        lines.add((f.mc().isEmpty() ? Text.translatable("betterhotbarswap.toolbar.foreign_any")
            : Text.translatable("betterhotbarswap.toolbar.foreign", f.mc())).formatted(Formatting.GRAY));
        return lines;
    }

    private static Icon icon(ToolbarStorage.Foreign f) {
        if (f.icon() == null) return null;
        float[] uv = f.uv() == null ? DEFAULT_UV : f.uv();
        String key = "foreign/" + Integer.toHexString(Arrays.hashCode(f.icon())) + (f.glint() ? "g" + Integer.toHexString(Arrays.hashCode(uv)) : "");
        Icon icon = ICONS.computeIfAbsent(key, k -> build(k, f, uv));
        return icon.id == null ? null : icon;
    }

    private static Icon build(String key, ToolbarStorage.Foreign f, float[] uv) {
        Icon icon = new Icon();
        MinecraftClient mc = MinecraftClient.getInstance();
        try (NativeImage src = NativeImage.read(f.icon())) {
            int w = src.getWidth(); // animasyonlu doku dikey şerittir: ilk kare w x w
            NativeImage first = new NativeImage(w, w, true);
            for (int y = 0; y < w; y++) for (int x = 0; x < w; x++) first.setColorArgb(x, y, src.getColorArgb(x, y));
            if (f.glint()) {
                if (glintTexture == null) {
                    try (InputStream in = mc.getResourceManager().open(Identifier.of("minecraft", "textures/misc/enchanted_glint_item.png"))) {
                        glintTexture = NativeImage.read(in);
                    }
                }
                // Doku ekran çözünürlüğünde animate() içinde oluşturulur (arayüz ölçeğine göre).
                icon.base = first;
                icon.itemSize = w;
                icon.uv = uv;
                icon.strength = f.glintStrength();
                icon.speed = f.glintSpeed();
                icon.id = Identifier.of("betterhotbarswap", key);
            } else {
                icon.texture = new NativeImageBackedTexture(() -> "betterhotbarswap foreign icon", first);
                icon.id = Identifier.of("betterhotbarswap", key);
                icon.size = w;
                mc.getTextureManager().registerTexture(icon.id, icon.texture);
            }
        } catch (Exception e) {
            icon.id = null;
        }
        return icon;
    }

    // Vanilla parlamasının bu karesi: atlas UV'si -> x8 -> 10° -> kaydırma; dokudan doğrusal örnek,
    // (renk x güç)^2 eklenir. Vanilla her EKRAN pikselinde örnekler: doku 16 x arayüz ölçeği
    // boyutunda hesaplanır (eşya pikselinden ince parlama ayrıntısı kaybolmasın). Güç/hız
    // kaydedildiği oyunun ayarı (yoksa bu oyununki). Aynı karede ikinci çizimde hesaplanmaz.
    private static void animate(Icon icon) {
        long now = Util.getMeasuringTimeMs();
        if (icon.drawnAt == now) return;
        icon.drawnAt = now;
        MinecraftClient mc = MinecraftClient.getInstance();
        int n = Math.max(icon.itemSize, (int) Math.round(16 * mc.getWindow().getScaleFactor()));
        if (icon.texture == null || icon.size != n) {
            icon.texture = new NativeImageBackedTexture(() -> "betterhotbarswap foreign icon", new NativeImage(n, n, true));
            icon.size = n;
            mc.getTextureManager().registerTexture(icon.id, icon.texture);
        }
        double speed = Float.isNaN(icon.speed) ? mc.options.getGlintSpeed().getValue() : icon.speed;
        float strength = Float.isNaN(icon.strength) ? mc.options.getGlintStrength().getValue().floatValue() : icon.strength;
        long l = (long) (now * speed * 8.0);
        float fu = (float) (l % 110000L) / 110000.0F;
        float gv = (float) (l % 30000L) / 30000.0F;
        NativeImage out = icon.texture.getImage();
        int w = icon.itemSize;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int c = icon.base.getColorArgb(x * w / n, y * w / n);
                if ((c >>> 24) != 0) {
                    float u = 8 * (icon.uv[0] + (x + 0.5f) / n * icon.uv[2]);
                    float v = 8 * (icon.uv[1] + (y + 0.5f) / n * icon.uv[3]);
                    c = glint(c, sample(glintTexture, u * COS - v * SIN - fu, u * SIN + v * COS + gv), strength);
                }
                out.setColorArgb(x, y, c);
            }
        }
        icon.texture.upload();
    }

    // Kaydederken bu oyunun parlama ayarları (Erişilebilirlik: güç, hız).
    static float glintStrength() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.options.getGlintStrength().getValue().floatValue();
    }

    static float glintSpeed() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return (float) (double) mc.options.getGlintSpeed().getValue();
    }

    // Doğrusal (blur) ve tekrarlı örnek; kanal sırası görüntününkiyle aynı kalır.
    static int sample(NativeImage t, float u, float v) {
        int tw = t.getWidth();
        int th = t.getHeight();
        float x = u * tw - 0.5f;
        float y = v * th - 0.5f;
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        int a = t.getColorArgb(Math.floorMod(x0, tw), Math.floorMod(y0, th));
        int b = t.getColorArgb(Math.floorMod(x0 + 1, tw), Math.floorMod(y0, th));
        int c = t.getColorArgb(Math.floorMod(x0, tw), Math.floorMod(y0 + 1, th));
        int d = t.getColorArgb(Math.floorMod(x0 + 1, tw), Math.floorMod(y0 + 1, th));
        int out = 0;
        for (int sh = 0; sh < 32; sh += 8) {
            float top = ((a >>> sh) & 255) * (1 - fx) + ((b >>> sh) & 255) * fx;
            float bottom = ((c >>> sh) & 255) * (1 - fx) + ((d >>> sh) & 255) * fx;
            out |= Math.round(top * (1 - fy) + bottom * fy) << sh;
        }
        return out;
    }

    // rendertype_glint.fsh + GLINT harmanlama: doku alfası < 0.1 atılır, kalan (renk x güç)^2 eklenir.
    static int glint(int c, int g, float strength) {
        if ((g >>> 24) < 26) return c;
        int out = c & 0xFF000000;
        for (int sh = 0; sh < 24; sh += 8) {
            float gc = ((g >>> sh) & 255) / 255f * strength;
            out |= Math.min(255, ((c >>> sh) & 255) + Math.round(gc * gc * 255)) << sh;
        }
        return out;
    }
}
