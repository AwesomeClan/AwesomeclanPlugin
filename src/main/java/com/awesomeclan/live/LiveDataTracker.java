package com.awesomeclan.live;

import com.awesomeclan.util.AccountId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.util.Text;

@Slf4j
public class LiveDataTracker
{
	// server uses the hiscores names
	private static final Map<String, String> SKILL_NAME_OVERRIDES = Map.of("Runecraft", "Runecrafting");

	// not main hiscores
	private static final Set<WorldType> IGNORED_WORLDS = EnumSet.of(
		WorldType.SEASONAL,
		WorldType.DEADMAN,
		WorldType.BETA_WORLD,
		WorldType.TOURNAMENT_WORLD,
		WorldType.NOSAVE_MODE,
		WorldType.FRESH_START_WORLD,
		WorldType.QUEST_SPEEDRUNNING,
		WorldType.PVP_ARENA,
		WorldType.LAST_MAN_STANDING
	);

	private static final long FULL_SYNC_INTERVAL_MS = 5 * 60 * 1000;

	// "Your Zulrah kill count is: 412."
	// "Your completed Chambers of Xeric count is: 5."
	private static final Pattern KILL_COUNT_PATTERN = Pattern.compile(
		"^Your (?:completed |subdued )?(.+?) (?:kill |chest |completion |success |harvest )?count is: ([0-9,]+)\\.?$",
		Pattern.CASE_INSENSITIVE
	);

	// "You have completed 12 medium Treasure Trails."
	private static final Pattern CLUE_COUNT_PATTERN = Pattern.compile(
		"^You have completed ([0-9,]+) (beginner|easy|medium|hard|elite|master) Treasure Trails?\\.$",
		Pattern.CASE_INSENSITIVE
	);

	// "Amount of rifts you have closed: 24."
	private static final Pattern RIFTS_PATTERN = Pattern.compile(
		"^Amount of rifts you have closed: ([0-9,]+)\\.?$",
		Pattern.CASE_INSENSITIVE
	);

	@Inject
	private Client client;

	@Inject
	private LiveUploader uploader;

	private final Map<String, Integer> lastKnownSkillXp = new LinkedHashMap<>();
	private final Map<String, Integer> pendingSkillXp = new LinkedHashMap<>();
	private final Map<String, Integer> pendingBossKc = new LinkedHashMap<>();
	private final Map<String, Integer> pendingActivities = new LinkedHashMap<>();

	private long cachedAccountHash = -1;
	private String cachedAccountId;
	private String lastRsn;
	private long lastFullSync;
	// world type can already be the new world while hopping
	private boolean ignoredWorld;

	public void reset()
	{
		lastKnownSkillXp.clear();
		clearPending();
		lastFullSync = 0;
		uploader.reset();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		String name = canonicalSkillName(event.getSkill());
		int xp = event.getXp();

		Integer previous = lastKnownSkillXp.put(name, xp);
		// skip the login sync and boosts/drains
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

		String message = Text.removeTags(event.getMessage());
		try
		{
			Matcher matcher = KILL_COUNT_PATTERN.matcher(message);
			if (matcher.matches())
			{
				pendingBossKc.put(matcher.group(1).trim(), parseCount(matcher.group(2)));
				return;
			}

			matcher = CLUE_COUNT_PATTERN.matcher(message);
			if (matcher.matches())
			{
				pendingActivities.put("Clue Scrolls (" + matcher.group(2).toLowerCase() + ")", parseCount(matcher.group(1)));
				return;
			}

			matcher = RIFTS_PATTERN.matcher(message);
			if (matcher.matches())
			{
				pendingActivities.put("Rifts closed", parseCount(matcher.group(1)));
			}
		}
		catch (NumberFormatException e)
		{
			log.debug("Bad count: {}", message);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		// hiscores update on hop as well as logout
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			flush(true);
			reset();
		}
		else if (state == GameState.LOGGED_IN)
		{
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

	public void flushOnShutdown()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			flush(true);
		}
	}

	// closing the client doesn't call shutDown
	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		CompletableFuture<Void> future = flush(true);
		if (future != null)
		{
			event.waitFor(future);
		}
	}

	private CompletableFuture<Void> flush(boolean loggedOut)
	{
		if (!loggedOut)
		{
			ignoredWorld = onIgnoredWorld();
		}
		if (ignoredWorld)
		{
			clearPending();
			return null;
		}

		String rsn = currentRsn();
		if (rsn == null)
		{
			return null;
		}

		if (!loggedOut)
		{
			addFullSyncIfDue();
		}

		LivePayload payload = new LivePayload(
			rsn,
			accountId(),
			loggedOut,
			new LinkedHashMap<>(pendingSkillXp),
			new LinkedHashMap<>(pendingBossKc),
			new LinkedHashMap<>(pendingActivities)
		);

		CompletableFuture<Void> future = uploader.upload(payload);
		clearPending();
		return future;
	}

	private void clearPending()
	{
		pendingSkillXp.clear();
		pendingBossKc.clear();
		pendingActivities.clear();
	}

	// server needs every skill for Overall
	private void addFullSyncIfDue()
	{
		long now = System.currentTimeMillis();
		if (now - lastFullSync < FULL_SYNC_INTERVAL_MS)
		{
			return;
		}
		// not loaded yet
		if (client.getSkillExperience(Skill.HITPOINTS) <= 0)
		{
			return;
		}

		for (Skill skill : Skill.values())
		{
			pendingSkillXp.put(canonicalSkillName(skill), client.getSkillExperience(skill));
		}
		lastFullSync = now;
	}

	private boolean onIgnoredWorld()
	{
		EnumSet<WorldType> types = client.getWorldType();
		return types != null && !Collections.disjoint(types, IGNORED_WORLDS);
	}

	// no local player while hopping
	private String currentRsn()
	{
		if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null)
		{
			String name = client.getLocalPlayer().getName().trim();
			if (!name.isEmpty())
			{
				lastRsn = name;
				return name;
			}
		}
		return client.getGameState() == GameState.HOPPING ? lastRsn : null;
	}

	// can change if you switch accounts without restarting
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

	private static int parseCount(String raw)
	{
		return Integer.parseInt(raw.replace(",", ""));
	}

	private static String canonicalSkillName(Skill skill)
	{
		String name = skill.getName();
		return SKILL_NAME_OVERRIDES.getOrDefault(name, name);
	}
}
