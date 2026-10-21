package com.betterhotbarswap.mixin;

import com.betterhotbarswap.client.input.TidyCollect;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Düzenli toplama tek kapısı: tüm Shift+tık yolları (vanilla tık/çift-tık,
// fare modlarının sürüklemesi) buraya QUICK_MOVE olarak düşer.
// Hedef javap ile doğrulandı: clickSlot(int,int,int,SlotActionType,PlayerEntity).
@Mixin(MultiPlayerGameMode.class)
public abstract class ClientInteractionQuickMoveMixin {
    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void betterhotbarswap$clickSlot(int syncId, int slotId, int button, ContainerInput actionType,
                                            Player player, CallbackInfo ci) {
        if (actionType == ContainerInput.QUICK_MOVE && TidyCollect.redirectQuickMove(syncId, slotId)) {
            ci.cancel();
        }
    }
}
