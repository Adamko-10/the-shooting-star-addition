package dev.ss05.halley.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * What a draw hook into the level renderer gets (from {@code HalleyClient}): the camera, this frame's projection and
 * view (the camera's turn: positions are relative to the camera) and the partial tick.
 */
public record WorldFrame(Camera camera, Matrix4f projection, Matrix4f view, float partial) {
    /**
     * Draws with this frame's view as the model-view matrix, whatever it was set to when the hook was called (some
     * points of the level renderer have it set, some don't), and puts it back after.
     */
    public void drawInView(Runnable draw) {
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.set(this.view);
        RenderSystem.applyModelViewMatrix();
        try {
            draw.run();
        } finally {
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }
}
