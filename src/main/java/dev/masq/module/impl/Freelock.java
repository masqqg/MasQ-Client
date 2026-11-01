// PATH: src/main/java/dev/masq/module/impl/FreeLook.java
package dev.masq.module.impl;

import com.mojang.authlib.GameProfile;
import dev.masq.module.Category;
import dev.masq.module.Module;
import dev.masq.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.phys.Vec3;

/** Kendini dışarıdan görme: kamera karakterin arkasında/önünde, duvarlar içinden geçer. */
public final class FreeLook extends Module {
	private final Setting.Num dist = add(new Setting.Num("Distance", 4f, 1f, 12f, 0.5f));
	private final Setting.Mode side = add(new Setting.Mode("Side", "Behind", "Behind", "Front"));
	private RemotePlayer cam;

	public FreeLook() { super("FreeLook", "See yourself, camera ignores walls", Category.RENDER); }

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) { set(false); return; }
		cam = new RemotePlayer(mc.level, new GameProfile(java.util.UUID.randomUUID(), "MasQCam"));
		cam.noPhysics = true;
		update(mc);
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

	private void update(Minecraft mc) {
		float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
		boolean front = side.is("Front");
		Vec3 look = Vec3.directionFromRotation(pitch, yaw);
		Vec3 eye = mc.player.getEyePosition();
		Vec3 p = front ? eye.add(look.scale(dist.f())) : eye.subtract(look.scale(dist.f()));
		double y = p.y - mc.player.getEyeHeight();
		cam.setPos(p.x, y, p.z);
		float cy = front ? yaw + 180f : yaw, cp = front ? -pitch : pitch;
		cam.setYRot(cy);
		cam.setXRot(cp);
		cam.yRotO = cy;
		cam.xRotO = cp;
		cam.xo = p.x; cam.yo = y; cam.zo = p.z;
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (cam == null || mc.player == null) return;
		update(mc);
	}
}
