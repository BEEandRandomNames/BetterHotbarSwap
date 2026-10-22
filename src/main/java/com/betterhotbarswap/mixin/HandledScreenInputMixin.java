package com.betterhotbarswap.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.betterhotbarswap.client.input.HotbarInputController;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenInputMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void betterhotbarswap$keyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        // Tek-aktor kurali (#12): Creative ekranin sahibi CreativeMixin'dir.
        // Cift SWAP engellenir; liste sekmelerinde vanilla korunur.
        if ((Object) this instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) return;
        if (HotbarInputController.onKeyPressed((AbstractContainerScreen<?>) (Object) this, event.key(), event.keycode(), event.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    // Fareye bağlı kısayollar (Mouse4/5): sol/sağ tık asla kısayol sayılmaz.
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void betterhotbarswap$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        int button = event.button();
        // 26.3+: SDL fare numaraları 1'den başlar (sol 1, orta 2, sağ 3); sol/sağ tık kısayol değildir.
        if (button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT) return;
        if ((Object) this instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) return;
        if (HotbarInputController.onMouseHotkey((AbstractContainerScreen<?>) (Object) this, button)) {
            cir.setReturnValue(true);
        }
    }
}