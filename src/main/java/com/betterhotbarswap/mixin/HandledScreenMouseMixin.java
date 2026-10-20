package com.betterhotbarswap.mixin;

import com.betterhotbarswap.client.input.TidyCollect;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// TidyCollect tıklama engeli: oturum armed iken depo slotuna SOL tık,
// vanilla PICKUP yerine tidy sıraya gider (imleç kavgası biterr).
// priority 900: diğer fare modlarından (örn. Mouse Tweaks) ÖNCE karar verilir;
// sıra ne olursa olsun taze-okuma + başarısızlıkta-vanilla sayesinde güvenlidir.
@Mixin(value = HandledScreen.class, priority = 900)
public abstract class HandledScreenMouseMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void betterhotbarswap$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (TidyCollect.handleClick((HandledScreen<?>) (Object) this, button)) {
            cir.setReturnValue(true);
        }
    }
}
