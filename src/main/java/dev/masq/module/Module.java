// PATH: src/main/java/dev/masq/module/Module.java
package dev.masq.module;

import dev.masq.anim.Spring;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
	/** GUI doluş animasyonu (0..1). Sadece ClickGui tick'ler. */
	public final Spring fill = Spring.snappy();

	private final String name, desc;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean on;

	protected Module(String name, String desc, Category category) {
		this.name = name;
		this.desc = desc;
		this.category = category;
	}

	protected <S extends Setting<?>> S add(S s) {
		settings.add(s);
		return s;
	}

	public void toggle() { set(!on); }

	public void set(boolean v) {
		if (on == v) return;
		on = v;
		fill.set(v ? 1f : 0f);
		if (v) onEnable(); else onDisable();
		ModuleManager.onToggle.accept(this);
	}

	protected void onEnable() {}
	protected void onDisable() {}
	public void onTick() {}

	public List<Setting<?>> settings() { return settings; }
	public boolean enabled() { return on; }
	public String name() { return name; }
	public String desc() { return desc; }
	public Category category() { return category; }
}
