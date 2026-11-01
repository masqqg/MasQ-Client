// PATH: src/main/java/dev/masq/module/impl/AntiAfk.java
package dev.masq.module.impl;

import dev.masq.module.Category;
import dev.masq.module.Module;
import dev.masq.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

import java.util.Random;

public final class AntiAfk extends Module {
	private final Setting.Num seconds = add(new Setting.Num("Seconds", 15f, 5f, 60f, 1f));
	private final Random rng = new Random();
	private int ticks;
	private boolean jumping;

	public AntiAfk() { super("AntiAFK", "Swing, turn and jump now and then", Category.MISC); }

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) return;
		if (jumping) {
			mc.options.keyJump.setDown(false);
			jumping = false;
		}
		if (++ticks < seconds.i() * 20) return;
		ticks = 0;
		p.swing(InteractionHand.MAIN_HAND);
		p.setYRot(p.getYRot() + (rng.nextBoolean() ? 40f : -40f));
		mc.options.keyJump.setDown(true);
		jumping = true;
	}

	@Override
	protected void onDisable() { Minecraft.getInstance().options.keyJump.setDown(false); }
}
