package com.awesomeclan;

import okhttp3.HttpUrl;

final class RosterEndpoint
{
	static final HttpUrl URL = HttpUrl.parse("https://api.awesomeclan.com/api/clan/roster");

	private RosterEndpoint()
	{
	}
}
