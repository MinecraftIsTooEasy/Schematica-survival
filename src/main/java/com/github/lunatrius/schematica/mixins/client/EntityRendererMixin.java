// MITE port (c) 2025 hahahha. Licensed under the MIT License.
package com.github.lunatrius.schematica.mixins.client;

import com.github.lunatrius.schematica.client.render.SchematicProjectionRenderer;
import net.minecraft.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @Inject(
            method = "renderWorld(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/EntityRenderer;renderRainSnow(F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void schematica$renderProjection(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        SchematicProjectionRenderer.render(partialTicks);
    }
}
