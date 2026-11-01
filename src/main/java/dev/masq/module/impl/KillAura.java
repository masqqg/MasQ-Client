// PATH: src/main/java/dev/masq/module/impl/KillAura.java
package dev.masq.module.impl;

import dev.masq.module.Category;
import dev.masq.module.Module;
import dev.masq.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class KillAura extends Module {
	private final Setting.Num range = add(new Setting.Num("Range", 4.2f, 1f, 6f, 0.1f));
	private final Setting.Bool players = add(new Setting.Bool("Players", true));
	private final Setting.Bool hostiles = add(new Setting.Bool("Hostiles", true));
	private final Setting.Bool passives = add(new Setting.Bool("Passives", false));
	private final Setting.Bool rotate = add(new Setting.Bool("Rotate", true));
	private final Setting.Bool cooldown = add(new Setting.Bool("Cooldown", true));
	private final Setting.Mode sort = add(new Setting.Mode("Sort", "Distance", "Distance", "Health"));

	public KillAura() { super("KillAura", "Auto attack nearby targets", Category.COMBAT); }

	private boolean valid(LocalPlayer p, Entity e) {
		if (e == p || !(e instanceof LivingEntity le) || !le.isAlive() || le.isSpectator() || e instanceof ArmorStand) return false;
		if (e instanceof Player) return players.get();
		if (e instanceof Enemy) return hostiles.get();
		return passives.get();
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.gameMode == null) return;
		if (cooldown.get() && p.getAttackStrengthScale(0f) < 0.95f) return;

		Entity best = null;
		double bestScore = Double.MAX_VALUE;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!valid(p, e)) continue;
			double d = p.distanceTo(e);
			if (d > range.f()) continue;
			double score = sort.is("Health") ? ((LivingEntity) e).getHealth() : d;
			if (score < bestScore) {
				bestScore = score;
				best = e;
			}
		}
		if (best == null) return;

		if (rotate.get()) {
			Vec3 eye = p.getEyePosition();
			Vec3 t = best.getBoundingBox().getCenter();
			double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
			double h = Math.sqrt(dx * dx + dz * dz);
			p.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
			p.setXRot((float) -Math.toDegrees(Math.atan2(dy, h)));
		}
		mc.gameMode.attack(p, best);
		p.swing(InteractionHand.MAIN_HAND);
	}
}
