package com.awesomeclan.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class AccountIdTest
{
	// checked against python hashlib, these must never change
	@Test
	public void matchesDinkFormat()
	{
		assertEquals("b87675cd6e176eb15f9886b1fb3b84a531bd31a751cad4a3c32f9b79", AccountId.of(123456789L));
		assertEquals("ada7d4ffe05736e9179130a78fca81ac4603940f23d218813e353e8e", AccountId.of(-5L));
	}

	@Test
	public void unknownHashGivesNull()
	{
		assertNull(AccountId.of(-1L));
	}
}
