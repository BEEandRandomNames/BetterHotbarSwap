package com.betterhotbarswap.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.option.ControlsListWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

// Sade vanilla Seçenekler düzeni: iki sütun "Ad: AÇIK/KAPALI" düğmeleri,
// açıklama fare üstündeyken ipucu balonunda. Altta araç çubukları + tuşlar.
public final class BHSConfigScreen extends Screen {
    private final Screen parent;

    public BHSConfigScreen(Screen parent) {
        super(Text.translatable("betterhotbarswap.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = height / 6;
        toggle(0, y, "hotbar_priority", BHSConfig.hotbarPriority, v -> BHSConfig.hotbarPriority = v);
        toggle(1, y, "shift_hotbar_pickup", BHSConfig.shiftHotbarPickup, v -> BHSConfig.shiftHotbarPickup = v);
        toggle(0, y + 24, "tidy_collect", BHSConfig.tidyCollect, v -> BHSConfig.tidyCollect = v);
        toggle(1, y + 24, "survival_toolbars", BHSConfig.survivalToolbars, v -> BHSConfig.survivalToolbars = v);
        toggle(0, y + 48, "debug", BHSConfig.debugEnabled, v -> BHSConfig.debugEnabled = v);
        addDrawableChild(ButtonWidget.builder(Text.translatable("betterhotbarswap.settings.toolbars"),
            b -> client.setScreen(new BHSToolbarScreen(this))).dimensions(colX(0), y + 84, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("betterhotbarswap.settings.keys"),
            b -> client.setScreen(keybindsAtOurCategory())).dimensions(colX(1), y + 84, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
            .dimensions(width / 2 - 100, y + 120, 200, 20).build());
    }

    // Vanilla Kontroller ekranı, BetterHotbarSwap bölümüne kaydırılmış açılır.
    // Liste = allKeys'in sıralı kopyası, her kategori değişiminde 1 başlık satırı,
    // satır yüksekliği 20 (javap: ControlsListWidget). Aynı sıra burada sayılır.
    // Başka bir mod listeyi değiştirdiyse (ör. Controlling) kaydırma sessizce atlanır.
    private Screen keybindsAtOurCategory() {
        return new KeybindsScreen(this, client.options) {
            @Override
            protected void init() {
                super.init();
                KeyBinding[] keys = client.options.allKeys.clone();
                java.util.Arrays.sort(keys);
                int row = 0;
                String last = null;
                for (KeyBinding k : keys) {
                    if (!k.getCategory().equals(last)) {
                        last = k.getCategory();
                        if (last.equals("category.betterhotbarswap")) break;
                        row++;
                    }
                    row++;
                }
                for (Element e : children()) {
                    if (e instanceof ControlsListWidget list) list.setScrollAmount(row * 20);
                }
            }
        };
    }

    private int colX(int col) {
        return width / 2 - 155 + col * 160;
    }

    private void toggle(int col, int y, String key, boolean initial, Toggle setter) {
        Text name = Text.translatable("betterhotbarswap.settings." + key);
        boolean[] state = {initial};
        addDrawableChild(ButtonWidget.builder(toggleText(name, initial), b -> {
            state[0] = !state[0];
            setter.set(state[0]);
            b.setMessage(toggleText(name, state[0]));
            BHSConfig.save();
        }).dimensions(colX(col), y, 150, 20)
            .tooltip(Tooltip.of(Text.translatable("betterhotbarswap.settings." + key + ".desc")))
            .build());
    }

    // Vanilla "Ad: AÇIK/KAPALI" biçimi (oyun dilinde), durum kısmı renkli.
    private static Text toggleText(Text name, boolean on) {
        return ScreenTexts.composeGenericOptionText(name,
            (on ? ScreenTexts.ON : ScreenTexts.OFF).copy().formatted(on ? Formatting.GREEN : Formatting.RED));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 15, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        BHSConfig.save();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @FunctionalInterface
    private interface Toggle {
        void set(boolean value);
    }
}
