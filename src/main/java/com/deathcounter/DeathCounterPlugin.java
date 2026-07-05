package com.deathcounter;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ActorDeath;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
		name = "Death Counter",
		description = "Tracks how many times you have died and shows the total in a movable overlay",
		tags = {"death", "counter", "deaths", "overlay", "tracker"},
		configName = "DeathCounterPlugin"
)
public class DeathCounterPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DeathCounterOverlay overlay;

	@Getter
	private int deaths;

	@Override
	protected void startUp() throws Exception
	{
		deaths = loadDeaths();
		overlayManager.add(overlay);
		log.debug("Death Counter started with {} deaths", deaths);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		log.debug("Death Counter stopped");
	}

	@Subscribe
	public void onActorDeath(ActorDeath actorDeath)
	{
		if (actorDeath.getActor() == client.getLocalPlayer())
		{
			deaths++;
			saveDeaths(deaths);
			log.debug("Local player died, death count now {}", deaths);
		}
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		if (event.getOverlay() == overlay
			&& DeathCounterOverlay.RESET_OPTION.equals(event.getEntry().getOption()))
		{
			resetDeaths();
		}
	}

	private void resetDeaths()
	{
		deaths = 0;
		saveDeaths(0);
		log.debug("Death count reset to 0");
	}

	private int loadDeaths()
	{
		Integer stored = configManager.getConfiguration(
			DeathCounterConfig.GROUP, DeathCounterConfig.DEATHS_KEY, int.class);
		return stored == null ? 0 : stored;
	}

	private void saveDeaths(int value)
	{
		configManager.setConfiguration(DeathCounterConfig.GROUP, DeathCounterConfig.DEATHS_KEY, value);
	}

	@Provides
	DeathCounterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DeathCounterConfig.class);
	}
}
