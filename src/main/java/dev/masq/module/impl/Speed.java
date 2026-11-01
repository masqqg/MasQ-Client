// PATH: src/main/java/dev/masq/module/impl/Speed.java
package dev.masq.module.impl;

import dev.masq.module.Category;
import dev.masq.module.Module;
import dev.masq.module.Setting;
import dev.masq.util.Move;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class Speed extends Module {
	private final Setting.Num speed = add(new Setting.Num("Speed", 0.4f, 0.1f, 2f, 0.05f));
	private final Setting.Bool hop = add(new Setting.Bool("Hop", true));

	public Speed() { super("Speed", "Ground speed / bunny hop", Category.MOVEMENT); }

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || !Move.moving(mc)) return;
		double[] d = Move.dir(mc, speed.f());
		double vy = p.getDeltaMovement().y;
		if (hop.get() && p.onGround()) vy = 0.42;
		p.setDeltaMovement(d[0], vy, d[1]);
	}
}
