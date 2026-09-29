package com.awesomeclan.live;

import java.util.Map;
import lombok.Value;

@Value
class LivePayload
{
	String rsn;
	String accountId;
	boolean loggedIn;
	boolean loggedOut;
	Map<String, Integer> skills;
	Map<String, Integer> bosses;
}
