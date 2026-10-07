package dev.ss05.halley.client.mixin;

import dev.ss05.halley.client.HalleyFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The caster raises the remote to the sky as the mark lands, then holds it out until the strike is over. */
@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void ss05halley$raiseRemote(AvatarRenderState state, CallbackInfo ci) {
        float[] pose = HalleyFx.armPoseFor(state.id, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        if (pose == null || pose[3] <= 0.0F) {
            return;
        }
        ModelPart arm = ((HumanoidModel<?>) (Object) this).rightArm;
        arm.xRot = Mth.lerp(pose[3], arm.xRot, pose[0]);
        arm.yRot = Mth.lerp(pose[3], arm.yRot, pose[1]);
        arm.zRot = Mth.lerp(pose[3], arm.zRot, pose[2]);
    }
}
