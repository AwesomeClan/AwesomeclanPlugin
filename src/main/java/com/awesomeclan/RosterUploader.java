package com.awesomeclan;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
class RosterUploader
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	@Inject
	private Gson gson;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private AwesomeClanConfig config;

	private volatile boolean requestInFlight;

	void upload(RosterPayload payload)
	{
		// Roster sync isn't tied to a specific character the way live data is,
		// so any one configured token proves clan membership -- just use the
		// first. The server requires this same per-member token; without one
		// configured there's nothing to authenticate with, so skip the
		// request entirely rather than send one we know will fail auth.
		List<String> tokens = PluginTokens.parse(config.pluginToken());
		if (tokens.isEmpty())
		{
			return;
		}

		if (requestInFlight)
		{
			return;
		}

		Request request = new Request.Builder()
			.url(RosterEndpoint.URL)
			.post(RequestBody.create(JSON, gson.toJson(payload).getBytes(StandardCharsets.UTF_8)))
			.header("Content-Type", JSON.toString())
			.header("Accept", "application/json")
			.header("Authorization", "Bearer " + tokens.get(0))
			.build();

		requestInFlight = true;
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(@Nonnull Call call, @Nonnull IOException e)
			{
				requestInFlight = false;
				log.warn("Clan roster sync failed", e);
			}

			@Override
			public void onResponse(@Nonnull Call call, @Nonnull Response response)
			{
				requestInFlight = false;
				try (ResponseBody ignored = response.body())
				{
					if (!response.isSuccessful())
					{
						log.warn("Clan roster sync failed with HTTP {}", response.code());
						return;
					}

					log.debug("Clan roster synced ({} members)", payload.getMembers().size());
				}
			}
		});
	}
}
