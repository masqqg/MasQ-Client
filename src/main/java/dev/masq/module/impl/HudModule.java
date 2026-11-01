// PATH: src/main/java/dev/masq/module/impl/HudModule.java
package dev.masq.module.impl;

import dev.masq.module.Category;
import dev.masq.module.Module;
import dev.masq.module.Setting;

public final class HudModule extends Module {
	public static HudModule I;

	public final Setting.Bool watermark = add(new Setting.Bool("Watermark", true));
	public final Setting.Bool list = add(new Setting.Bool("ModuleList", true));
	public final Setting.Bool coords = add(new Setting.Bool("Coords", true));
	public final Setting.Bool intro = add(new Setting.Bool("Intro", true));
	public final Setting.Bool sounds = add(new Setting.Bool("Sounds", true));

	public HudModule() {
		super("Hud", "Overlay and sounds", Category.RENDER);
		I = this;
	}
}
