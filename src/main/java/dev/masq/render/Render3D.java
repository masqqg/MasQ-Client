// PATH: src/main/java/dev/masq/render/Render3D.java
package dev.masq.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.List;

/**
 * Duvarların ardından görünen dolu kutu çizimi (Fabric docs 1.21.11 CustomRenderPipeline'dan uyarlandı).
 * Modüller add() ile bir katman kaydeder; her karede katmanlar box/slab/edges çağırır, sonunda tek draw yapılır.
 */
public final class Render3D {
	private static final RenderPipeline PIPE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
		.withLocation(Identifier.fromNamespaceAndPath("masq", "pipeline/filled_through_walls"))
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.build());

	private static final ByteBufferBuilder ALLOC = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MOD = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEX_MATRIX = new Matrix4f();
	private static final List<Runnable> LAYERS = new ArrayList<>();

	private static MappableRingBuffer ring;
	private static BufferBuilder buf;
	private static Matrix4fc pose;
	private static int verts;

	private Render3D() {}

	public static void init() {
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(Render3D::frame);
	}

	/** Katman her karede çağrılır; kendi içinde aktif mi diye bakmalı. */
	public static void add(Runnable layer) { LAYERS.add(layer); }

	private static void frame(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (LAYERS.isEmpty() || mc.level == null) return;

		PoseStack ps = ctx.matrices();
		Vec3 cam = ctx.worldState().cameraRenderState.pos;
		ps.pushPose();
		ps.translate(-cam.x, -cam.y, -cam.z);
		pose = ps.last().pose();
		verts = 0;
		try {
			for (Runnable l : LAYERS) l.run();
		} finally {
			ps.popPose();
		}
		if (buf != null) flush(mc);
	}

	// ---- çizim ilkelleri (dünya koordinatı) ----

	private static void vtx(double x, double y, double z, float r, float g, float b, float a) {
		buf.addVertex(pose, (float) x, (float) y, (float) z).setColor(r, g, b, a);
		verts++;
	}

	private static void ensure() {
		if (buf == null) buf = new BufferBuilder(ALLOC, PIPE.getVertexFormatMode(), PIPE.getVertexFormat());
	}

	/** Sadece üst ve alt yüz (piksel sanatı gibi çok kutu gereken yerde ucuz). */
	public static void slab(double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
		ensure();
		float a = (argb >>> 24 & 255) / 255f, r = (argb >> 16 & 255) / 255f, g = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;
		vtx(x0, y1, z1, r, g, b, a); vtx(x1, y1, z1, r, g, b, a); vtx(x1, y1, z0, r, g, b, a); vtx(x0, y1, z0, r, g, b, a);
		vtx(x0, y0, z0, r, g, b, a); vtx(x1, y0, z0, r, g, b, a); vtx(x1, y0, z1, r, g, b, a); vtx(x0, y0, z1, r, g, b, a);
	}

	public static void box(double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
		ensure();
		float a = (argb >>> 24 & 255) / 255f, r = (argb >> 16 & 255) / 255f, g = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;
		vtx(x0, y0, z1, r, g, b, a); vtx(x1, y0, z1, r, g, b, a); vtx(x1, y1, z1, r, g, b, a); vtx(x0, y1, z1, r, g, b, a);
		vtx(x1, y0, z0, r, g, b, a); vtx(x0, y0, z0, r, g, b, a); vtx(x0, y1, z0, r, g, b, a); vtx(x1, y1, z0, r, g, b, a);
		vtx(x0, y0, z0, r, g, b, a); vtx(x0, y0, z1, r, g, b, a); vtx(x0, y1, z1, r, g, b, a); vtx(x0, y1, z0, r, g, b, a);
		vtx(x1, y0, z1, r, g, b, a); vtx(x1, y0, z0, r, g, b, a); vtx(x1, y1, z0, r, g, b, a); vtx(x1, y1, z1, r, g, b, a);
		vtx(x0, y1, z1, r, g, b, a); vtx(x1, y1, z1, r, g, b, a); vtx(x1, y1, z0, r, g, b, a); vtx(x0, y1, z0, r, g, b, a);
		vtx(x0, y0, z0, r, g, b, a); vtx(x1, y0, z0, r, g, b, a); vtx(x1, y0, z1, r, g, b, a); vtx(x0, y0, z1, r, g, b, a);
	}

	/** Çerçeve: 12 ince kutu (çizgi pipeline'ı gerekmez). */
	public static void edges(double x0, double y0, double z0, double x1, double y1, double z1, double t, int c) {
		t = Math.min(t, (y1 - y0) / 2);
		box(x0, y0, z0, x1, y0 + t, z0 + t, c); box(x0, y1 - t, z0, x1, y1, z0 + t, c);
		box(x0, y0, z1 - t, x1, y0 + t, z1, c); box(x0, y1 - t, z1 - t, x1, y1, z1, c);
		box(x0, y0, z0, x0 + t, y0 + t, z1, c); box(x1 - t, y0, z0, x1, y0 + t, z1, c);
		box(x0, y1 - t, z0, x0 + t, y1, z1, c); box(x1 - t, y1 - t, z0, x1, y1, z1, c);
		box(x0, y0, z0, x0 + t, y1, z0 + t, c); box(x1 - t, y0, z0, x1, y1, z0 + t, c);
		box(x0, y0, z1 - t, x0 + t, y1, z1, c); box(x1 - t, y0, z1 - t, x1, y1, z1, c);
	}

	public static int argb(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }

	// ---- GPU'ya gönder ----

	private static void flush(Minecraft mc) {
		BufferBuilder b = buf;
		buf = null;
		if (verts == 0) {
			b.build();
			return;
		}
		MeshData mesh = b.buildOrThrow();
		MeshData.DrawState ds = mesh.drawState();
		VertexFormat fmt = ds.format();
		int size = ds.vertexCount() * fmt.getVertexSize();

		if (ring == null || ring.size() < size) {
			if (ring != null) ring.close();
			ring = new MappableRingBuffer(() -> "masq world render", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
		}
		CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView view = enc.mapBuffer(ring.currentBuffer().slice(0, mesh.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(mesh.vertexBuffer(), view.data());
		}
		GpuBuffer vertices = ring.currentBuffer();

		GpuBuffer indices;
		VertexFormat.IndexType indexType;
		if (PIPE.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
			mesh.sortQuads(ALLOC, RenderSystem.getProjectionType().vertexSorting());
			indices = PIPE.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
			indexType = mesh.drawState().indexType();
		} else {
			RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(PIPE.getVertexFormatMode());
			indices = seq.getBuffer(ds.indexCount());
			indexType = seq.type();
		}

		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
			.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MOD, MODEL_OFFSET, TEX_MATRIX);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> "masq world render pass",
			mc.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
			mc.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(PIPE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indices, indexType);
			pass.drawIndexed(0, 0, ds.indexCount(), 1);
		}
		mesh.close();
		ring.rotate();
	}
}
