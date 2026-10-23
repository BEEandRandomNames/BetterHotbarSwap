package com.betterhotbarswap.client.toolbar;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

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
// arayüz ölçeği 8, atlas UV'si üzerinde) + glint.fsh (renk x parlama gücü) + GLINT harmanlama
// (SRC_COLOR, ONE: rengin karesi eklenir); parlama dokusu doğrusal örneklenir (mcmeta blur).
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
        DynamicTexture texture;
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
    // shortcut: eşya başına önbellek; bileşene göre değişen doku (özel model) ilk görüleni alır.
    static Capture capture(ItemStack s) {
        if (CAPTURED.containsKey(s.getItem())) return CAPTURED.get(s.getItem());
        Capture cap = null;
        try {
            Minecraft mc = Minecraft.getInstance();
            ItemStackRenderState state = new ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(state, s, ItemDisplayContext.GUI, mc.level, null, 0);
            Material.Baked m = state.pickParticleMaterial(RandomSource.create());
            if (m != null) {
                TextureAtlasSprite sp = m.sprite();
                Identifier id = sp.contents().name();
                try (InputStream in = mc.getResourceManager().open(Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png"))) {
                    cap = new Capture(in.readAllBytes(), new float[]{sp.getU0(), sp.getV0(), sp.getU1() - sp.getU0(), sp.getV1() - sp.getV0()});
                }
            }
        } catch (Exception ignored) {
        }
        CAPTURED.put(s.getItem(), cap);
        return cap;
    }

    // Yabancı slotu çizer ve true döner; gerçek eşyada false (çağıran vanilla çizer).
    public static boolean draw(GuiGraphicsExtractor g, Font font, ItemStack st, int x, int y) {
        ToolbarStorage.Foreign f = ToolbarStorage.foreign(st);
        if (f == null) return false;
        Icon icon = icon(f);
        if (icon == null) {
            g.item(st, x, y);
        } else {
            if (icon.base != null) animate(icon);
            g.blit(RenderPipelines.GUI_TEXTURED, icon.id, x, y, 0, 0, 16, 16, icon.size, icon.size, icon.size, icon.size);
        }
        if (f.count() > 1) {
            String c = String.valueOf(f.count());
            g.text(font, c, x + 17 - font.width(c), y + 9, 0xFFFFFFFF, true);
        }
        return true;
    }

    // Yabancı slotun ipucu: kayıttaki eşya adı + nerede kaydedildiği; gerçek eşyada null.
    public static List<Component> tooltip(ItemStack st) {
        ToolbarStorage.Foreign f = ToolbarStorage.foreign(st);
        if (f == null) return null;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(f.name())); // kayıttaki veri, arayüz metni değil
        lines.add((f.mc().isEmpty() ? Component.translatable("betterhotbarswap.toolbar.foreign_any")
            : Component.translatable("betterhotbarswap.toolbar.foreign", f.mc())).withStyle(ChatFormatting.GRAY));
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
        Minecraft mc = Minecraft.getInstance();
        try (NativeImage src = NativeImage.read(f.icon())) {
            int w = src.getWidth(); // animasyonlu doku dikey şerittir: ilk kare w x w
            NativeImage first = new NativeImage(w, w, true);
            for (int y = 0; y < w; y++) for (int x = 0; x < w; x++) first.setPixel(x, y, src.getPixel(x, y));
            if (f.glint()) {
                if (glintTexture == null) {
                    try (InputStream in = mc.getResourceManager().open(Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png"))) {
                        glintTexture = NativeImage.read(in);
                    }
                }
                // Doku ekran çözünürlüğünde animate() içinde oluşturulur (arayüz ölçeğine göre).
                icon.base = first;
                icon.itemSize = w;
                icon.uv = uv;
                icon.strength = f.glintStrength();
                icon.speed = f.glintSpeed();
                icon.id = Identifier.fromNamespaceAndPath("betterhotbarswap", key);
            } else {
                icon.texture = new DynamicTexture(() -> "betterhotbarswap foreign icon", first);
                icon.id = Identifier.fromNamespaceAndPath("betterhotbarswap", key);
                icon.size = w;
                mc.getTextureManager().register(icon.id, icon.texture);
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
        long now = Util.getMillis();
        if (icon.drawnAt == now) return;
        icon.drawnAt = now;
        Minecraft mc = Minecraft.getInstance();
        int n = Math.max(icon.itemSize, (int) Math.round(16 * mc.getWindow().getGuiScale()));
        if (icon.texture == null || icon.size != n) {
            icon.texture = new DynamicTexture(() -> "betterhotbarswap foreign icon", new NativeImage(n, n, true));
            icon.size = n;
            mc.getTextureManager().register(icon.id, icon.texture);
        }
        double speed = Float.isNaN(icon.speed) ? mc.options.glintSpeed().get() : icon.speed;
        float strength = Float.isNaN(icon.strength) ? mc.options.glintStrength().get().floatValue() : icon.strength;
        long l = (long) (now * speed * 8.0);
        float fu = (float) (l % 110000L) / 110000.0F;
        float gv = (float) (l % 30000L) / 30000.0F;
        NativeImage out = icon.texture.getPixels();
        int w = icon.itemSize;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int c = icon.base.getPixel(x * w / n, y * w / n);
                if ((c >>> 24) != 0) {
                    float u = 8 * (icon.uv[0] + (x + 0.5f) / n * icon.uv[2]);
                    float v = 8 * (icon.uv[1] + (y + 0.5f) / n * icon.uv[3]);
                    c = glint(c, sample(glintTexture, u * COS - v * SIN - fu, u * SIN + v * COS + gv), strength);
                }
                out.setPixel(x, y, c);
            }
        }
        icon.texture.upload();
    }

    // Kaydederken bu oyunun parlama ayarları (Erişilebilirlik: güç, hız).
    static float glintStrength() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options.glintStrength().get().floatValue();
    }

    static float glintSpeed() {
        Minecraft mc = Minecraft.getInstance();
        return (float) (double) mc.options.glintSpeed().get();
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
        int a = t.getPixel(Math.floorMod(x0, tw), Math.floorMod(y0, th));
        int b = t.getPixel(Math.floorMod(x0 + 1, tw), Math.floorMod(y0, th));
        int c = t.getPixel(Math.floorMod(x0, tw), Math.floorMod(y0 + 1, th));
        int d = t.getPixel(Math.floorMod(x0 + 1, tw), Math.floorMod(y0 + 1, th));
        int out = 0;
        for (int sh = 0; sh < 32; sh += 8) {
            float top = ((a >>> sh) & 255) * (1 - fx) + ((b >>> sh) & 255) * fx;
            float bottom = ((c >>> sh) & 255) * (1 - fx) + ((d >>> sh) & 255) * fx;
            out |= Math.round(top * (1 - fy) + bottom * fy) << sh;
        }
        return out;
    }

    // glint.fsh + GLINT harmanlama: doku alfası < 0.1 atılır, kalan (renk x güç)^2 eklenir.
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
