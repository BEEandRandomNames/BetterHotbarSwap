package com.betterhotbarswap.client.input;

import com.betterhotbarswap.config.BHSConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public final class VanillaClickExecutor {
    private VanillaClickExecutor() {
    }

    public static boolean assignSlotToHotbar(HandledScreen<?> screen, Slot sourceSlot, int hotbarIndex, boolean useHotbarPriority) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canStart(client, screen, sourceSlot, hotbarIndex)) {
            return false;
        }

        ScreenHandler handler = screen.getScreenHandler();
        Slot hotbarSlot = findHotbarSlot(handler, client.player, hotbarIndex);
        if (hotbarSlot == null) {
            return false;
        }

        if (useHotbarPriority && hotbarSlot.hasStack() && hotbarSlot.id != sourceSlot.id) {
            Slot destination = findDisplacedDestination(handler, client.player, hotbarSlot, sourceSlot);
            if (destination == null) {
                return false;
            }
            click(client, handler, hotbarSlot.id, 0, SlotActionType.PICKUP);
            click(client, handler, destination.id, 0, SlotActionType.PICKUP);

            if (!handler.getCursorStack().isEmpty()) {
                click(client, handler, hotbarSlot.id, 0, SlotActionType.PICKUP);
                return false;
            }
        }

        click(client, handler, sourceSlot.id, hotbarIndex, SlotActionType.SWAP);
        return true;
    }

    public static boolean moveSlotToMainInventory(HandledScreen<?> screen, Slot sourceSlot) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canStart(client, screen, sourceSlot, 0)) {
            return false;
        }

        ScreenHandler handler = screen.getScreenHandler();
        Slot destination = findInventoryDestination(handler, client.player, sourceSlot.getStack(), sourceSlot);
        if (destination == null) {
            return false;
        }

        click(client, handler, sourceSlot.id, 0, SlotActionType.PICKUP);
        click(client, handler, destination.id, 0, SlotActionType.PICKUP);

        if (!handler.getCursorStack().isEmpty()) {
            click(client, handler, sourceSlot.id, 0, SlotActionType.PICKUP);
            return false;
        }
        return true;
    }

    private static boolean canStart(MinecraftClient client, HandledScreen<?> screen, Slot sourceSlot, int hotbarIndex) {
        return client != null
            && client.player != null
            && client.interactionManager != null
            && screen != null
            && sourceSlot != null
            && sourceSlot.hasStack()
            && sourceSlot.canTakeItems(client.player)
            && hotbarIndex >= 0
            && hotbarIndex < 9
            && screen.getScreenHandler().getCursorStack().isEmpty();
    }

    private static Slot findHotbarSlot(ScreenHandler handler, PlayerEntity player, int hotbarIndex) {
        for (Slot slot : handler.slots) {
            if (SlotClassifier.isHotbarSlot(slot, player) && slot.getIndex() == hotbarIndex) {
                return slot;
            }
        }
        return null;
    }

    private static Slot findDisplacedDestination(ScreenHandler handler, PlayerEntity player, Slot displacedSlot, Slot sourceSlot) {
        ItemStack displaced = displacedSlot.getStack();
        Slot stackDestination = BHSConfig.stackToInventoryFirst ? findStackDestination(handler, player, displaced, displacedSlot, sourceSlot, true) : null;
        if (stackDestination != null) {
            return stackDestination;
        }

        Slot hotbarDestination = findNearestHotbarEmpty(handler, player, displacedSlot.getIndex(), sourceSlot);
        if (hotbarDestination != null) {
            return hotbarDestination;
        }

        return findEmptyMainInventorySlot(handler, player, sourceSlot);
    }

    private static Slot findInventoryDestination(ScreenHandler handler, PlayerEntity player, ItemStack stack, Slot sourceSlot) {
        Slot stackDestination = BHSConfig.stackToInventoryFirst ? findStackDestination(handler, player, stack, sourceSlot, sourceSlot, false) : null;
        if (stackDestination != null) {
            return stackDestination;
        }
        return findEmptyMainInventorySlot(handler, player, sourceSlot);
    }

    private static Slot findStackDestination(ScreenHandler handler, PlayerEntity player, ItemStack stack, Slot displacedSlot, Slot sourceSlot, boolean includeHotbar) {
        if (stack.isEmpty()) {
            return null;
        }

        for (Slot slot : handler.slots) {
            if (slot.id == displacedSlot.id || slot.id == sourceSlot.id || !SlotClassifier.isGeneralStorageSlot(slot, player, includeHotbar)) {
                continue;
            }

            ItemStack existing = slot.getStack();
            if (!existing.isEmpty() && ItemStack.canCombine(existing, stack) && existing.getCount() < existing.getMaxCount()) {
                return slot;
            }
        }
        return null;
    }

    private static Slot findNearestHotbarEmpty(ScreenHandler handler, PlayerEntity player, int hotbarIndex, Slot sourceSlot) {
        for (int offset = 1; offset < 9; offset++) {
            Slot left = findHotbarSlot(handler, player, hotbarIndex - offset);
            if (left != null && left.id != sourceSlot.id && !left.hasStack()) {
                return left;
            }
        }

        for (int offset = 1; offset < 9; offset++) {
            Slot right = findHotbarSlot(handler, player, hotbarIndex + offset);
            if (right != null && right.id != sourceSlot.id && !right.hasStack()) {
                return right;
            }
        }

        return null;
    }

    private static Slot findEmptyMainInventorySlot(ScreenHandler handler, PlayerEntity player, Slot sourceSlot) {
        for (Slot slot : handler.slots) {
            if (slot.id != sourceSlot.id && SlotClassifier.isMainInventorySlot(slot, player) && !slot.hasStack()) {
                return slot;
            }
        }
        return null;
    }

    private static void click(MinecraftClient client, ScreenHandler handler, int slotId, int button, SlotActionType actionType) {
        client.interactionManager.clickSlot(handler.syncId, slotId, button, actionType, client.player);
    }
}
