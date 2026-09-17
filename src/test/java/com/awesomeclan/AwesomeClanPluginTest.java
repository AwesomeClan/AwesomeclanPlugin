package com.awesomeclan;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class AwesomeClanPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(AwesomeClanPlugin.class);
		RuneLite.main(args);
	}
}
