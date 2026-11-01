// PATH: src/main/java/dev/masq/module/impl/Sprint.java
package dev.masq.module.impl;

import dev.masq.module.Category;
import dev.masq.module.Module;
import net.minecraft.client.Minecraft;

public final class Sprint extends Module {
	public Sprint() { super("Sprint", "Always sprint", Category.MOVEMENT); }

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.keyUp.isDown()) mc.options.keySprint.setDown(true);
	}
}
