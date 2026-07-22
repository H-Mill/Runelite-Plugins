package com.mlmqol;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.Text;

/**
 * Puts a name of your choosing over the baby mole. The plugin only ever tracks moles inside the
 * Motherlode Mine, so this cannot follow a pet around the rest of the game.
 */
class BabyMoleOverlay extends Overlay
{
	private static final int NAME_OFFSET = 40;

	private final MlmQolPlugin plugin;
	private final MlmQolConfig config;

	@Inject
	BabyMoleOverlay(MlmQolPlugin plugin, MlmQolConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.nameBabyMole())
		{
			return null;
		}

		String name = Text.removeTags(config.babyMoleName()).trim();
		if (name.isEmpty())
		{
			return null;
		}

		for (NPC mole : plugin.getBabyMoles())
		{
			Point location = mole.getCanvasTextLocation(graphics, name, mole.getLogicalHeight() + NAME_OFFSET);
			if (location != null)
			{
				OverlayUtil.renderTextLocation(graphics, location, name, config.babyMoleNameColor());
			}
		}

		return null;
	}
}
