package com.deathcounter;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
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
	private DeathCounterConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DeathCounterOverlay overlay;

	@Getter
	private int deaths;

	// True once we've seen LOGGING_IN, so the following LOGGED_IN is a real login and not a world hop.
	private boolean loginPending;

	// Armed on a client-open reset so the first login afterwards doesn't reset the counter a second time.
	private boolean skipNextLoginReset;

	@Override
	protected void startUp() throws Exception
	{
		deaths = loadDeaths();
		loginPending = false;
		skipNextLoginReset = false;
		overlayManager.add(overlay);
		log.debug("Death Counter started with {} deaths", deaths);

		if (config.resetOnClientOpen())
		{
			resetDeaths();
			// A client start always follows a logout, so suppress the next login reset to avoid a double reset.
			skipNextLoginReset = config.resetOnLogin();
			log.debug("Death count reset on client open");
		}
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
	public void onCommandExecuted(CommandExecuted event)
	{
		// Dev helper: "::dcIncrement" bumps the counter without needing to actually die.
		if ("dcIncrement".equalsIgnoreCase(event.getCommand()))
		{
			deaths++;
			saveDeaths(deaths);
			log.debug("::dcIncrement, death count now {}", deaths);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGING_IN:
				// Marks an actual login attempt; world hops go through HOPPING instead, so they won't trigger a reset.
				loginPending = true;
				break;
			case LOGGED_IN:
				if (loginPending)
				{
					loginPending = false;
					handleLogin();
				}
				break;
			default:
				break;
		}
	}

	private void handleLogin()
	{
		if (!config.resetOnLogin())
		{
			return;
		}

		if (skipNextLoginReset)
		{
			skipNextLoginReset = false;
			log.debug("Skipping login reset; counter was already reset on client open");
			return;
		}

		resetDeaths();
		log.debug("Death count reset on login");
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
