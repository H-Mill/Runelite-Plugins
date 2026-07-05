package com.hmill;

import com.google.inject.Provides;
import java.util.Arrays;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
		name = "GE Restrictor",
		description = "Restrict your ability to access the Grand Exchange",
		configName = "GrandExchangeRestrictorPlugin"
)
public class GrandExchangeRestrictorPlugin extends Plugin
{
	private static final int GE_GROUP_ID = 465;

	@Inject
	private Client client;

	@Inject
	private GrandExchangeRestrictorConfig config;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Grand Exchange Restrictor started!");

	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// Fallback, if the menu entry doesn't exist, how did you get here?
		if (event.getGroupId() == GE_GROUP_ID)
		{
			if (isRestricted())
			{
				Widget geWidget = client.getWidget(GE_GROUP_ID, 0);
				if (geWidget != null)
				{
					geWidget.setHidden(true);
				}
			}
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!isRestricted())
		{
			return;
		}

		NPC npc = event.getMenuEntry().getNpc();
		boolean isGeClerk = npc != null && "Grand Exchange Clerk".equals(npc.getName());
		boolean isGeBooth = "Grand Exchange booth".equals(Text.removeTags(event.getTarget()));

		if (isGeClerk || isGeBooth)
		{
			client.getMenu().setMenuEntries(Arrays.stream(client.getMenu().getMenuEntries())
				.filter(e -> !e.equals(event.getMenuEntry()))
				.toArray(MenuEntry[]::new));
		}
	}

	private boolean isIronman()
	{
		return client.getVarbitValue(VarbitID.IRONMAN) != 0;
	}

	private boolean isRestricted()
	{
		return (config.restrictIronmen() && isIronman())
			|| (config.restrictMains() && !isIronman());
	}

	@Provides
	GrandExchangeRestrictorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GrandExchangeRestrictorConfig.class);
	}
}
