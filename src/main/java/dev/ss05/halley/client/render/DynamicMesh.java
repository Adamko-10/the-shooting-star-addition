package dev.ss05.halley.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import java.nio.ByteBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Geometry built on the CPU every frame and drawn from a GPU buffer of its own (the way the weather is drawn). The
 * upload has to happen outside any render pass; the draw inside one.
 */
public final class DynamicMesh implements AutoCloseable {
    private final String label;
    @Nullable
    private GpuBuffer buffer;
    private PrimitiveTopology topology = PrimitiveTopology.QUADS;
    private int indexCount;

    public DynamicMesh(String label) {
        this.label = label;
    }

    /** Copies the mesh to the GPU and closes it. False when there is nothing to draw. */
    public boolean upload(@Nullable MeshData mesh) {
        this.indexCount = 0;
        if (mesh == null) {
            return false;
        }
        try (mesh) {
            ByteBuffer vertices = mesh.vertexBuffer();
            int size = vertices.remaining();
            if (size == 0) {
                return false;
            }
            GpuDevice device = RenderSystem.getDevice();
            if (this.buffer == null || this.buffer.isClosed() || this.buffer.size() < size) {
                if (this.buffer != null) {
                    this.buffer.close();
                }
                // room to grow, so a strike that gets busier doesn't reallocate every frame
                long capacity = Math.max(size + (size >> 1), 1 << 16);
                this.buffer = device.createBuffer(() -> this.label, GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_VERTEX, capacity);
            }
            device.createCommandEncoder().writeToBuffer(this.buffer.slice(0, size), vertices);
            this.topology = mesh.drawState().primitiveTopology();
            this.indexCount = mesh.drawState().indexCount();
            // Minecraft's shared index buffer for this kind of geometry, grown now if it has to be (never mid-pass:
            // growing it replaces the buffer, so every mesh drawn in a pass is uploaded before the pass opens)
            RenderSystem.getSequentialBuffer(this.topology).getBuffer(this.indexCount);
            return this.indexCount > 0;
        }
    }

    /** Draws what was last uploaded, with whatever pipeline and uniforms the pass has set. */
    public void draw(RenderPass pass) {
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(this.topology);
        if (this.buffer == null || this.indexCount <= 0 || !indices.hasStorage(this.indexCount)) {
            return;
        }
        pass.setVertexBuffer(0, this.buffer.slice());
        pass.setIndexBuffer(indices.getBuffer(), indices.type());
        pass.drawIndexed(this.indexCount, 1, 0, 0, 0);
    }

    @Override
    public void close() {
        if (this.buffer != null) {
            this.buffer.close();
            this.buffer = null;
        }
        this.indexCount = 0;
    }
}
