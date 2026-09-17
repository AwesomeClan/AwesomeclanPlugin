# AwesomeClan

Keeps the AwesomeClan roster synced with the clan website. Runs quietly in the
background for any member of the clan: no config, no side panel. Every 20-30
minutes (randomized per client so a few hundred members don't all sync at
once) it reads the clan roster from the game and posts it to the clan's
website.

## Development

```
./gradlew run
```

This launches a local RuneLite client with the plugin loaded, per the
[Plugin Hub development guide](https://github.com/runelite/plugin-hub#developing-plugins).
