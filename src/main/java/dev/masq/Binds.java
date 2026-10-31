// PATH: src/main/java/dev/masq/Binds.java
package dev.masq;

import com.mojang.blaze3d.platform.InputConstants;
import dev.masq.module.Module;
import dev.masq.module.ModuleManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;

/** Her modül için bir KeyMapping: tuşlar options.txt'ye kaydedilir, Controls menüsünde de görünür. */
public final class Binds {
	private static final Map<Module, KeyMapping> MAP = new LinkedHashMap<>();

	private Binds() {}

	public static void register(KeyMapping.Category category) {
		for (Module m : ModuleManager.all()) {
			String id = "key.masq.mod." + m.name().toLowerCase();
			MAP.put(m, KeyBindingHelper.registerKeyBinding(new KeyMapping(id, InputConstants.Type.KEYSYM, -1, category)));
		}
	}

	public static void set(Module m, int key) {
		KeyMapping km = MAP.get(m);
		if (km == null) return;
		km.setKey(key < 0 ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(key));
		KeyMapping.resetMapping();
		Minecraft.getInstance().options.save();
	}

	public static String label(Module m) {
		KeyMapping km = MAP.get(m);
		return km == null || km.isUnbound() ? "" : km.getTranslatedKeyMessage().getString();
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		for (Map.Entry<Module, KeyMapping> e : MAP.entrySet()) {
			while (e.getValue().consumeClick()) {
				if (mc.screen == null) e.getKey().toggle();
			}
		}
	}
}
