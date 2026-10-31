// PATH: src/main/java/dev/masq/Sfx.java
package dev.masq;

import dev.masq.module.impl.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** UI tick sesleri (modül aç/kapa, GUI tıkları). Hud > Sounds ile kapatılır. */
public final class Sfx {
	private Sfx() {}

	public static void tick(float pitch) {
		if (HudModule.I != null && !HudModule.I.sounds.get()) return;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
	}
}
