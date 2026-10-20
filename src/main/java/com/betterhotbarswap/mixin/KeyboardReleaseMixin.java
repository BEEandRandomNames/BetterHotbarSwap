package com.betterhotbarswap.mixin;

import com.betterhotbarswap.client.input.HotbarInputController;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Bırakma (release) takibi TEK noktadan: basılı-tutma tekrar sayacı buradan sıfırlanır.
// Hedef metodun varlığı javap ile doğrulandı (Keyboard.onKey var, HandledScreen.keyReleased YOK - bug #18).
@Mixin(Keyboard.class)
public final class KeyboardReleaseMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void betterhotbarswap$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_RELEASE) {
            HotbarInputController.releasePressed(key);
        }
    }
}
