package com.awesomeclan.notify;

import com.awesomeclan.AwesomeClanConfig;
import java.awt.Color;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;
import lombok.Getter;

// colours match the dashboard's admin notifications page
@Getter
@AllArgsConstructor
public enum NotificationType
{
	VOTE("Vote", new Color(0xF5C542), AwesomeClanConfig::notifyVotes),
	COMPETITION("Competition", new Color(0xFF8C42), AwesomeClanConfig::notifyCompetitions),
	BINGO("Bingo", new Color(0xB07CFF), AwesomeClanConfig::notifyBingo),
	BATTLESHIP("Battleship", new Color(0x3FB6E0), AwesomeClanConfig::notifyBattleship),
	EVENT("Event", new Color(0x5FD068), AwesomeClanConfig::notifyOther),
	ANNOUNCEMENT("Announcement", new Color(0xE8E0D0), AwesomeClanConfig::notifyOther);

	private final String label;
	private final Color color;
	private final Predicate<AwesomeClanConfig> enabled;

	public boolean isEnabled(AwesomeClanConfig config)
	{
		return enabled.test(config);
	}

	public static NotificationType of(String type)
	{
		if (type != null)
		{
			for (NotificationType t : values())
			{
				if (t.name().equalsIgnoreCase(type))
				{
					return t;
				}
			}
		}
		return ANNOUNCEMENT;
	}
}
