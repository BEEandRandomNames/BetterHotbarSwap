package com.betterhotbarswap.mixin;

import com.betterhotbarswap.client.input.HotbarInputController;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenInputMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void betterhotbarswap$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (HotbarInputController.onKeyPressed((HandledScreen<?>) (Object) this, keyCode, scanCode)) {
            cir.setReturnValue(true);
        }
    }


    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void betterhotbarswap$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (HotbarInputController.onMouseClicked((HandledScreen<?>) (Object) this, button)) {
            cir.setReturnValue(true);
        }
    }
}
