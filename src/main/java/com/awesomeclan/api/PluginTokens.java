package com.awesomeclan.api;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class PluginTokens
{
	private PluginTokens()
	{
	}

	public static List<String> parse(String raw)
	{
		if (raw == null)
		{
			return List.of();
		}

		return Arrays.stream(raw.split("\\r?\\n"))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toList());
	}
}
