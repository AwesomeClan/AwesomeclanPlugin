package com.awesomeclan.live;

import com.awesomeclan.api.ApiClient;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

// Tokens are per account, so with alts we try each until one works and keep it
// until logout. One request at a time, the newest payload waits so logout isn't lost.
@Slf4j
class LiveUploader
{
	@Inject
	private ApiClient api;

	private boolean requestInFlight;
	private LivePayload queuedPayload;
	private CompletableFuture<Void> queuedFuture;
	private String matchedToken;
	private int nextTokenIndex;

	synchronized void reset()
	{
		matchedToken = null;
		nextTokenIndex = 0;
		// don't drop a queued logout
		if (queuedPayload != null && !queuedPayload.isLoggedOut())
		{
			queuedPayload = null;
			queuedFuture.complete(null);
			queuedFuture = null;
		}
	}

	synchronized CompletableFuture<Void> upload(LivePayload payload)
	{
		CompletableFuture<Void> future = new CompletableFuture<>();
		if (requestInFlight)
		{
			if (queuedFuture != null)
			{
				queuedFuture.complete(null);
			}
			queuedPayload = payload;
			queuedFuture = future;
			return future;
		}

		send(payload, future);
		return future;
	}

	private void send(LivePayload payload, CompletableFuture<Void> future)
	{
		String token = matchedToken != null ? matchedToken : nextCandidateToken();
		if (token == null)
		{
			future.complete(null);
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
				future.complete(null);
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
				future.complete(null);
			}
		});
	}

	private synchronized void onRequestFinished()
	{
		requestInFlight = false;
		if (queuedPayload != null)
		{
			LivePayload next = queuedPayload;
			CompletableFuture<Void> nextFuture = queuedFuture;
			queuedPayload = null;
			queuedFuture = null;
			send(next, nextFuture);
		}
	}

	private String nextCandidateToken()
	{
		List<String> tokens = api.tokens();
		if (tokens.isEmpty())
		{
			return null;
		}

		return tokens.get(nextTokenIndex % tokens.size());
	}
}
