package com.awesomeclan;

import java.util.List;
import java.util.Map;
import lombok.Value;

@Value
class RosterPayload
{
	String clanName;
	List<RosterMember> members;
	Map<String, Integer> hierarchy;
}
