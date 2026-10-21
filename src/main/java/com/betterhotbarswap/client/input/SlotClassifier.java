package com.betterhotbarswap.client.input;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class SlotClassifier {
    private SlotClassifier() {
    }

    public static boolean isPlayerInventorySlot(Slot slot, Player player) {
        return player!= null && slot.container == player.getInventory();
    }

    public static boolean isHotbarSlot(Slot slot, Player player) {
        return isPlayerInventorySlot(slot, player) && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < Inventory.getSelectionSize();
    }

    public static boolean isMainInventorySlot(Slot slot, Player player) {
        return isPlayerInventorySlot(slot, player) && slot.getContainerSlot() >= Inventory.getSelectionSize() && slot.getContainerSlot() < 36;
    }

    // NOT: adı creative kalsa da bu, survival dahil HER ekranda "alınabilir
    // container slotu" demektir (büyü masası lapis slotu, sandık vb. dahil).
    // Atama sistemi bilerek output/input/depo ayırmaz: hepsi aynı kural
    // zincirinden geçer (MERGE/PRIORITY/OVERFLOW); SADECE shift-bulk output'a
    // özeldir (üretim). Evrensellik ilkesi gereği blok-özel dal YOKTUR.
    public static boolean isCreativeListSlot(Slot slot, Player player) {
        if (slot == null || player == null) return false;
        // Creative listesi: player inventory DEĞİL, stack var, ve alınabilir
        return slot.container!= player.getInventory()
                && slot.hasItem()
                && slot.mayPickup(player);
    }

    public static boolean isProbableOutputSlot(Slot slot, Player player) {
        return isProbableOutputSlot(slot, player, false);
    }

    // inCreativeScreen: Creative ekranındaki oyuncu-dışı slotlar liste slotudur,
    // output sayılmaz. Diğer ekranlarda davranışsal heuristic geçerlidir.
    // (Bug #21: eski sırada creative-kontrol CraftingResultSlot dalını öldürüyordu.)
    public static boolean isProbableOutputSlot(Slot slot, Player player, boolean inCreativeScreen) {
        if (slot == null || player == null || !slot.hasItem() || !slot.mayPickup(player)) {
            return false;
        }

        // Kesin output her şeyden önce (ölü dal olmamalı).
        if (slot instanceof ResultSlot) {
            return true;
        }

        if (isPlayerInventorySlot(slot, player)) {
            return false;
        }

        if (inCreativeScreen) {
            return false;
        }

        ItemStack stack = slot.getItem();
        return !stack.isEmpty() && !slot.mayPlace(stack);
    }

    public static boolean isGeneralStorageSlot(Slot slot, Player player, boolean includeHotbar) {
        if (slot == null || player == null ||!slot.mayPickup(player)) {
            return false;
        }

        if (includeHotbar && isHotbarSlot(slot, player)) {
            return true;
        }
        return isMainInventorySlot(slot, player);
    }

    // Zirh/offhand ASLA kaynak olmaz (vanilla yapsin, test matrisi). Bug #31.
    public static boolean isArmorOrOffhand(Slot slot, Player player) {
        if (slot == null || player == null) return false;
        if (slot.container != player.getInventory()) return false;
        int idx = slot.getContainerSlot();
        return idx >= 36 && idx <= 40;
    }

    // YENI: Bu slot hotbar'a atanabilir mi? (Creative + Survival hepsi)
    public static boolean isAssignableSlot(Slot slot, Player player) {
        if (slot == null ||!slot.hasItem()) return false;
        if (isArmorOrOffhand(slot, player)) return false;
        return isPlayerInventorySlot(slot, player)
                || isCreativeListSlot(slot, player)
                || isProbableOutputSlot(slot, player);
    }
}