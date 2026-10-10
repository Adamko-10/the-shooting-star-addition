package dev.ss05.halley.compat.mixin;

import dev.ss05.halley.client.HalleyFx;
import dev.ss05.halley.client.luna.MoonFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional: the caster raises the remote to the sky as the mark lands (SS-05) or as the alarm sounds (SS-06), then
 * holds it out until the strike is over.
 */
@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"), require = 0)
    private void ss05halley$raiseRemote(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                        float netHeadYaw, float headPitch, CallbackInfo ci) {
        float partial = Minecraft.getInstance().getPartialTick();
        float[] pose = HalleyFx.armPoseFor(entity.getId(), partial);
        if (pose == null) {
            pose = MoonFx.armPoseFor(entity.getId(), partial);
        }
        if (pose == null || pose[3] <= 0.0F) {
            return;
        }
        ModelPart arm = ((HumanoidModel<?>) (Object) this).rightArm;
        arm.xRot = Mth.lerp(pose[3], arm.xRot, pose[0]);
        arm.yRot = Mth.lerp(pose[3], arm.yRot, pose[1]);
        arm.zRot = Mth.lerp(pose[3], arm.zRot, pose[2]);
    }
}
