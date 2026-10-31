// PATH: src/main/java/dev/masq/module/Config.java
package dev.masq.module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Ayarları config/masq.properties içine yazar. Modüllerin açık/kapalı durumu kaydedilmez (güvenlik). */
public final class Config {
	private Config() {}

	public static void load(Path p) {
		Properties pr = new Properties();
		try (var in = Files.newInputStream(p)) {
			pr.load(in);
		} catch (IOException e) {
			return;
		}
		for (Module m : ModuleManager.all()) {
			for (Setting<?> s : m.settings()) {
				String v = pr.getProperty(m.name() + "." + s.name());
				if (v == null) continue;
				try {
					s.de(v);
				} catch (RuntimeException ignored) {}
			}
		}
	}

	public static void save(Path p) {
		Properties pr = new Properties();
		for (Module m : ModuleManager.all()) {
			for (Setting<?> s : m.settings()) pr.setProperty(m.name() + "." + s.name(), s.ser());
		}
		try (var out = Files.newOutputStream(p)) {
			pr.store(out, "MasQ");
		} catch (IOException ignored) {}
	}
}
