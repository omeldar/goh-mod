# GoH

The GoH server's own mod. It welcomes players, shows them what the server offers, and puts it
one button away: distant terrain for Voxy, a shared list of waypoints for JourneyMap, and an
introduction to [Lumi](https://github.com/omeldar/lumi).

It is the hub of the GoH mod family. The others are
[Autopilot](https://github.com/omeldar/goh-autopilot),
[Keeper](https://github.com/omeldar/goh-keeper),
[Worldgrow](https://github.com/omeldar/goh-worldgrow) and Lumi; ideas for all of them live on
the [GoH mods project board](https://github.com/users/omeldar/projects/2).

Built for **Minecraft 1.21.11 / Fabric**. One jar for both sides: the server half keeps the
waypoint list and says hello, the client half shows the screens. It changes no game mechanic.

---

## What players get

**A setup walkthrough**, once, the first time a player joins a GoH server. A page per mod:
what it does, in plain words, and the one choice it needs - load the distant terrain, import
the shared waypoints, say hello to Lumi. Only for what they actually have installed, so the
walkthrough is as long as their mod folder and no longer.

The last page before the send-off is every key the GoH mods register, in one list. Click a
row, press a key. If the key is already doing something the screen says what ("W is also Walk
Forward") and lets them decide, because occasionally that is the point. Esc while it is
listening clears the key instead, for players who would rather use the commands. `/goh setup`
reopens the whole thing.

**A welcome screen** for every server after the first. It shows the server's own welcome text
and a button for each thing they can switch on. Hover a button to see what it does; buttons
for things they cannot use say why ("needs JourneyMap"). `/goh` opens it again.
It is laid out from the bottom up, so it fits at the default window size and GUI scale, and the
text scrolls if a server writes a lot of it.

**Distant terrain**, through [LOD Server Support](https://modrinth.com/plugin/lod-server-support)
feeding [Voxy](https://modrinth.com/mod/voxy). Off until the player chooses: a first load can
be a gigabyte or more, and that should be their decision. Before it starts they see roughly how
much there is and how long it will take at this server's speed. While it loads, a chat line
every half minute says how far it is and how long is left, with a **[Stop]** link. A last line
says when it has caught up. Stopping keeps what loaded; switching it on again carries on.

Tested against a test server with 34,365 generated columns: the transfer ran at almost exactly
the 4 MiB/s per-player cap (about 37 KiB a column), which is what the estimate is built on, and
stopping ended it immediately.

**Shared waypoints**, for JourneyMap. Nothing is ever uploaded unless a player shares it on
purpose, one waypoint at a time - there is no syncing of anyone's map.

- *Share*: one of your own JourneyMap waypoints, or the block you are standing on, under a name.
- *Import*: copies the server's list into its own "GoH server" group in JourneyMap. Your own
  waypoints are not touched, and re-importing replaces the group, so moved or removed shared
  waypoints follow.
- *Names are unique* on the server, ignoring case. Sharing under an existing name overwrites it,
  and removing deletes it - but only for the player who shared it first, or the server owner.
  Anyone else is refused and told whose it is.
- When the list changes, everyone online gets the new one.

**Meet Lumi**: a short introduction on the first world a player joins after installing, with a
button to say hello. Only shown when Lumi is installed.

Players without the mod still get the server's welcome text in chat, and a link to the modpack.

## Commands

```
G                             the welcome screen (rebindable)
/goh                          the same
/goh setup                    the first-join walkthrough, again
/goh lumi                     the Lumi introduction
/goh terrain [on|off]         distant terrain: show, start or stop
/goh waypoints                the server's shared list
/goh waypoints import         copy it into JourneyMap
/goh waypoints share <name>   share one of your JourneyMap waypoints
/goh waypoints here <name>    share where you are standing
/goh waypoints remove <name>  remove one you shared (or any, as server owner)

/gohadmin waypoints           server owner / console: the list with owners
/gohadmin waypoints remove <name>
/gohadmin reload              re-read config/goh/server.json
```

## Lumi can use it

The client half serves a small MCP endpoint on `127.0.0.1` (port 25597, or the next free one)
and announces it through **Lumi Link** - see
[goh-autopilot/docs/lumi-link.md](https://github.com/omeldar/goh-autopilot/blob/main/docs/lumi-link.md).
With Lumi installed, a session gets these tools under a `goh_` prefix:

| Tool | What it does |
| --- | --- |
| `get_status` | What the server offers, what is installed, whether distant terrain is on |
| `list_shared_waypoints` | The server's list, and which ones this player may change |
| `list_my_waypoints` | The player's own JourneyMap waypoints |
| `import_waypoints` | Copy the shared list into JourneyMap |
| `share_waypoint` | Upload one waypoint - only when the player asks for that one |
| `remove_shared_waypoint` | Remove one, owner or server owner only |
| `set_distant_terrain` | Switch distant terrain on or off |

They go through exactly the same code as the buttons and commands, so the same rules hold, and a
refusal comes back with the server's reason ("belongs to Alice").

## Server setup

Drop the jar in `mods/` next to Fabric API. `config/goh/server.json`:

| Key | Default | |
| --- | --- | --- |
| `serverName` | `GoH` | Shown as "Welcome to ..." |
| `welcome` | two lines | The server's own text, one paragraph per entry |
| `website` | goh.omeldar.com | The Website button |
| `modpackUrl` | goh.omeldar.com | Linked in chat for players without the mod |
| `waypointSharing` | true | |
| `maxWaypointsPerPlayer` | 50 | The server owner is not limited |
| `maxWaypointsTotal` | 1000 | |

The shared list lives in the world folder as `goh-waypoints.json`, written atomically. If it
cannot be read the mod refuses to write rather than replace it with an empty list.

For distant terrain the server also needs LOD Server Support (which needs Fabric loader 0.18.4
or newer). The mod reads LSS's per-player bandwidth from its config and counts generated chunks
from region file headers on a background thread every ten minutes, so the estimate costs the
game nothing. On a hosted server, turn LSS's own generation off and let
[Worldgrow](https://github.com/omeldar/goh-worldgrow) pregenerate within the host's limits.

## Cost

No rendering outside its own screens, one tick listener that does nothing after the first two
seconds of a session unless distant terrain is loading (then one status read a second), and no
network traffic unless a player presses something. JourneyMap and LSS are reached only when they
are installed: JourneyMap through its plugin entry point, LSS by reflection, so a missing or
newer version of either degrades to a greyed-out button rather than a crash.

## Licence

MIT. Not made or endorsed by Mojang, Microsoft or Anthropic.
