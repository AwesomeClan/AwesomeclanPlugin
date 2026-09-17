package com.awesomeclan;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import okhttp3.HttpUrl;

final class RosterEndpoint
{
	// Base64'd so it isn't a plain grep-able string. This repo is public, so treat this
	// as a light speed bump, not a secret - anyone can decode it from source in seconds.
	private static final String ENCODED = "aHR0cHM6Ly9hcGkuYXdlc29tZWNsYW4uY29tL2FwaS9jbGFuL3Jvc3Rlcg==";

	static final HttpUrl URL = HttpUrl.parse(new String(Base64.getDecoder().decode(ENCODED), StandardCharsets.UTF_8));

	private RosterEndpoint()
	{
	}
}
