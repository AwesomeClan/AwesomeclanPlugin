package com.awesomeclan.notify;

import java.util.List;
import lombok.Data;

@Data
public class ClanNotification
{
	private long id;
	private String type;
	private String title;
	private String message;
	private boolean test;

	public NotificationType kind()
	{
		return NotificationType.of(type);
	}

	@Data
	static class Feed
	{
		private long cursor;
		private List<ClanNotification> notifications;
	}
}
