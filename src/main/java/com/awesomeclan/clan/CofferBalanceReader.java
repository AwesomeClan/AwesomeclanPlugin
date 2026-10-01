package com.awesomeclan.clan;

import com.awesomeclan.AwesomeClanPlugin;
import com.awesomeclan.api.ApiClient;
import javax.inject.Inject;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

// Corrects the coffer total on the site in case a broadcast got missed
public class CofferBalanceReader
{
	@Inject
	private Client client;

	@Inject
	private ApiClient api;

	private int readDelay = -1;

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.CLANS_STORAGE_MAIN)
		{
			// items aren't populated on the tick the widget loads
			readDelay = 2;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (readDelay < 0 || --readDelay > 0)
		{
			return;
		}
		readDelay = -1;

		if (!AwesomeClanPlugin.inClan(client))
		{
			return;
		}

		Widget items = client.getWidget(InterfaceID.ClansStorageMain.ITEMS);
		if (items == null || items.getDynamicChildren() == null)
		{
			return;
		}

		for (Widget child : items.getDynamicChildren())
		{
			if (child.getItemId() == ItemID.COINS)
			{
				api.post("clan/coffer-balance", new CofferBalance(AwesomeClanPlugin.CLAN_NAME, child.getItemQuantity()));
				return;
			}
		}
	}

	@Value
	private static class CofferBalance
	{
		String clanName;
		long balance;
	}
}
