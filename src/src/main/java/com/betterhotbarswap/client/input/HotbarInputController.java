package com.betterhotbarswap.client.input;

import com.betterhotbarswap.config.BHSConfig;
import com.betterhotbarswap.mixin.HandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;

public final class HotbarInputController {
    private static int heldHotbarIndex = -1;
    private static boolean firstContainerAssignmentUsed = false;

    private HotbarInputController() {
    }

    public static boolean onKeyPressed(HandledScreen<?> screen, int keyCode, int scanCode) {
        MinecraftClient client = MinecraftClient.getInstance();
        int hotbarIndex = matchingHotbarKey(client, keyCode, scanCode);
        if (hotbarIndex < 0) {
            return false;
        }

        if (isShiftDown(client)) {
            if (heldHotbarIndex != hotbarIndex) {
                heldHotbarIndex = hotbarIndex;
                firstContainerAssignmentUsed = false;
            }
            return true;
        }

        Slot focusedSlot = ((HandledScreenAccessor) screen).betterhotbarswap$getFocusedSlot();
        if (focusedSlot == null || ((HandledScreenAccessor) screen).betterhotbarswap$isCursorDragging()) {
            return false;
        }

        if (VanillaClickExecutor.canFullyMergeToHotbar(screen, focusedSlot, hotbarIndex)) {
            return VanillaClickExecutor.assignSlotToHotbar(screen, focusedSlot, hotbarIndex, true);
        }

        if (SlotClassifier.isProbableOutputSlot(focusedSlot, client.player) && BHSConfig.craftingOutputQuickSwap) {
            return VanillaClickExecutor.assignSlotToHotbar(screen, focusedSlot, hotbarIndex, true);
        }

        if (screen instanceof InventoryScreen && BHSConfig.hotbarPriority) {
            return VanillaClickExecutor.assignSlotToHotbar(screen, focusedSlot, hotbarIndex, true);
        }

        return false;
    }

    public static void tick(MinecraftClient client) {
        if (heldHotbarIndex >= 0 && (!isShiftDown(client) || !isHotbarKeyDown(client, heldHotbarIndex))) {
            releaseHeldHotbar();
        }
    }

    public static boolean onMouseClicked(HandledScreen<?> screen, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || heldHotbarIndex < 0 || !BHSConfig.shiftHotbarPickup) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (!isShiftDown(client) || ((HandledScreenAccessor) screen).betterhotbarswap$isCursorDragging()) {
            return false;
        }

        Slot focusedSlot = ((HandledScreenAccessor) screen).betterhotbarswap$getFocusedSlot();
        if (focusedSlot == null || client.player == null || !screen.getScreenHandler().getCursorStack().isEmpty()) {
            return false;
        }

        boolean handled;
        if (!firstContainerAssignmentUsed) {
            handled = VanillaClickExecutor.assignSlotToHotbar(screen, focusedSlot, heldHotbarIndex, true);
            firstContainerAssignmentUsed = handled;
        } else {
            handled = VanillaClickExecutor.moveSlotToMainInventory(screen, focusedSlot, true);
        }
        return handled;
    }

    private static void releaseHeldHotbar() {
        heldHotbarIndex = -1;
        firstContainerAssignmentUsed = false;
    }

    private static int matchingHotbarKey(MinecraftClient client, int keyCode, int scanCode) {
        if (client == null || client.options == null) {
            return -1;
        }

        KeyBinding[] hotbarKeys = client.options.hotbarKeys;
        for (int index = 0; index < hotbarKeys.length; index++) {
            if (hotbarKeys[index].matchesKey(keyCode, scanCode)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isShiftDown(MinecraftClient client) {
        if (client == null || client.getWindow() == null) {
            return false;
        }
        long handle = client.getWindow().getHandle();
        return InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean isHotbarKeyDown(MinecraftClient client, int hotbarIndex) {
        if (client == null || client.options == null || hotbarIndex < 0 || hotbarIndex >= client.options.hotbarKeys.length) {
            return false;
        }
        return client.options.hotbarKeys[hotbarIndex].isPressed();
    }
}