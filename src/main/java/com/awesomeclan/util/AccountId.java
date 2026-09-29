package com.awesomeclan.util;

import com.google.common.hash.HashCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.extern.slf4j.Slf4j;

/**
 * Hashed account ID, stable across name changes. Uses the same hash as Dink's
 * dinkAccountHash so the IDs line up with Dink's. Don't change the padding,
 * the server has these stored.
 */
@Slf4j
public final class AccountId
{
	private static final byte[] PADDING = "JennaRaidingDreamSeedZeroPercentOptionsForAnts!"
		.getBytes(StandardCharsets.UTF_8);

	private AccountId()
	{
	}

	/** Null before login (account hash is -1). */
	public static String of(long accountHash)
	{
		if (accountHash == -1)
		{
			return null;
		}

		MessageDigest digest;
		try
		{
			digest = MessageDigest.getInstance("SHA-224");
		}
		catch (NoSuchAlgorithmException e)
		{
			log.warn("SHA-224 unavailable", e);
			return null;
		}

		byte[] input = ByteBuffer.allocate(8 + PADDING.length)
			.putLong(accountHash)
			.put(PADDING)
			.array();
		return HashCode.fromBytes(digest.digest(input)).toString();
	}
}
