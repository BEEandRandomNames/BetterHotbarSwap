package com.betterhotbarswap.mixin;

import com.betterhotbarswap.client.input.TidyCollect;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Düzenli toplama tek kapısı: tüm Shift+tık yolları (vanilla tık/çift-tık,
// fare modlarının sürüklemesi) buraya QUICK_MOVE olarak düşer.
// Hedef javap ile doğrulandı: clickSlot(int,int,int,SlotActionType,PlayerEntity).
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientInteractionQuickMoveMixin {
    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void betterhotbarswap$clickSlot(int syncId, int slotId, int button, SlotActionType actionType,
                                            PlayerEntity player, CallbackInfo ci) {
        if (actionType == SlotActionType.QUICK_MOVE && TidyCollect.redirectQuickMove(syncId, slotId)) {
            ci.cancel();
        }
    }
}
