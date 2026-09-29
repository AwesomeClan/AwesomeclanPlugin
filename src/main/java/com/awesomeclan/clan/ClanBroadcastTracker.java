package com.awesomeclan.clan;

import com.awesomeclan.AwesomeClanPlugin;
import com.awesomeclan.api.ApiClient;
import com.awesomeclan.util.AccountId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Reports clan broadcasts for kicks, recruits and coffer deposits/withdrawals.
 * Every online member gets these, so they're picked up even if the person
 * doing the kicking/depositing doesn't run the plugin.
 */
@Slf4j
public class ClanBroadcastTracker
{
	private static final Pattern KICK = Pattern.compile("^(.+?) has expelled (.+?) from the clan\\.$");
	private static final Pattern RECRUIT = Pattern.compile("^(.+?) has been invited into the clan by (.+?)\\.?$");
	private static final Pattern DEPOSIT = Pattern.compile("^(.+?) has deposited ([0-9,]+) coins? into the coffer\\.$");
	private static final Pattern WITHDRAW = Pattern.compile("^(.+?) has withdrawn ([0-9,]+) coins? from the coffer\\.$");

	@Inject
	private Client client;

	@Inject
	private ApiClient api;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.CLAN_MESSAGE || !AwesomeClanPlugin.inClan(client))
		{
			return;
		}

		String message = Text.removeTags(event.getMessage()).trim();
		Matcher m;
		if ((m = KICK.matcher(message)).matches())
		{
			api.post("clan/kick", new Kick(AwesomeClanPlugin.CLAN_NAME, m.group(2).trim(), m.group(1).trim()));
		}
		else if ((m = RECRUIT.matcher(message)).matches())
		{
			api.post("clan/recruit", new Recruit(AwesomeClanPlugin.CLAN_NAME, m.group(1).trim(), m.group(2).trim()));
		}
		else if ((m = DEPOSIT.matcher(message)).matches())
		{
			reportCoffer(m, "deposit");
		}
		else if ((m = WITHDRAW.matcher(message)).matches())
		{
			reportCoffer(m, "withdraw");
		}
	}

	private void reportCoffer(Matcher m, String type)
	{
		long amount;
		try
		{
			amount = Long.parseLong(m.group(2).replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			log.debug("Bad coffer amount: {}", m.group(2));
			return;
		}

		// Everyone online reports the same broadcast. The server uses the
		// account ID to count it once.
		String accountId = AccountId.of(client.getAccountHash());
		api.post("clan/coffer", new CofferTransaction(AwesomeClanPlugin.CLAN_NAME, accountId, m.group(1).trim(), amount, type));
	}

	@Value
	private static class Kick
	{
		String clanName;
		String kickedName;
		String kickedBy;
	}

	@Value
	private static class Recruit
	{
		String clanName;
		String recruitedName;
		String recruitedBy;
	}

	@Value
	private static class CofferTransaction
	{
		String clanName;
		String accountId;
		String memberName;
		long amount;
		String type;
	}
}
