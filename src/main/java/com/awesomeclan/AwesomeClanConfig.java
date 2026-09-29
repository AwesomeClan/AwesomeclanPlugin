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
		description = "Your token from the dashboard profile page. If you play more than one account, put each account's token on its own line. The plugin does nothing without a token."
	)
	default String pluginToken()
	{
		return "";
	}
}
