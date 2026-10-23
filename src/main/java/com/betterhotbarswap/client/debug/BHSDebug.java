package com.betterhotbarswap.client.debug;

import com.betterhotbarswap.BetterHotbarSwap;
import com.betterhotbarswap.config.BHSConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Eylemsel debug: klavye + slot + click akışının son olaylarını hem ekranda
 * hem latest.log'da ("[BHS-ACTION]" prefixiyle) tutar.
 */
public final class BHSDebug {
    private static final int MAX_LINES = 12;
    private static final Deque<String> LINES = new ArrayDeque<>();
    public static String lastInfo = "No slot";
    public static int lastHotbar = -1;

    private BHSDebug() {
    }

    public static void log(String line) {
        // Debug kapalıysa ekran da dosya-spamı da durur (teşhis snapshot'ları
        // [BHS-INV]/[BHS-LAYOUT] ayrı kanaldan yazılmaya devam eder).
        if (!BHSConfig.debugEnabled) return;
        if (LINES.size() >= MAX_LINES) {
            LINES.pollFirst();
        }
        LINES.addLast(line);
        BetterHotbarSwap.LOGGER.info("[BHS-ACTION] {}", line);
    }

    public static void update(Slot focusedSlot, int hotbarIndex) {
        if (focusedSlot == null) {
            lastInfo = "focusedSlot = null";
            lastHotbar = -1;
            return;
        }
        try {
            String stackName = focusedSlot.getStack().isEmpty() ? "Empty" : focusedSlot.getStack().getName().getString();
            lastInfo = String.format("SlotId:%d | %s", focusedSlot.id, stackName);
        } catch (Exception e) {
            lastInfo = "SlotId:" + focusedSlot.id;
        }
        lastHotbar = hotbarIndex;
    }

    public static String describeSlot(Slot slot, PlayerEntity player) {
        if (slot == null) {
            return "null";
        }
        String stack;
        try {
            stack = slot.getStack().isEmpty()
                ? "empty"
                : slot.getStack().getName().getString() + "x" + slot.getStack().getCount();
        } catch (Exception e) {
            stack = "?";
        }
        String inv;
        if (player != null && slot.inventory == player.getInventory()) {
            inv = "player idx=" + slot.getIndex();
        } else if (slot.inventory == null) {
            inv = "no-inv";
        } else {
            inv = slot.inventory.getClass().getSimpleName() + " idx=" + slot.getIndex();
        }
        boolean take = false;
        try {
            take = player != null && slot.canTakeItems(player);
        } catch (Exception ignored) {
        }
        return "id=" + slot.id + " [" + inv + "] " + stack + " take=" + take;
    }

    public static void render(DrawContext context) {
        var client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        if (!BHSConfig.debugEnabled) return;
        int y = 5;
        context.drawTextWithShadow(client.textRenderer, Text.literal("[BHS] " + lastInfo + " | Hotbar:" + lastHotbar), 5, y, 0xFF00FF00);
        y += 12;
        for (String line : LINES) {
            context.drawTextWithShadow(client.textRenderer, Text.literal(line), 5, y, 0xFFFFFF00);
            y += 10;
        }
    }
}
