package com.betterhotbarswap.config;

import com.betterhotbarswap.client.toolbar.SurvivalToolbars;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// Kayıtlı araç çubukları: SADECE dolu olanlar listelenir (vanilla envanter stili
// görünümü + [Yükle] [Sil]). [Ekle] şu anki hotbar'ı ilk boş yuvaya kaydeder.
// Satır numarası = kayıt yuvası = dünyadaki "Yükle tuşu + o kısayol".
// Survival'da envanterde OLMAYAN eşyalar kırmızı tonla gösterilir.
public final class BHSToolbarScreen extends Screen {
    private static final net.minecraft.resources.Identifier HOTBAR = net.minecraft.resources.Identifier.withDefaultNamespace("hud/hotbar");
    private static final int HOTBAR_W = 182;
    private static final int ROW_H = 24;
    private static final int BTN_W = 50;
    private static final int ROW_W = 14 + HOTBAR_W + 4 + BTN_W * 2 + 2;
    // Düzenleyicide "Bitti" anı + yuva: o çubuğun solunda kısa süre "Kaydedildi".
    static long savedAtMs;
    static int savedIndex = -1;
    private static final long SAVED_SHOW_MS = 2500;

    private final Screen parent;
    private final List<Integer> shown = new ArrayList<>();
    private int left;
    private int top;
    // Kaydırma (çok kayıt ya da büyük arayüz ölçeği): görünen ilk satır + görünür satır sayısı.
    // Fare tekerleği veya sağdaki vanilla kaydırma çubuğu (sürüklenir/tıklanır).
    private int scroll;
    private int visible;
    private boolean dragging;
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("widget/scroller");
    private static final Identifier SCROLLER_BG = Identifier.withDefaultNamespace("widget/scroller_background");
    private static final int BAR_W = 6;

    public BHSToolbarScreen(Screen parent) {
        super(Component.translatable("betterhotbarswap.toolbar.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        shown.clear();
        for (int i = 0; i < 9; i++) if (!SurvivalToolbars.isEmpty(minecraft, i)) shown.add(i);
        left = width / 2 - ROW_W / 2;
        top = 32;
        visible = Math.max(1, (height - 48 - top) / ROW_H);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        for (int r = scroll; r < Math.min(shown.size(), scroll + visible); r++) {
            int idx = shown.get(r);
            int y = rowY(r);
            int bx = left + 14 + HOTBAR_W + 4;
            addRenderableWidget(Button.builder(Component.translatable("betterhotbarswap.toolbar.load"),
                b -> SurvivalToolbars.load(minecraft, idx)).bounds(bx, y + 1, BTN_W, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("betterhotbarswap.toolbar.delete"), b -> {
                SurvivalToolbars.delete(minecraft, idx);
                rebuildWidgets();
            }).bounds(bx + BTN_W + 2, y + 1, BTN_W, 20).build());
        }
        int free = SurvivalToolbars.firstFree(minecraft);
        Button add = addRenderableWidget(Button.builder(Component.translatable("betterhotbarswap.toolbar.add"), b -> {
            SurvivalToolbars.save(minecraft, free, com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarLoadKey());
            rebuildWidgets();
        }).bounds(width / 2 - 155, height - 28, 150, 20)
            .tooltip(Tooltip.create(Component.translatable(free == -1 ? "betterhotbarswap.toolbar.full" : "betterhotbarswap.toolbar.add.desc")))
            .build());
        add.active = free != -1;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .bounds(width / 2 + 5, height - 28, 150, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // 26.1+: render -> extractRenderState; çizim sırası katmanı belirler (z yok), renkler ARGB.
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        if (!shown.isEmpty()) {
            context.centeredText(font, Component.translatable("betterhotbarswap.toolbar.edit_hint"),
                width / 2, 19, 0xFFA0A0A0);
        }
        if (shown.isEmpty()) {
            context.centeredText(font, Component.translatable("betterhotbarswap.toolbar.none"),
                width / 2, height / 2 - 10, 0xFFA0A0A0);
        }
        boolean hoveredBar = false;
        Inventory inv = minecraft.player != null ? minecraft.player.getInventory() : null;
        for (int r = scroll; r < Math.min(shown.size(), scroll + visible); r++) {
            int idx = shown.get(r);
            int y = rowY(r);
            context.text(font, String.valueOf(idx + 1), left + 2, y + 7, 0xFFFFFFFF);
            int hx = left + 14;
            // Oyunun KENDİ hotbar görseli (oyundakiyle birebir, kaynak paketine uyar).
            // Adı/API'si sürümle değişir: 1.20.1 widgets.png, 1.20.2+ "hud/hotbar" sprite'ı
            // (javap: InGameHud aynısını çiziyor). Port prosedüründe her sürüm kontrol edilir.
            context.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, hx, y, HOTBAR_W, 22);
            boolean over = mouseX >= hx && mouseX < hx + HOTBAR_W && mouseY >= y && mouseY < y + 22;
            java.util.List<ItemStack> entry = SurvivalToolbars.storage(minecraft).get(idx);
            for (int s = 0; s < 9; s++) {
                ItemStack st = entry.get(s);
                if (st.isEmpty()) continue;
                int ix = hx + 3 + s * 20;
                int iy = y + 3;
                context.item(st, ix, iy);
                context.itemDecorations(font, st, ix, iy);
                if (inv != null && !has(inv, st)) {
                    context.fill(ix, iy, ix + 16, iy + 16, 0x66FF0000);
                }
            }
            // Tıklanabilir hissi: üstüne gelince tüm çubuk beyazlaşır.
            if (over) {
                context.fill(hx, y, hx + HOTBAR_W, y + 22, 0x60FFFFFF);
                hoveredBar = true;
            }
        }
        if (maxScroll() > 0) {
            int h = visible * ROW_H;
            int th = Math.max(16, h * visible / shown.size());
            context.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BG, barX(), top, BAR_W, h);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER, barX(), top + (h - th) * scroll / maxScroll(), BAR_W, th);
        }
        Component hint = Component.translatable("betterhotbarswap.toolbar.hint",
            com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarSaveKey().getTranslatedKeyMessage(),
            com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarLoadKey().getTranslatedKeyMessage());
        context.centeredText(font, hint, width / 2, height - 42, 0xFFA0A0A0);
        long since = System.currentTimeMillis() - savedAtMs;
        int savedRow = shown.indexOf(savedIndex);
        if (since < SAVED_SHOW_MS && savedRow >= scroll && savedRow < scroll + visible) {
            // Son 500 ms'de solar (alfa 4'ün altında font hiç çizmez).
            int alpha = (int) (255 * Math.min(1.0, (SAVED_SHOW_MS - since) / 500.0));
            Component saved = Component.translatable("betterhotbarswap.toolbar.saved");
            int x = Math.max(2, left - 4 - font.width(saved));
            if (alpha > 4) context.text(font, saved, x, rowY(savedRow) + 7, (alpha << 24) | 0x55FF55);
        }
        // Vanilla'nın "Düzenle" çevirisi: her dilde hazır.
        if (hoveredBar) context.setTooltipForNextFrame(font, Component.translatable("selectWorld.edit"), mouseX, mouseY);
    }

