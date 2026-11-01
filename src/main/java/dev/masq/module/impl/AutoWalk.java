// PATH: src/main/java/dev/masq/module/impl/AutoWalk.java
package dev.masq.module.impl;

import dev.masq.module.Category;
import dev.masq.module.Module;
import net.minecraft.client.Minecraft;

public final class AutoWalk extends Module {
	public AutoWalk() { super("AutoWalk", "Hold forward", Category.MOVEMENT); }

	@Override
	public void onTick() { Minecraft.getInstance().options.keyUp.setDown(true); }

	@Override
	protected void onDisable() { Minecraft.getInstance().options.keyUp.setDown(false); }
}
