package com.betterhotbarswap.mixin;

import com.betterhotbarswap.client.input.HotbarInputController;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fiziksel tuş durumu TEK noktadan: bas/bırak olayları (ekran açık olsun olmasın).
// Basılı-tutma tekrar sayacı + düzenli toplama "basılı mı" bilgisi buradan gelir.
// Hedef metodun varlığı javap ile doğrulandı (Keyboard.onKey var, HandledScreen.keyReleased YOK - bug #18).
// Odak kaybında GLFW basılı tuşlar için RELEASE üretir (takılı tuş kalmaz).
@Mixin(Keyboard.class)
public final class KeyboardReleaseMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void betterhotbarswap$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_RELEASE) {
            HotbarInputController.releasePressed(key);
        } else if (action == GLFW.GLFW_PRESS) {
            HotbarInputController.physicalPress(key, scancode);
        }
    }
}
