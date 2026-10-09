package com.awesomeclan.notify;

import com.awesomeclan.AwesomeClanConfig;
import com.awesomeclan.api.ApiClient;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@Singleton
public class NotificationPoller
{
	private static final String CURSOR_KEY = "notificationCursor";

	@Inject
	private Client client;

	@Inject
	private ApiClient api;

	@Inject
	private AwesomeClanConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private NotificationOverlay overlay;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Notifier notifier;

	@Inject
	private ClientThread clientThread;

	private final AtomicBoolean inFlight = new AtomicBoolean();

	public void poll()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		List<String> tokens = api.tokens();
		if (tokens.isEmpty() || !inFlight.compareAndSet(false, true))
		{
			return;
		}

		HttpUrl.Builder url = ApiClient.url("plugin/notifications").newBuilder();
		Long cursor = configManager.getConfiguration(AwesomeClanConfig.GROUP, CURSOR_KEY, Long.class);
		if (cursor != null)
		{
			url.addQueryParameter("after", Long.toString(cursor));
		}

		api.newGet(url.build(), tokens.get(0)).enqueue(new Callback()
		{
			@Override
			public void onFailure(@Nonnull Call call, @Nonnull IOException e)
			{
				inFlight.set(false);
				log.debug("notification poll failed", e);
			}

			@Override
			public void onResponse(@Nonnull Call call, @Nonnull Response response)
			{
				try (response)
				{
					ResponseBody body = response.body();
					if (!response.isSuccessful() || body == null)
					{
						log.debug("notification poll failed with HTTP {}", response.code());
						return;
					}
					handle(gson.fromJson(body.charStream(), ClanNotification.Feed.class));
				}
				catch (RuntimeException e)
				{
					log.warn("bad notification response", e);
				}
				finally
				{
					inFlight.set(false);
				}
			}
		});
	}

	private void handle(ClanNotification.Feed feed)
	{
		if (feed == null)
		{
			return;
		}

		configManager.setConfiguration(AwesomeClanConfig.GROUP, CURSOR_KEY, feed.getCursor());

		if (feed.getNotifications() == null)
		{
			return;
		}

		// notifier posts events other plugins expect on the client thread
		clientThread.invokeLater(() ->
		{
			for (ClanNotification n : feed.getNotifications())
			{
				if (n.kind().isEnabled(config))
				{
					show(n);
				}
			}
		});
	}

	private void show(ClanNotification n)
	{
		if (config.showNotifications())
		{
			overlay.push(n);
		}

		String text = n.getTitle() + (n.getMessage() == null || n.getMessage().isBlank() ? "" : ": " + n.getMessage());

		if (config.notificationChatMessage())
		{
			String chat = new ChatMessageBuilder()
				.append(n.kind().getColor(), "[AwesomeClan] ")
				.append(ChatColorType.NORMAL)
				.append(text)
				.build();
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(chat)
				.build());
		}

		notifier.notify(config.notificationDesktop(), "AwesomeClan: " + text);
	}
}
