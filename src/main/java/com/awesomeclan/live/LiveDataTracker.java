package com.awesomeclan.live;

import com.awesomeclan.util.AccountId;
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
 * Collects XP and kill count changes and sends them every 15s while logged in.
 * It sends even when nothing changed, so the server knows you're still online.
 */
@Slf4j
public class LiveDataTracker
{
	// RuneLite names that differ from the hiscores names the server uses
	private static final Map<String, String> SKILL_NAME_OVERRIDES = Map.of("Runecraft", "Runecrafting");

	// "Your Zulrah kill count is: 412."
	// "Your completed Barrows Chests count is: 100."
	// "Your Chambers of Xeric completion count is: 5."
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

	private long cachedAccountHash = -1;
	private String cachedAccountId;

	public void reset()
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
		// first event per skill is the login sync, and boosts/drains fire
		// this too without an xp change
		if (previous == null || previous == xp)
		{
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
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			// may no-op if the local player isn't set yet, the periodic flush covers it
			flush(false);
		}
	}

	public void flushPeriodic()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			flush(false);
		}
	}

	// Closing the client while logged in never goes through LOGIN_SCREEN
	public void flushOnShutdown()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			flush(true);
		}
	}

	private void flush(boolean loggedOut)
	{
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
			accountId(),
			!loggedOut,
			loggedOut,
			new LinkedHashMap<>(pendingSkillXp),
			new LinkedHashMap<>(pendingBossKc)
		);

		uploader.upload(payload);
		pendingSkillXp.clear();
		pendingBossKc.clear();
	}

	// Checked every flush since the hash changes when you switch accounts
	// without restarting the client.
	private String accountId()
	{
		long hash = client.getAccountHash();
		if (hash != cachedAccountHash)
		{
			cachedAccountHash = hash;
			cachedAccountId = AccountId.of(hash);
		}
		return cachedAccountId;
	}

	private static String canonicalSkillName(Skill skill)
	{
		String name = skill.getName();
		return SKILL_NAME_OVERRIDES.getOrDefault(name, name);
	}
}
