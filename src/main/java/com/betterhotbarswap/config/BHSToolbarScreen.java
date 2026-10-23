package com.betterhotbarswap.config;

import com.betterhotbarswap.client.toolbar.SurvivalToolbars;
import com.betterhotbarswap.client.toolbar.ToolbarIcons;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

// Kayıtlı araç çubukları: SADECE dolu olanlar listelenir (vanilla envanter stili
// görünümü + [Yükle] [Sil]). [Ekle] şu anki hotbar'ı ilk boş yuvaya kaydeder.
// Satır numarası = kayıt yuvası = dünyadaki "Yükle tuşu + o kısayol".
// Survival'da envanterde OLMAYAN eşyalar kırmızı tonla gösterilir.
public final class BHSToolbarScreen extends Screen {
    private static final net.minecraft.util.Identifier HOTBAR = net.minecraft.util.Identifier.ofVanilla("hud/hotbar");
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
    // Fare tekerleği veya sağdaki kaydırma çubuğu (sürüklenir/tıklanır) (F11).
    private int scroll;
    private int visible;
    private boolean dragging;
    private static final net.minecraft.util.Identifier SCROLLER = net.minecraft.util.Identifier.ofVanilla("widget/scroller");
    private static final net.minecraft.util.Identifier SCROLLER_BG = net.minecraft.util.Identifier.ofVanilla("widget/scroller_background");
    private static final int BAR_W = 6;