    private static boolean has(Inventory inv, ItemStack want) {
        for (int i = 0; i < 36; i++) if (inv.getItem(i).getItem() == want.getItem()) return true;
        return false;
    }

    // Çubuğun üzerine tıklayınca düzenleyici açılır.
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        if (button == 0 && maxScroll() > 0 && mx >= barX() && mx < barX() + BAR_W && my >= top && my < top + visible * ROW_H) {
            dragging = true;
            scrollTo(my);
            return true;
        }
        for (int r = scroll; r < Math.min(shown.size(), scroll + visible); r++) {
            int y = rowY(r);
            if (button == 0 && mx >= left + 14 && mx < left + 14 + HOTBAR_W && my >= y && my < y + 22) {
                minecraft.gui.setScreen(new BHSToolbarEditScreen(this, shown.get(r)));
                return true;
            }
        }
        return false;
    }

    private int rowY(int r) {
        return top + (r - scroll) * ROW_H;
    }

    private int maxScroll() {
        return Math.max(0, shown.size() - visible);
    }

    private int barX() {
        return left + ROW_W + 4;
    }

    private void setScroll(int s) {
        s = Math.max(0, Math.min(s, maxScroll()));
        if (s == scroll) return;
        scroll = s;
        rebuildWidgets();
    }

    // Çubukta tutulan yükseklik oranı = kaydırma oranı.
    private void scrollTo(double my) {
        setScroll((int) Math.round((my - top) / (visible * ROW_H) * maxScroll()));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy == 0 || maxScroll() == 0) return super.mouseScrolled(mx, my, sx, sy);
        setScroll(scroll - (int) Math.signum(sy));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging) return super.mouseDragged(event, dx, dy);
        scrollTo(event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    // Açan tuşa tekrar basınca kapanır (vanilla envanter tuşu gibi).
    @Override
    public boolean keyPressed(KeyEvent event) {
        var key = com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarsKey();
        if (key != null && key.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
