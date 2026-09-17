package com.awesomeclan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;

final class RosterCollector
{
	private static final String DEFAULT_CLAN_NAME = "AwesomeClan";

	private RosterCollector()
	{
	}

	static RosterPayload collect(ClanSettings clanSettings)
	{
		Map<String, Integer> hierarchy = new LinkedHashMap<>();
		for (int i = -1; i <= 127; i++)
		{
			String name = rankName(clanSettings, new ClanRank(i));
			if (name != null)
			{
				hierarchy.put(name, i);
			}
		}

		List<RosterMember> members = new ArrayList<>();
		for (ClanMember member : clanSettings.getMembers())
		{
			String name = normalizeName(member.getName());
			if (name == null || name.isEmpty())
			{
				continue;
			}

			ClanRank rank = member.getRank();
			int rankId = rank == null ? 0 : rank.getRank();
			members.add(new RosterMember(name, rankName(clanSettings, rank), rankId));
		}

		String clanName = normalizeName(clanSettings.getName());
		if (clanName == null || clanName.isEmpty())
		{
			clanName = DEFAULT_CLAN_NAME;
		}

		return new RosterPayload(clanName, members, hierarchy);
	}

	private static String rankName(ClanSettings clanSettings, ClanRank rank)
	{
		if (rank == null)
		{
			return null;
		}

		ClanTitle title = clanSettings.titleForRank(rank);
		if (title != null && title.getName() != null && !title.getName().isEmpty())
		{
			return title.getName();
		}

		if (rank.equals(ClanRank.OWNER))
		{
			return "Owner";
		}
		if (rank.equals(ClanRank.DEPUTY_OWNER))
		{
			return "Deputy Owner";
		}
		if (rank.equals(ClanRank.ADMINISTRATOR))
		{
			return "Administrator";
		}
		if (rank.equals(ClanRank.JMOD))
		{
			return "Jagex Moderator";
		}
		if (rank.equals(ClanRank.GUEST))
		{
			return "Guest";
		}

		return "Rank " + rank.getRank();
	}

	private static String normalizeName(String name)
	{
		if (name == null)
		{
			return null;
		}

		String normalized = name.replace(' ', ' ').replace('�', ' ').trim();
		return normalized.replaceAll("\\s+", " ");
	}
}
