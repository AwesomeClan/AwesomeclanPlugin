package com.awesomeclan.api;

import com.awesomeclan.AwesomeClanConfig;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@Singleton
public class ApiClient
{
	private static final HttpUrl BASE_URL = HttpUrl.parse("https://api.awesomeclan.com/api/");
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	@Inject
	private Gson gson;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private AwesomeClanConfig config;

	// clan-wide stuff, any of the tokens works so just use the first
	public void post(String path, Object payload)
	{
		List<String> tokens = PluginTokens.parse(config.pluginToken());
		if (tokens.isEmpty())
		{
			return;
		}

		newCall(path, payload, tokens.get(0)).enqueue(new Callback()
		{
			@Override
			public void onFailure(@Nonnull Call call, @Nonnull IOException e)
			{
				log.warn("POST {} failed", path, e);
			}

			@Override
			public void onResponse(@Nonnull Call call, @Nonnull Response response)
			{
				response.close();
				if (!response.isSuccessful())
				{
					log.warn("POST {} failed with HTTP {}", path, response.code());
				}
			}
		});
	}

	public Call newCall(String path, Object payload, String token)
	{
		Request request = new Request.Builder()
			.url(BASE_URL.resolve(path))
			.post(RequestBody.create(JSON, gson.toJson(payload)))
			.header("Authorization", "Bearer " + token)
			.build();
		return okHttpClient.newCall(request);
	}
}
