package dev.masq.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class ModuleManager {
	private static final List<Module> MODULES = new ArrayList<>();

	/** Modül açılıp kapanınca çağrılır (tick sesi vb. buraya bağlanır). */
	public static Consumer<Module> onToggle = m -> {};

	private ModuleManager() {}

	public static void register(Module m) { MODULES.add(m); }

	public static List<Module> all() { return Collections.unmodifiableList(MODULES); }

	public static List<Module> byCategory(Category c) {
		List<Module> out = new ArrayList<>();
		for (Module m : MODULES) if (m.category() == c) out.add(m);
		return out;
	}

	public static void tick() {
		for (Module m : MODULES) if (m.enabled()) m.onTick();
	}
}
