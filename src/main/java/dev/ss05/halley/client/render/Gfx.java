package dev.ss05.halley.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The few frame-level things the renderers need from Minecraft 1.21.11 (Fabric 1.21.11 branch), in one place: 26.x
 * hands most of them over in its camera render state, 1.21.11 keeps them on the game renderer.
 */
public final class Gfx {
    private Gfx() {
    }

    public static Camera camera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera();
    }

    /** The far plane's distance, in blocks. */
    public static double depthFar() {
        return Minecraft.getInstance().gameRenderer.getDepthFar();
    }

    /** The camera's rotation as a view matrix (camera-relative world to view space), as the level is drawn with. */
    public static Matrix4f viewRotation() {
        return new Matrix4f().rotation(camera().rotation().conjugate(new Quaternionf()));
    }

    /** The perspective projection at the player's field of view (for pointing at things on screen, not drawing). */
    public static Matrix4f projection() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gameRenderer.getProjectionMatrix(minecraft.options.fov().get().floatValue());
    }

    /** The per-draw transform block for this model-view (no colour tint, no offset, no texture matrix). */
    public static GpuBufferSlice transforms(Matrix4fc modelView) {
        return RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
            new Vector3f(), new Matrix4f());
    }

    /** The transform block for whatever model-view the level is being drawn with right now. */
    public static GpuBufferSlice levelTransforms() {
        return transforms(new Matrix4f(RenderSystem.getModelViewMatrix()));
    }

    /** A render pass onto the main target, keeping what's there, with its depth buffer. Close it when done. */
    public static RenderPass levelPass(String name) {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> name, target.getColorTextureView(),
            OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty());
    }
}
