package com.awesomeclan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Watches XP and boss kill-count changes while logged in and hands batched
 * updates to {@link LiveUploader} every ~15s (via {@link #flushPeriodic()},
 * called from AwesomeClanPlugin's own {@code @Schedule} method) plus an
 * immediate flush on logout. Registered on/unregistered from the EventBus by
 * AwesomeClanPlugin's startUp()/shutDown() -- this class does not manage its
 * own subscription.
 */
@Slf4j
class LiveDataTracker
{
	// A handful of skills where RuneLite's Skill enum display name doesn't
	// match the server's canonical hiscore category name (see SKILL_NAMES in
	// the server's app/services/hiscore_service.py). The server silently
	// drops anything it doesn't recognise, so a missing/wrong alias here
	// just means that skill never shows up live -- not a hard failure -- but
	// keeping this in sync avoids that.
	private static final Map<String, String> SKILL_NAME_OVERRIDES = new LinkedHashMap<>();
	static
	{
		SKILL_NAME_OVERRIDES.put("Runecraft", "Runecrafting");
	}

	// Matches the family of OSRS kill-count game messages, e.g.:
	//   "Your Zulrah kill count is: 412."
	//   "Your completed Barrows Chests count is: 100."
	//   "Your Chambers of Xeric completion count is: 5."
	// The one qualifier word ("kill"/"completed"/"completion"/...) can land
	// either right after "Your" or right before "count is:" depending on the
	// boss/activity, so both slots are optional single words and the name
	// itself is captured non-greedily in between.
	private static final Pattern KILL_COUNT_PATTERN = Pattern.compile(
		"^Your (?:\\w+ )?(.+?) (?:\\w+ )?count is: ([0-9,]+)\\.$",
		Pattern.CASE_INSENSITIVE
	);

	@Inject
	private Client client;

	@Inject
	private LiveUploader uploader;

	private final Map<String, Integer> lastKnownSkillXp = new LinkedHashMap<>();
	private final Map<String, Integer> pendingSkillXp = new LinkedHashMap<>();
	private final Map<String, Integer> pendingBossKc = new LinkedHashMap<>();

	void reset()
	{
		lastKnownSkillXp.clear();
		pendingSkillXp.clear();
		pendingBossKc.clear();
		uploader.reset();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		String name = canonicalSkillName(event.getSkill());
		int xp = event.getXp();

		Integer previous = lastKnownSkillXp.put(name, xp);
		if (previous == null)
		{
			// First StatChanged for this skill this session is RuneLite
			// syncing the client's current total on login, not a real gain
			// -- record it as the baseline and queue nothing yet.
			return;
		}
		if (previous == xp)
		{
			// A pure stat-boost/drain event (potion, prayer drain, ...)
			// fires StatChanged too, but with unchanged true xp.
			return;
		}

		pendingSkillXp.put(name, xp);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		Matcher matcher = KILL_COUNT_PATTERN.matcher(Text.removeTags(event.getMessage()));
		if (!matcher.matches())
		{
			return;
		}

		String bossName = matcher.group(1).trim();
		String count = matcher.group(2).replace(",", "");

		try
		{
			pendingBossKc.put(bossName, Integer.parseInt(count));
		}
		catch (NumberFormatException e)
		{
			log.debug("Could not parse kill count from chat message: {}", event.getMessage());
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			flush(true);
			reset();
		}
	}

	/** Called every ~15s from AwesomeClanPlugin's own {@code @Schedule}
	 * method -- see that class for why this isn't a {@code @Schedule} method
	 * on this class directly. */
	void flushPeriodic()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		flush(false);
	}

	private void flush(boolean loggedOut)
	{
		if (!loggedOut && pendingSkillXp.isEmpty() && pendingBossKc.isEmpty())
		{
			return;
		}

		if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
		{
			return;
		}
		String rsn = client.getLocalPlayer().getName().trim();
		if (rsn.isEmpty())
		{
			return;
		}

		LivePayload payload = new LivePayload(
			rsn,
			!loggedOut,
			loggedOut,
			new LinkedHashMap<>(pendingSkillXp),
			new LinkedHashMap<>(pendingBossKc)
		);

		uploader.upload(payload);
		pendingSkillXp.clear();
		pendingBossKc.clear();
	}

	private static String canonicalSkillName(Skill skill)
	{
		String name = skill.getName();
		return SKILL_NAME_OVERRIDES.getOrDefault(name, name);
	}
}
