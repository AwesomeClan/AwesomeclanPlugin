package com.awesomeclan.roster;

import java.util.List;
import java.util.Map;
import lombok.Value;

@Value
public class RosterPayload
{
	String clanName;
	List<RosterMember> members;
	Map<String, Integer> hierarchy;
}
