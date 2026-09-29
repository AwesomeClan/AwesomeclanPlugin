package com.awesomeclan.live;

import com.awesomeclan.AwesomeClanConfig;
import com.awesomeclan.api.ApiClient;
import com.awesomeclan.api.PluginTokens;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Each token belongs to one account and the server rejects the others, so
 * with several tokens configured we try them in turn until one is accepted,
 * then stick with it until logout.
 *
 * Only one request runs at a time. If another payload comes in meanwhile we
 * keep the newest one and send it after, so the logout payload doesn't get
 * dropped.
 */
@Slf4j
class LiveUploader
{
	@Inject
	private ApiClient api;

	@Inject
	private AwesomeClanConfig config;

	private boolean requestInFlight;
	private LivePayload queuedPayload;
	private String matchedToken;
	private int nextTokenIndex;

	synchronized void reset()
	{
		matchedToken = null;
		nextTokenIndex = 0;
		queuedPayload = null;
	}

	synchronized void upload(LivePayload payload)
	{
		if (requestInFlight)
		{
			queuedPayload = payload;
			return;
		}

		send(payload);
	}

	private void send(LivePayload payload)
	{
		String token = matchedToken != null ? matchedToken : nextCandidateToken();
		if (token == null)
		{
			return;
		}

		requestInFlight = true;
		api.newCall("live/submit", payload, token).enqueue(new Callback()
		{
			@Override
			public void onFailure(@Nonnull Call call, @Nonnull IOException e)
			{
				log.warn("Live data submit failed", e);
				onRequestFinished();
			}

			@Override
			public void onResponse(@Nonnull Call call, @Nonnull Response response)
			{
				response.close();
				synchronized (LiveUploader.this)
				{
					if (response.isSuccessful())
					{
						matchedToken = token;
					}
					else if (matchedToken == null)
					{
						nextTokenIndex++;
					}
				}

				if (!response.isSuccessful())
				{
					log.warn("Live data submit failed with HTTP {}", response.code());
				}
				onRequestFinished();
			}
		});
	}

	private synchronized void onRequestFinished()
	{
		requestInFlight = false;
		if (queuedPayload != null)
		{
			LivePayload next = queuedPayload;
			queuedPayload = null;
			send(next);
		}
	}

	private String nextCandidateToken()
	{
		List<String> tokens = PluginTokens.parse(config.pluginToken());
		if (tokens.isEmpty())
		{
			return null;
		}

		return tokens.get(nextTokenIndex % tokens.size());
	}
}
