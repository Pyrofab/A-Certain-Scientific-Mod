/*
 * Although most of the project is under the LGPL v3
 * The files for WORST are licensed under MIT.
 * 
 * Copyright 2020 Valoeghese
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * 
 * - The above copyright notice and this permission notice shall be
 *   included in all copies or substantial portions of the Software.
 * 
 * - THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 *   INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 *   PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 *   CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *   FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package tk.valoeghese.tknm.rendering;

import java.util.List;

import javax.annotation.Nullable;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.impl.renderer.RendererAccessImpl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3d;

/**
 * World Oriented Render System Translator Impl.
 * @author Valoeghese
 */
public final class WORSTImpl {
	private WORSTImpl() {
		// NO-OP
	}

	private static boolean started = false;
	private static MatrixStack currentStack;
	// might be able to make this constant, but not taking chances
	private static VertexConsumerProvider.Immediate immediate;
	private static VertexConsumer vc;
	private static Renderer renderer;
	private static MeshBuilder meshBuilder;
	private static QuadEmitter emitter;
	private static Camera camera;
	// whether we have pushed yet
	private static boolean dirty = false;
	// current index of vertex
	private static int index = 0;
	private static Vector3f[] quadBuffer = new Vector3f[4];
	private static boolean dqb = false; // whether the quad buffer is "dirty"
	public static final Vector3f ONE = new Vector3f(1.0f, 1.0f, 1.0f);

	public static void init(MatrixStack stack, Camera cameraIn) throws RuntimeException {
		// init notif
		if (started) {
			throw new RuntimeException("WORST already initialised! Call end() before re-initialising!");
		}
		started = true;
		// set variables
		currentStack = stack;
		immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
		vc = immediate.getBuffer(RenderLayer.getSolid());
		renderer = RendererAccessImpl.INSTANCE.getRenderer();
		camera = cameraIn;
	}

	public static void mesh() {
		// flush if already dirty
		if (dirty) {
			// flush
			immediate.draw(RenderLayer.getSolid());
			// pop
			currentStack.pop();
		} else {
			dirty = true;
		}
		// push matrices
		currentStack.push();
		// start mesh
		meshBuilder = renderer.meshBuilder();
		emitter = meshBuilder.getEmitter();
		index = 0;
	}

	public static void nextQuadDouble() {
		if (dqb) {
			// normal
			emitter
			.pos(0, quadBuffer[0])
			.pos(1, quadBuffer[1])
			.pos(2, quadBuffer[2])
			.pos(3, quadBuffer[3]).emit()
			// reverse
			.pos(0, quadBuffer[0])
			.pos(1, quadBuffer[3])
			.pos(2, quadBuffer[2])
			.pos(3, quadBuffer[1]).emit();
			dqb = false;
			index = 0;
		}
	}

	public static void nextQuadSingle() {
		if (dqb) {
			emitter
			.pos(0, quadBuffer[0])
			.pos(1, quadBuffer[1])
			.pos(2, quadBuffer[2])
			.pos(3, quadBuffer[3]).emit();
			dqb = false;
			index = 0;
		}
	}

	public static void vertex(float x, float y, float z) {
		dqb = true;
		quadBuffer[index++] = new Vector3f(x, y, z);
	}

	public static void renderMesh(Vector3f translate, @Nullable Quaternion rotation, Vector3f scale) {
		// offset position from camera and translate
		Vec3d pos = camera.getPos();

		// scale
		currentStack.scale(scale.getX(), scale.getY(), scale.getZ());

		// rotate
		if (rotation != null) {
			currentStack.multiply(rotation);
		}

		// translate
		currentStack.translate(-pos.x + translate.getX(), -pos.y + translate.getY(), -pos.z + translate.getZ());

		Mesh m = meshBuilder.build();
		List<BakedQuad>[] quadListArray = ModelHelper.toQuadLists(m);

		for (int i = 0; i < quadListArray.length; ++i) {
			for (BakedQuad bq : quadListArray[i]) {
				vc.quad(currentStack.peek(), bq, 0.5f, 0.5f, 0.5f, 15728880, OverlayTexture.DEFAULT_UV);
			}
		}
	}

	public static void end() {
		if (started) {
			started = false;
			// flush if dirty
			if (dirty) {
				// flush
				immediate.draw(RenderLayer.getSolid());
				// pop
				currentStack.pop();
				// not dirty anymore!
				dirty = false;
			}
			// reset stack and camera variables
			currentStack = null;
			camera = null;
		}
	}
}