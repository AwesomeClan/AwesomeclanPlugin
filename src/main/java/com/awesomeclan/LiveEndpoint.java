package com.awesomeclan;

import okhttp3.HttpUrl;

final class LiveEndpoint
{
	static final HttpUrl URL = HttpUrl.parse("https://api.awesomeclan.com/api/live/submit");

	private LiveEndpoint()
	{
	}
}
