package com.awesomeclan;

import java.util.Map;
import lombok.Value;

@Value
class LivePayload
{
	String rsn;
	boolean loggedIn;
	boolean loggedOut;
	Map<String, Integer> skills;
	Map<String, Integer> bosses;
}
