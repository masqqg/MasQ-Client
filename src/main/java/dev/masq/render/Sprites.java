// PATH: src/main/java/dev/masq/render/Sprites.java
package dev.masq.render;

import net.minecraft.client.gui.GuiGraphics;

/** Amethyst cluster piksel sanatı (ClusterBypass'tan alındı). 16x16, ölçeklenebilir. */
public final class Sprites {
	private static final int[] PALETTE = { 0x7658B0, 0xCC9AF1, 0x8B68CC, 0x60449B, 0xFEC9E5, 0xAF88F2, 0xFEFDD1 };
	private static final String[] CLUSTER = {
		"................",
		".......B........",
		"......BEF.......",
		".....BBECB......",
		"....BBBECCB.....",
		"....BEGGFCB.....",
		"....AFFBACA.....",
		"....ACFBACD.....",
		"EEB..ACBAAD..BEE",
		"BEEB.AAFAAD.BEEB",
		"BCGBBDACFA.BBGCB",
		"BCCEADAAFDCCBCCB",
		".BCABADACDABACB.",
		"..BAAAADCDCAAC..",
		"...CADADBCDAC...",
		"...ADDDCBDDDA..."
	};

	private Sprites() {}

	public static void cluster(GuiGraphics g, int x, int y, int scale) {
		for (int row = 0; row < CLUSTER.length; row++) {
			String line = CLUSTER[row];
			int col = 0;
			while (col < line.length()) {
				char ch = line.charAt(col);
				if (ch == '.') {
					col++;
					continue;
				}
				int end = col;
				while (end < line.length() && line.charAt(end) == ch) end++;
				g.fill(x + col * scale, y + row * scale, x + end * scale, y + (row + 1) * scale, 0xFF000000 | PALETTE[ch - 'A']);
				col = end;
			}
		}
	}
}
