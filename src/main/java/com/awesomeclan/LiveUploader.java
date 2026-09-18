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

/**
 * Multiple accounts can each have their own token configured (comma-separated
 * in {@link AwesomeClanConfig#pluginToken()}); the backend ties a token to one
 * RSN and 403s any other, so rather than asking the user to map RSN -> token
 * themselves, one untried candidate is sent per flush until one is accepted.
 * The winner is then cached for the rest of the login session. {@link #reset()}
 * clears that cache and is called whenever the tracked session ends (logout,
 * plugin shutdown) so the next login re-probes from the top of the list.
 */
@Slf4j
class LiveUploader
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	@Inject
	private Gson gson;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private AwesomeClanConfig config;

	private volatile boolean requestInFlight;
	private String matchedToken;
	private int nextTokenIndex;

	void reset()
	{
		matchedToken = null;
		nextTokenIndex = 0;
	}

	void upload(LivePayload payload)
	{
		if (requestInFlight)
		{
			return;
		}

		String token = matchedToken != null ? matchedToken : nextCandidateToken();
		if (token == null)
		{
			// No token configured -- live data is opt-in, so members who
			// never open the config panel send nothing at all.
			return;
		}

		Request request = new Request.Builder()
			.url(LiveEndpoint.URL)
			.post(RequestBody.create(JSON, gson.toJson(payload).getBytes(StandardCharsets.UTF_8)))
			.header("Content-Type", JSON.toString())
			.header("Accept", "application/json")
			.header("Authorization", "Bearer " + token)
			.build();

		requestInFlight = true;
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(@Nonnull Call call, @Nonnull IOException e)
			{
				requestInFlight = false;
				log.warn("Live data submit failed", e);
			}

			@Override
			public void onResponse(@Nonnull Call call, @Nonnull Response response)
			{
				requestInFlight = false;
				try (ResponseBody ignored = response.body())
				{
					if (!response.isSuccessful())
					{
						if (matchedToken == null)
						{
							nextTokenIndex++;
						}
						log.warn("Live data submit failed with HTTP {}", response.code());
						return;
					}

					matchedToken = token;
					log.debug("Live data submitted ({} skills, {} bosses)", payload.getSkills().size(), payload.getBosses().size());
				}
			}
		});
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
