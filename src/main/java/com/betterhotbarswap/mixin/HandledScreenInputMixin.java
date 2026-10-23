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
    private void betterhotbarswap$keyPressed(net.minecraft.client.input.KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        // Tek-aktor kurali (#12): Creative ekranin sahibi CreativeMixin'dir.
        // Cift SWAP engellenir; liste sekmelerinde vanilla korunur.
        if ((Object) this instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen) return;
        if (HotbarInputController.onKeyPressed((HandledScreen<?>) (Object) this, input.key(), input.scancode(), input.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    // Fareye bağlı kısayollar (Mouse4/5): sol/sağ tık asla kısayol sayılmaz.
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void betterhotbarswap$mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        int button = click.button();
        if (button <= 1) return;
        if ((Object) this instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen) return;
        if (HotbarInputController.onMouseHotkey((HandledScreen<?>) (Object) this, button)) {
            cir.setReturnValue(true);
        }
    }
}