package com.betterhotbarswap.client.debug;

import com.betterhotbarswap.BetterHotbarSwap;
import com.betterhotbarswap.config.BHSConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

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
            String stackName = focusedSlot.getItem().isEmpty() ? "Empty" : focusedSlot.getItem().getHoverName().getString();
            lastInfo = String.format("SlotId:%d | %s", focusedSlot.index, stackName);
        } catch (Exception e) {
            lastInfo = "SlotId:" + focusedSlot.index;
        }
        lastHotbar = hotbarIndex;
    }

    public static String describeSlot(Slot slot, Player player) {
        if (slot == null) {
            return "null";
        }
        String stack;
        try {
            stack = slot.getItem().isEmpty()
                ? "empty"
                : slot.getItem().getHoverName().getString() + "x" + slot.getItem().getCount();
        } catch (Exception e) {
            stack = "?";
        }
        String inv;
        if (player != null && slot.container == player.getInventory()) {
            inv = "player idx=" + slot.getContainerSlot();
        } else if (slot.container == null) {
            inv = "no-inv";
        } else {
            inv = slot.container.getClass().getSimpleName() + " idx=" + slot.getContainerSlot();
        }
        boolean take = false;
        try {
            take = player != null && slot.mayPickup(player);
        } catch (Exception ignored) {
        }
        return "id=" + slot.index + " [" + inv + "] " + stack + " take=" + take;
    }

    public static void render(GuiGraphics context) {
        var client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        if (!BHSConfig.debugEnabled) return;
        int y = 5;
        context.drawString(client.font, Component.literal("[BHS] " + lastInfo + " | Hotbar:" + lastHotbar), 5, y, 0x00FF00);
        y += 12;
        for (String line : LINES) {
            context.drawString(client.font, Component.literal(line), 5, y, 0xFFFF00);
            y += 10;
        }
    }
}
