// PATH: src/main/java/dev/masq/util/Move.java
package dev.masq.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

public final class Move {
	private Move() {}

	public static boolean moving(Minecraft mc) {
		Options o = mc.options;
		return o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown();
	}

	/** Tuşlara göre yatay hız vektörü {x, z} (blok/tick). Tuş yoksa {0, 0}. */
	public static double[] dir(Minecraft mc, double speed) {
		Options o = mc.options;
		double f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
		double s = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
		if (f == 0 && s == 0) return new double[] { 0, 0 };
		double len = Math.sqrt(f * f + s * s);
		f /= len;
		s /= len;
		double yaw = Math.toRadians(mc.player.getYRot());
		return new double[] {
			(-Math.sin(yaw) * f + Math.cos(yaw) * s) * speed,
			(Math.cos(yaw) * f + Math.sin(yaw) * s) * speed
		};
	}
}
