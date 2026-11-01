// PATH: src/main/java/dev/masq/module/impl/Flight.java
package dev.masq.module.impl;

import dev.masq.module.Category;
import dev.masq.module.Module;
import dev.masq.module.Setting;
import dev.masq.util.Move;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class Flight extends Module {
	private final Setting.Num speed = add(new Setting.Num("Speed", 0.6f, 0.1f, 3f, 0.1f));
	private final Setting.Num vertical = add(new Setting.Num("Vertical", 0.4f, 0.1f, 2f, 0.1f));
	private final Setting.Bool antiKick = add(new Setting.Bool("AntiKick", true));
	private int ticks;

	public Flight() { super("Flight", "Velocity based flight", Category.MOVEMENT); }

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) return;
		double[] d = Move.dir(mc, speed.f());
		double vy = mc.options.keyJump.isDown() ? vertical.f() : mc.options.keyShift.isDown() ? -vertical.f() : 0;
		if (antiKick.get() && vy == 0 && ++ticks % 20 == 0) vy = -0.04;
		p.setDeltaMovement(d[0], vy, d[1]);
	}
}
