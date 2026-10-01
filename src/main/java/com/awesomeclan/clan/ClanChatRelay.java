package com.awesomeclan.clan;

import com.awesomeclan.AwesomeClanConfig;
import com.awesomeclan.AwesomeClanPlugin;
import com.awesomeclan.api.ApiClient;
import com.awesomeclan.util.AccountId;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Singleton
public class ClanChatRelay
{
	private static final int MAX_BATCH = 50;
	private static final int MAX_QUEUED = 200;
	// shown to you on login, not real chat
	private static final String CLAN_CHAT_HINT = "To talk in your clan's channel, start each line of chat with // or /c.";

	@Inject
	private Client client;

	@Inject
	private ApiClient api;

	@Inject
	private AwesomeClanConfig config;

	private final List<RelayedMessage> queue = new ArrayList<>();

	// kept so the last batch after logout still has it
	private String accountId;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.CLAN_CHAT && type != ChatMessageType.CLAN_MESSAGE)
		{
			return;
		}
		if (!config.relayClanChat() || !AwesomeClanPlugin.inClan(client))
		{
			return;
		}

		String message = Text.removeTags(event.getMessage()).trim();
		if (message.isEmpty() || (type == ChatMessageType.CLAN_MESSAGE && message.equalsIgnoreCase(CLAN_CHAT_HINT)))
		{
			return;
		}

		RelayedMessage relayed = type == ChatMessageType.CLAN_CHAT
			? new RelayedMessage("chat", Text.sanitize(event.getName()).trim(), message)
			: new RelayedMessage("broadcast", null, message);

		synchronized (queue)
		{
			accountId = AccountId.of(client.getAccountHash());
			if (queue.size() >= MAX_QUEUED)
			{
				queue.remove(0);
			}
			queue.add(relayed);
		}
	}

	public void flush()
	{
		List<RelayedMessage> batch;
		String batchAccountId;
		synchronized (queue)
		{
			if (queue.isEmpty())
			{
				return;
			}
			int n = Math.min(queue.size(), MAX_BATCH);
			batch = new ArrayList<>(queue.subList(0, n));
			queue.subList(0, n).clear();
			batchAccountId = accountId;
		}

		api.post("clan/chat", new ClanChatBatch(AwesomeClanPlugin.CLAN_NAME, batchAccountId, batch));
	}

	public void reset()
	{
		synchronized (queue)
		{
			queue.clear();
			accountId = null;
		}
	}

	@Value
	private static class RelayedMessage
	{
		String type;
		String sender;
		String message;
	}

	@Value
	private static class ClanChatBatch
	{
		String clanName;
		String accountId;
		List<RelayedMessage> messages;
	}
}
