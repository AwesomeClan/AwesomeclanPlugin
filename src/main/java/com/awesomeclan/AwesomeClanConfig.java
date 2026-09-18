package com.awesomeclan;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("awesomeclan")
public interface AwesomeClanConfig extends Config
{
	@ConfigItem(
		keyName = "pluginToken",
		name = "Plugin tokens",
		description = "Paste a personal token from the dashboard's account page (Generate token) - used for both roster sync and live XP/boss-kill data. Playing multiple accounts? Put one token per line, one per account (e.g. main on the first line, each alt on its own line below) - the plugin figures out which token matches whichever character is logged in. Leave blank to disable live data (roster sync still needs at least one token)."
	)
	default String pluginToken()
	{
		return "";
	}
}
