package com.awesomeclan;

import com.awesomeclan.api.ApiClient;
import com.awesomeclan.clan.ClanBroadcastTracker;
import com.awesomeclan.clan.ClanChatRelay;
import com.awesomeclan.clan.CofferBalanceReader;
import com.awesomeclan.live.LiveDataTracker;
import com.awesomeclan.notify.NotificationOverlay;
import com.awesomeclan.notify.NotificationPoller;
import com.awesomeclan.roster.RosterCollector;
import com.google.inject.Provides;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "AwesomeClan",
	description = "Keeps the AwesomeClan roster synced with the clan website, pops up clan votes, competitions and event signups, and optionally shows your XP/boss kills live and relays clan chat to the dashboard",
	tags = {"clan", "roster", "awesomeclan"}
)
public class AwesomeClanPlugin extends Plugin
{
	public static final String CLAN_NAME = "AwesomeClan";

	private static final int SYNC_CHECK_MINUTES = 5;
	private static final int SYNC_INTERVAL_MINUTES = 20;
	private static final int SYNC_JITTER_MINUTES = 10;
	private static final int LIVE_FLUSH_PERIOD_SECONDS = 15;
	private static final int CHAT_FLUSH_PERIOD_SECONDS = 5;
	private static final int NOTIFICATION_POLL_SECONDS = 30;

	@Inject
	private Client client;

	@Inject
	private ApiClient api;

	@Inject
	private EventBus eventBus;

	@Inject
	private LiveDataTracker liveDataTracker;

	@Inject
	private ClanBroadcastTracker clanBroadcastTracker;

	@Inject
	private CofferBalanceReader cofferBalanceReader;

	@Inject
	private ClanChatRelay clanChatRelay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private NotificationOverlay notificationOverlay;

	@Inject
	private NotificationPoller notificationPoller;

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
		eventBus.register(clanBroadcastTracker);
		eventBus.register(cofferBalanceReader);
		eventBus.register(clanChatRelay);
		overlayManager.add(notificationOverlay);
	}

	@Override
	protected void shutDown()
	{
		nextSyncAt = null;
		liveDataTracker.flushOnShutdown();
		clanChatRelay.flush();
		eventBus.unregister(liveDataTracker);
		eventBus.unregister(clanBroadcastTracker);
		eventBus.unregister(cofferBalanceReader);
		eventBus.unregister(clanChatRelay);
		overlayManager.remove(notificationOverlay);
		notificationOverlay.clear();
		liveDataTracker.reset();
		clanChatRelay.reset();
	}

	@Schedule(period = LIVE_FLUSH_PERIOD_SECONDS, unit = ChronoUnit.SECONDS)
	public void flushLiveData()
	{
		liveDataTracker.flushPeriodic();
	}

	@Schedule(period = CHAT_FLUSH_PERIOD_SECONDS, unit = ChronoUnit.SECONDS)
	public void flushClanChat()
	{
		clanChatRelay.flush();
	}

	@Schedule(period = NOTIFICATION_POLL_SECONDS, unit = ChronoUnit.SECONDS)
	public void pollNotifications()
	{
		notificationPoller.poll();
	}

	@Schedule(period = SYNC_CHECK_MINUTES, unit = ChronoUnit.MINUTES)
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

		api.post("clan/roster", RosterCollector.collect(clanSettings));
	}

	public static boolean inClan(Client client)
	{
		ClanChannel channel = client.getClanChannel();
		return channel != null && CLAN_NAME.equalsIgnoreCase(channel.getName());
	}

	private static Instant nextSyncTime()
	{
		int jitterSeconds = ThreadLocalRandom.current().nextInt(0, SYNC_JITTER_MINUTES * 60);
		return Instant.now().plusSeconds(SYNC_INTERVAL_MINUTES * 60L + jitterSeconds);
	}
}
