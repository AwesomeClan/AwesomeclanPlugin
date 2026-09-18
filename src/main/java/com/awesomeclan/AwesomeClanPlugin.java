package com.awesomeclan;

import com.google.inject.Provides;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;

@PluginDescriptor(
	name = "AwesomeClan",
	description = "Keeps the AwesomeClan roster synced with the clan website, and optionally shows your XP/boss kills live on the dashboard while you play",
	tags = {"clan", "roster", "awesomeclan"}
)
public class AwesomeClanPlugin extends Plugin
{
	private static final String CLAN_NAME = "AwesomeClan";
	private static final int SYNC_INTERVAL_MINUTES = 20;
	private static final int SYNC_JITTER_MINUTES = 10;
	private static final int LIVE_FLUSH_PERIOD_SECONDS = 15;

	@Inject
	private Client client;

	@Inject
	private RosterUploader uploader;

	@Inject
	private EventBus eventBus;

	@Inject
	private LiveDataTracker liveDataTracker;

	private Instant nextSyncAt;

	@Provides
	AwesomeClanConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AwesomeClanConfig.class);
	}

	@Override
	protected void startUp()
	{
		nextSyncAt = nextSyncTime();
		eventBus.register(liveDataTracker);
	}

	@Override
	protected void shutDown()
	{
		nextSyncAt = null;
		eventBus.unregister(liveDataTracker);
		liveDataTracker.reset();
	}

	@Schedule(period = LIVE_FLUSH_PERIOD_SECONDS, unit = ChronoUnit.SECONDS)
	public void flushLiveData()
	{
		liveDataTracker.flushPeriodic();
	}

	@Schedule(period = 5, unit = ChronoUnit.MINUTES)
	public void trySync()
	{
		if (nextSyncAt == null || Instant.now().isBefore(nextSyncAt))
		{
			return;
		}

		nextSyncAt = nextSyncTime();

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		ClanSettings clanSettings = client.getClanSettings();
		if (clanSettings == null || clanSettings.getMembers() == null || clanSettings.getMembers().isEmpty())
		{
			return;
		}

		if (!CLAN_NAME.equalsIgnoreCase(clanSettings.getName()))
		{
			return;
		}

		uploader.upload(RosterCollector.collect(clanSettings));
	}

	private static Instant nextSyncTime()
	{
		int jitterSeconds = ThreadLocalRandom.current().nextInt(0, SYNC_JITTER_MINUTES * 60);
		return Instant.now().plusSeconds(SYNC_INTERVAL_MINUTES * 60L + jitterSeconds);
	}
}
