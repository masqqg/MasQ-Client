// PATH: src/main/java/dev/masq/module/impl/Modules.java
package dev.masq.module.impl;

import dev.masq.module.ModuleManager;

/** Tüm yerleşik modüllerin kaydı. Yeni modülü buraya bir satırla ekle. */
public final class Modules {
	private Modules() {}

	public static void registerAll() {
		HudModule hud = new HudModule();
		ModuleManager.register(hud);
		ModuleManager.register(new Sprint());
		ModuleManager.register(new Flight());
		ModuleManager.register(new Speed());
		ModuleManager.register(new AutoWalk());
		ModuleManager.register(new KillAura());
		ModuleManager.register(new Fullbright());
		ModuleManager.register(new Esp());
		ModuleManager.register(new AntiAfk());
		ModuleManager.register(new Freecam());
		ModuleManager.register(new FreeLook());
		ModuleManager.register(new ChunkHunter());   // ClearRender'dan önce: önce tespit
		ModuleManager.register(new ClearRender());
		hud.set(true);
	}
}
