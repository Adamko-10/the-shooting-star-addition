package dev.ss05.halley.client.render;

import net.minecraft.client.Camera;
import org.joml.Matrix4f;

/**
 * What a draw hook into the level renderer gets (from {@code HalleyClient}): the camera, this frame's projection and
 * view (the camera's turn, as the level renderer's pose stack holds it: positions are relative to the camera) and the
 * partial tick.
 */
public record WorldFrame(Camera camera, Matrix4f projection, Matrix4f view, float partial) {
}