    public BHSToolbarScreen(Screen parent) {
        super(Text.translatable("betterhotbarswap.toolbar.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        shown.clear();
        for (int i = 0; i < 9; i++) if (!SurvivalToolbars.isEmpty(client, i)) shown.add(i);
        left = width / 2 - ROW_W / 2;
        top = 32;
        visible = Math.max(1, (height - 48 - top) / ROW_H);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        for (int r = scroll; r < Math.min(shown.size(), scroll + visible); r++) {
            int idx = shown.get(r);
            int y = rowY(r);
            int bx = left + 14 + HOTBAR_W + 4;
            addDrawableChild(ButtonWidget.builder(Text.translatable("betterhotbarswap.toolbar.load"),
                b -> SurvivalToolbars.load(client, idx)).dimensions(bx, y + 1, BTN_W, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("betterhotbarswap.toolbar.delete"), b -> {
                SurvivalToolbars.delete(client, idx);
                clearAndInit();
            }).dimensions(bx + BTN_W + 2, y + 1, BTN_W, 20).build());
        }
        int free = SurvivalToolbars.firstFree(client);
        ButtonWidget add = addDrawableChild(ButtonWidget.builder(Text.translatable("betterhotbarswap.toolbar.add"), b -> {
            SurvivalToolbars.save(client, free, com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarLoadKey());
            clearAndInit();
        }).dimensions(width / 2 - 155, height - 28, 150, 20)
            .tooltip(Tooltip.of(Text.translatable(free == -1 ? "betterhotbarswap.toolbar.full" : "betterhotbarswap.toolbar.add.desc")))
            .build());
        add.active = free != -1;
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
            .dimensions(width / 2 + 5, height - 28, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta); // 1.20.2+: Screen.render arka plan + düğmeleri çizer; içerik üstüne
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
        if (!shown.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("betterhotbarswap.toolbar.edit_hint"),
                width / 2, 19, 0xA0A0A0);
        }
        if (shown.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("betterhotbarswap.toolbar.none"),
                width / 2, height / 2 - 10, 0xA0A0A0);
        }
        boolean hoveredBar = false;
        PlayerInventory inv = client.player != null ? client.player.getInventory() : null;
        for (int r = scroll; r < Math.min(shown.size(), scroll + visible); r++) {
            int idx = shown.get(r);
            int y = rowY(r);
            context.drawTextWithShadow(textRenderer, String.valueOf(idx + 1), left + 2, y + 7, 0xFFFFFF);
            int hx = left + 14;
            // Oyunun KENDİ hotbar görseli (oyundakiyle birebir, kaynak paketine uyar).
            // Adı/API'si sürümle değişir: 1.20.1 widgets.png, 1.20.2+ "hud/hotbar" sprite'ı
            // (javap: InGameHud aynısını çiziyor). Port prosedüründe her sürüm kontrol edilir.
            context.drawGuiTexture(HOTBAR, hx, y, HOTBAR_W, 22);
            boolean over = mouseX >= hx && mouseX < hx + HOTBAR_W && mouseY >= y && mouseY < y + 22;
            java.util.List<ItemStack> entry = SurvivalToolbars.storage(client).get(idx);
            for (int s = 0; s < 9; s++) {
                ItemStack st = entry.get(s);
                if (st.isEmpty()) continue;
                int ix = hx + 3 + s * 20;
                int iy = y + 3;
                if (!ToolbarIcons.draw(context, textRenderer, st, ix, iy)) {
                    context.drawItem(st, ix, iy);
                    context.drawItemInSlot(textRenderer, st, ix, iy);
                }
                if (inv != null && !has(inv, st)) {
                    context.fill(ix, iy, ix + 16, iy + 16, 300, 0x66FF0000);
                }
            }
            // Tıklanabilir hissi: üstüne gelince tüm çubuk beyazlaşır.
            if (over) {
                context.fill(hx, y, hx + HOTBAR_W, y + 22, 310, 0x60FFFFFF);
                hoveredBar = true;
            }
        }
        if (maxScroll() > 0) {
            int h = visible * ROW_H;
            int th = Math.max(16, h * visible / shown.size());
            context.drawGuiTexture(SCROLLER_BG, barX(), top, BAR_W, h);
            context.drawGuiTexture(SCROLLER, barX(), top + (h - th) * scroll / maxScroll(), BAR_W, th);
        }
        Text hint = Text.translatable("betterhotbarswap.toolbar.hint",
            com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarSaveKey().getBoundKeyLocalizedText(),
            com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarLoadKey().getBoundKeyLocalizedText());
        context.drawCenteredTextWithShadow(textRenderer, hint, width / 2, height - 42, 0xA0A0A0);
        long since = System.currentTimeMillis() - savedAtMs;
        int savedRow = shown.indexOf(savedIndex);
        if (since < SAVED_SHOW_MS && savedRow >= scroll && savedRow < scroll + visible) {
            // Son 500 ms'de solar (alfa 4'ün altında font hiç çizmez).
            int alpha = (int) (255 * Math.min(1.0, (SAVED_SHOW_MS - since) / 500.0));
            Text saved = Text.translatable("betterhotbarswap.toolbar.saved");
            int x = Math.max(2, left - 4 - textRenderer.getWidth(saved));
            if (alpha > 4) context.drawTextWithShadow(textRenderer, saved, x, rowY(savedRow) + 7, (alpha << 24) | 0x55FF55);
        }
        // Vanilla'nın "Düzenle" çevirisi: her dilde hazır.
        if (hoveredBar) context.drawTooltip(textRenderer, Text.translatable("selectWorld.edit"), mouseX, mouseY);
    }

    private static boolean has(PlayerInventory inv, ItemStack want) {
        for (int i = 0; i < 36; i++) if (inv.getStack(i).getItem() == want.getItem()) return true;
        return false;
    }

    // Çubuğun üzerine tıklayınca düzenleyici açılır.
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && maxScroll() > 0 && mx >= barX() && mx < barX() + BAR_W && my >= top && my < top + visible * ROW_H) {
            dragging = true;
            scrollTo(my);
            return true;
        }
        for (int r = scroll; r < Math.min(shown.size(), scroll + visible); r++) {
            int y = rowY(r);
            if (button == 0 && mx >= left + 14 && mx < left + 14 + HOTBAR_W && my >= y && my < y + 22) {
                client.setScreen(new BHSToolbarEditScreen(this, shown.get(r)));
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
        clearAndInit();
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
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) return super.mouseDragged(mx, my, button, dx, dy);
        scrollTo(my);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    // Açan tuşa tekrar basınca kapanır (vanilla envanter tuşu gibi).
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        var key = com.betterhotbarswap.client.BetterHotbarSwapClient.getToolbarsKey();
        if (key != null && key.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
