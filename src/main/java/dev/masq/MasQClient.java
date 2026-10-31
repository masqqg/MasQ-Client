// PATH: src/main/java/dev/masq/MasQClient.java
package dev.masq;

import com.mojang.blaze3d.platform.InputConstants;
import dev.masq.module.Config;
import dev.masq.module.Module;
import dev.masq.module.ModuleManager;
import dev.masq.module.impl.Modules;
import dev.masq.ui.ClickGui;
import dev.masq.ui.Hud;
import dev.masq.world.ChunkScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;

public final class MasQClient implements ClientModInitializer {
	public static final String ID = "masq";
	private static Path cfg;

	public static void saveConfig() {
		if (cfg != null) Config.save(cfg);
	}

	@Override
	public void onInitializeClient() {
		Modules.registerAll();
		cfg = FabricLoader.getInstance().getConfigDir().resolve("masq.properties");
		Config.load(cfg);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
		KeyMapping guiKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.masq.gui", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, category));
		Binds.register(category);

		ModuleManager.onToggle = m -> Sfx.tick(m.enabled() ? 1.5f : 0.9f);
		for (Module m : ModuleManager.all()) {
			if (m instanceof ChunkScanner.Pass p) ChunkScanner.register(p);
		}

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(ID, "hud"), Hud::render);

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (guiKey.consumeClick()) {
				if (mc.screen == null) mc.setScreen(new ClickGui());
			}
			Binds.tick();
			ChunkScanner.tick(mc);
			if (mc.player != null) ModuleManager.tick();
		});
	}
}
