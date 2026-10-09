package com.awesomeclan.notify;

import com.awesomeclan.AwesomeClanConfig;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.util.ImageUtil;

@Singleton
public class NotificationOverlay extends Overlay
{
	private static final int MAX_TEXT_WIDTH = 170;
	private static final int PAD_X = 5;
	private static final int PAD_Y = 4;
	private static final int BAR = 2;
	private static final int MAX_QUEUED = 3;
	private static final long FADE_MS = 250;
	private static final Color MESSAGE_COLOR = new Color(0xC8C8C8);
	private static final BufferedImage BANNER = loadBanner();

	private final AwesomeClanConfig config;
	private final Deque<ClanNotification> queue = new ArrayDeque<>();

	private ClanNotification current;
	private long shownAt;

	@Inject
	NotificationOverlay(AwesomeClanConfig config)
	{
		this.config = config;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
		setMovable(true);
	}

	public synchronized void push(ClanNotification n)
	{
		if (queue.size() >= MAX_QUEUED)
		{
			queue.pollFirst();
		}
		queue.addLast(n);
	}

	public synchronized void clear()
	{
		queue.clear();
		current = null;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		ClanNotification n;
		float alpha;
		synchronized (this)
		{
			long now = System.currentTimeMillis();
			long duration = config.notificationDuration() * 1000L;
			if (current != null && now - shownAt > duration)
			{
				current = null;
			}
			if (current == null)
			{
				current = queue.pollFirst();
				shownAt = now;
			}
			if (current == null)
			{
				return null;
			}
			n = current;
			long age = now - shownAt;
			alpha = Math.max(0f, Math.min(1f, Math.min(age, duration - age) / (float) FADE_MS));
		}

		Font font = FontManager.getRunescapeSmallFont();
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();

		List<String> title = wrap(n.getTitle(), fm);
		List<String> message = wrap(n.getMessage(), fm);

		int textW = Stream.concat(title.stream(), message.stream()).mapToInt(fm::stringWidth).max().orElse(0);

		int lineH = fm.getHeight();
		int iconH = BANNER == null ? 0 : BANNER.getHeight();
		int iconSpace = BANNER == null ? 0 : BANNER.getWidth() + PAD_X;
		int width = BAR + PAD_X + iconSpace + textW + PAD_X;
		int height = PAD_Y * 2 + Math.max(iconH, lineH * (title.size() + message.size()));

		Composite original = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

		g.setColor(ComponentConstants.STANDARD_BACKGROUND_COLOR);
		g.fillRect(0, 0, width, height);
		g.setColor(n.kind().getColor());
		g.fillRect(0, 0, BAR, height);

		if (BANNER != null)
		{
			g.drawImage(BANNER, BAR + PAD_X, (height - iconH) / 2, null);
		}

		int x = BAR + PAD_X + iconSpace;
		int y = PAD_Y + lineH - fm.getDescent();
		for (String line : title)
		{
			text(g, line, n.kind().getColor(), x, y);
			y += lineH;
		}
		for (String line : message)
		{
			text(g, line, MESSAGE_COLOR, x, y);
			y += lineH;
		}

		g.setComposite(original);
		return new Dimension(width, height);
	}

	private static void text(Graphics2D g, String s, Color color, int x, int y)
	{
		g.setColor(Color.BLACK);
		g.drawString(s, x + 1, y + 1);
		g.setColor(color);
		g.drawString(s, x, y);
	}

	private static List<String> wrap(String text, FontMetrics fm)
	{
		List<String> lines = new ArrayList<>();
		if (text == null || text.isBlank())
		{
			return lines;
		}

		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+"))
		{
			String next = line.length() == 0 ? word : line + " " + word;
			if (line.length() > 0 && fm.stringWidth(next) > MAX_TEXT_WIDTH)
			{
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
			else
			{
				line.setLength(0);
				line.append(next);
			}
		}
		lines.add(line.toString());
		return lines;
	}

	private static BufferedImage loadBanner()
	{
		// already at draw size, scaling it in game makes it blurry
		try
		{
			return ImageUtil.loadImageResource(NotificationOverlay.class, "banner.png");
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}
}
