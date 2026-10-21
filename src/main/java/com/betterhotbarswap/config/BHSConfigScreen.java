package com.betterhotbarswap.config;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

// Sade vanilla Seçenekler düzeni: iki sütun "Ad: AÇIK/KAPALI" düğmeleri,
// açıklama fare üstündeyken ipucu balonunda. Altta araç çubukları + tuşlar.
public final class BHSConfigScreen extends Screen {
    private final Screen parent;

    public BHSConfigScreen(Screen parent) {
        super(Component.translatable("betterhotbarswap.settings.title"));
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
        addRenderableWidget(Button.builder(Component.translatable("betterhotbarswap.settings.toolbars"),
            b -> minecraft.setScreen(new BHSToolbarScreen(this))).bounds(colX(0), y + 84, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("betterhotbarswap.settings.keys"),
            b -> minecraft.setScreen(keybindsAtOurCategory())).bounds(colX(1), y + 84, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .bounds(width / 2 - 100, y + 120, 200, 20).build());
    }

    // Vanilla Kontroller ekranı, BetterHotbarSwap bölümüne kaydırılmış açılır.
    // Liste = allKeys'in sıralı kopyası, her kategori değişiminde 1 başlık satırı,
    // satır yüksekliği 20 (javap: ControlsListWidget). Aynı sıra burada sayılır.
    // Başka bir mod listeyi değiştirdiyse (ör. Controlling) kaydırma sessizce atlanır.
    private Screen keybindsAtOurCategory() {
        return new KeyBindsScreen(this, minecraft.options) {
            @Override
            protected void init() {
                super.init();
                KeyMapping[] keys = minecraft.options.keyMappings.clone();
                java.util.Arrays.sort(keys);
                int row = 0;
                String last = null;
                for (KeyMapping k : keys) {
                    if (!k.getCategory().equals(last)) {
                        last = k.getCategory();
                        if (last.equals("category.betterhotbarswap")) break;
                        row++;
                    }
                    row++;
                }
                for (GuiEventListener e : children()) {
                    if (e instanceof KeyBindsList list) list.setScrollAmount(row * 20);
                }
            }
        };
    }

    private int colX(int col) {
        return width / 2 - 155 + col * 160;
    }

    private void toggle(int col, int y, String key, boolean initial, Toggle setter) {
        Component name = Component.translatable("betterhotbarswap.settings." + key);
        boolean[] state = {initial};
        addRenderableWidget(Button.builder(toggleText(name, initial), b -> {
            state[0] = !state[0];
            setter.set(state[0]);
            b.setMessage(toggleText(name, state[0]));
            BHSConfig.save();
        }).bounds(colX(col), y, 150, 20)
            .tooltip(Tooltip.create(Component.translatable("betterhotbarswap.settings." + key + ".desc")))
            .build());
    }

    // Vanilla "Ad: AÇIK/KAPALI" biçimi (oyun dilinde), durum kısmı renkli.
    private static Component toggleText(Component name, boolean on) {
        return CommonComponents.optionNameValue(name,
            (on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF).copy().withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta); // 1.20.2+: Screen.render arka plan + düğmeleri çizer; içerik üstüne
        context.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        BHSConfig.save();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @FunctionalInterface
    private interface Toggle {
        void set(boolean value);
    }
}
