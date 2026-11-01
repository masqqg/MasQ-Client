// PATH: src/main/java/dev/masq/module/impl/Freecam.java
package dev.masq.module.impl;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import dev.masq.module.Category;
import dev.masq.module.Module;
import dev.masq.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.phys.Vec3;

/** Serbest kamera: karakter yerinde kalır, kamera uçar. */
public final class Freecam extends Module {
	private final Setting.Num speed = add(new Setting.Num("Speed", 1.0f, 0.2f, 4f, 0.1f));
	private RemotePlayer cam;
	private Vec3 pos = Vec3.ZERO;

	public Freecam() { super("Freecam", "Fly the camera, body stays", Category.RENDER); }

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) { set(false); return; }
		cam = new RemotePlayer(mc.level, new GameProfile(java.util.UUID.randomUUID(), "MasQCam"));
		cam.noPhysics = true;
		pos = mc.player.getEyePosition().subtract(0, mc.player.getEyeHeight(), 0);
		place(mc);
		mc.level.addEntity(cam);
		mc.setCameraEntity(cam);
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.setCameraEntity(mc.player);
		if (cam != null) cam.discard();
		cam = null;
	}

	private void place(Minecraft mc) {
		cam.setPos(pos.x, pos.y, pos.z);
		cam.setYRot(mc.player.getYRot());
		cam.setXRot(mc.player.getXRot());
		cam.yRotO = cam.getYRot();
		cam.xRotO = cam.getXRot();
		cam.xo = pos.x; cam.yo = pos.y; cam.zo = pos.z;
	}

	private static boolean down(Minecraft mc, int key) {
		return InputConstants.isKeyDown(mc.getWindow(), key);
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (cam == null || mc.player == null || mc.screen != null) return;
		float yaw = (float) Math.toRadians(mc.player.getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		double mx = 0, my = 0, mz = 0;
		if (down(mc, InputConstants.KEY_W)) { mx += fx; mz += fz; }
		if (down(mc, InputConstants.KEY_S)) { mx -= fx; mz -= fz; }
		if (down(mc, InputConstants.KEY_A)) { mx += fz; mz -= fx; }
		if (down(mc, InputConstants.KEY_D)) { mx -= fz; mz += fx; }
		if (down(mc, InputConstants.KEY_SPACE)) my += 1;
		if (down(mc, InputConstants.KEY_LSHIFT)) my -= 1;
		pos = pos.add(new Vec3(mx, my, mz).scale(speed.f()));
		place(mc);
		// karakter yürümesin
		mc.options.keyUp.setDown(false);
		mc.options.keyDown.setDown(false);
		mc.options.keyLeft.setDown(false);
		mc.options.keyRight.setDown(false);
		mc.options.keyJump.setDown(false);
		mc.options.keyShift.setDown(false);
	}
}
