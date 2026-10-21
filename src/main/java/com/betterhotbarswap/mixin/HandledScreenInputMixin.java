package com.betterhotbarswap.mixin;

import com.betterhotbarswap.client.input.HotbarInputController;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenInputMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void betterhotbarswap$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        // Tek-aktor kurali (#12): Creative ekranin sahibi CreativeMixin'dir.
        // Cift SWAP engellenir; liste sekmelerinde vanilla korunur.
        if ((Object) this instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) return;
        if (HotbarInputController.onKeyPressed((AbstractContainerScreen<?>) (Object) this, keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }

    // Fareye bağlı kısayollar (Mouse4/5): sol/sağ tık asla kısayol sayılmaz.
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void betterhotbarswap$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button <= 1) return;
        if ((Object) this instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) return;
        if (HotbarInputController.onMouseHotkey((AbstractContainerScreen<?>) (Object) this, button)) {
            cir.setReturnValue(true);
        }
    }
}