// PATH: src/main/java/dev/masq/module/impl/Esp.java
package dev.masq.module.impl;

import dev.masq.module.Category;
import dev.masq.module.Module;
import dev.masq.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Glow tabanlı ESP: entity'ye client tarafında parlama bayrağı koyar. */
public final class Esp extends Module {
	private final Setting.Bool players = add(new Setting.Bool("Players", true));
	private final Setting.Bool hostiles = add(new Setting.Bool("Hostiles", true));
	private final Setting.Bool passives = add(new Setting.Bool("Passives", false));
	private final Set<Entity> touched = Collections.newSetFromMap(new IdentityHashMap<>());

	public Esp() { super("ESP", "Glow outline through walls", Category.RENDER); }

	private boolean want(Entity e) {
		if (e == Minecraft.getInstance().player || !(e instanceof LivingEntity)) return false;
		if (e instanceof Player) return players.get();
		if (e instanceof Enemy) return hostiles.get();
		return passives.get();
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (want(e)) {
				e.setGlowingTag(true);
				touched.add(e);
			} else if (touched.remove(e)) {
				e.setGlowingTag(false);
			}
		}
	}

	@Override
	protected void onDisable() {
		for (Entity e : touched) e.setGlowingTag(false);
		touched.clear();
	}
}
