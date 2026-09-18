package com.awesomeclan;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("awesomeclan")
public interface AwesomeClanConfig extends Config
{
	@ConfigItem(
		keyName = "pluginToken",
		name = "Live data token(s)",
		description = "Paste a personal token from the dashboard's account page (Generate token) to show your XP and boss kills live while you play. Playing multiple accounts? Generate one token per account and separate them with commas - the plugin figures out which token matches whichever character is logged in. Leave blank to disable."
	)
	default String pluginToken()
	{
		return "";
	}
}
