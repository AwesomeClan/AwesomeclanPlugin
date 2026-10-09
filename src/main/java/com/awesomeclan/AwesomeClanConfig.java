package com.awesomeclan;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(AwesomeClanConfig.GROUP)
public interface AwesomeClanConfig extends Config
{
	String GROUP = "awesomeclan";

	@ConfigSection(
		name = "Notifications",
		description = "Pop-ups for new votes, competitions and event signups",
		position = 10
	)
	String notificationsSection = "notifications";

	@ConfigItem(
		keyName = "pluginToken",
		name = "Plugin tokens",
		description = "Your token from the dashboard profile page. If you play more than one account, put each account's token on its own line. The plugin does nothing without a token.",
		position = 0
	)
	default String pluginToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "relayClanChat",
		name = "Relay clan chat",
		description = "Send clan chat and clan broadcasts to the dashboard",
		warning = "This sends all AwesomeClan clan chat you see, including other members' messages, to the dashboard where staff can read it. Public chat and PMs are never sent. Turn it on?",
		position = 1
	)
	default boolean relayClanChat()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showNotifications",
		name = "Show notifications",
		description = "Show a pop-up in game when the clan starts a vote, competition or event signup",
		section = notificationsSection,
		position = 0
	)
	default boolean showNotifications()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notificationDuration",
		name = "Pop-up duration",
		description = "How long each pop-up stays on screen",
		section = notificationsSection,
		position = 1
	)
	@Range(min = 3, max = 30)
	@Units(Units.SECONDS)
	default int notificationDuration()
	{
		return 6;
	}

	@ConfigItem(
		keyName = "notificationChatMessage",
		name = "Chat message",
		description = "Also put the notification in your chatbox",
		section = notificationsSection,
		position = 2
	)
	default boolean notificationChatMessage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notificationDesktop",
		name = "Desktop notification",
		description = "Also send a RuneLite desktop notification",
		section = notificationsSection,
		position = 3
	)
	default Notification notificationDesktop()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "notifyVotes",
		name = "Votes",
		description = "New clan and competition votes",
		section = notificationsSection,
		position = 4
	)
	default boolean notifyVotes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyCompetitions",
		name = "Competitions",
		description = "When a clan competition starts",
		section = notificationsSection,
		position = 5
	)
	default boolean notifyCompetitions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyBingo",
		name = "Bingo",
		description = "When bingo signups open",
		section = notificationsSection,
		position = 6
	)
	default boolean notifyBingo()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyBattleship",
		name = "Battleship",
		description = "When battleship signups open",
		section = notificationsSection,
		position = 7
	)
	default boolean notifyBattleship()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyOther",
		name = "Events & announcements",
		description = "Mass events and other clan announcements",
		section = notificationsSection,
		position = 8
	)
	default boolean notifyOther()
	{
		return true;
	}
}
