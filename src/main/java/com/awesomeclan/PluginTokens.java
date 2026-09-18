package com.awesomeclan;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class PluginTokens
{
	private PluginTokens()
	{
	}

	static List<String> parse(String raw)
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
