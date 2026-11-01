// PATH: src/main/java/dev/masq/module/impl/Fullbright.java
package dev.masq.module.impl;

import dev.masq.module.Category;
import dev.masq.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class Fullbright extends Module {
	public Fullbright() { super("Fullbright", "Client side night vision", Category.RENDER); }

	@Override
	public void onTick() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p != null && !p.hasEffect(MobEffects.NIGHT_VISION)) {
			p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		}
	}

	@Override
	protected void onDisable() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p != null) p.removeEffect(MobEffects.NIGHT_VISION);
	}
}
