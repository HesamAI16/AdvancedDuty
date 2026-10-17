# Changelog

## 2.0.0 - BIG UPDATE

### New features
- **Vanish** (built-in, or driven by SuperVanish/PremiumVanish commands), silent join/quit, optional fake join/leave messages. Works from the duty toggle, tools or `/duty vanish`.
- **Freeze** with screenshare title, private chat, invulnerability, effects, command whitelist, on-quit punishment commands, persists across restarts.
- **Inspect** inventories and ender chests (read-only snapshot, or live/editable with `advancedduty.inspect.edit`).
- **Duty tools**: configurable hotbar items (vanish, freeze, inspect, random-tp, staff list, menu, custom commands), per profile.
- **Profiles**: per-rank duty setup (gamemode, flight, vanish, god mode, glow, effects, tab prefix, tools, scoreboard, commands) chosen by permission + priority.
- **Staff menu GUI** (`/duty gui`): duty, vanish, staff list, player list with teleport/inspect/freeze, stats, leaderboard.
- **Displays**: scoreboard (with per-player toggle `/duty scoreboard`), bossbar, actionbar, tab prefix/suffix.
- **Milestones**: reward goals (console commands) once per day/week/month/all-time, plus a **weekly quota** with overrides, exemptions, join reminder and `/duty quota report`.
- **Session history** (`/duty log`) in every storage backend; **playtime periods** (today/week/month/all) and cached leaderboards.
- **Staff list** (`/stafflist`, `/duty list`) including AFK/vanished markers and other servers.
- **Staff chat 2.0**: toggle mode, chat prefix, mentions, cross-server relay, Discord relay.
- **Discord**: embeds for duty, freeze and milestone events, plus a polling bot answering `!staff` and custom text commands (no JDA needed).
- **Network mode**: shared staff presence and staff chat over MySQL/MariaDB.
- **SQLite** backend and **MariaDB** alias, configurable table names, automatic YAML -> SQL import.
- **Restrictions**: block commands while on duty, or require duty for commands.
- Duty reasons (required/min/max/presets), `-s` silent flag, announce modes, sound/title/actionbar feedback, world whitelist/blacklist, auto duty on join, `on-quit` KEEP/OFF.
- **API**: `AdvancedDutyAPI`, `DutyToggleEvent` (cancellable), `DutyStateChangeEvent`, `DutyCause`, `DutyResult`.
- New PlaceholderAPI placeholders (staff counts, quota, session, rank, vanish, freeze ...).
- Languages: English, Spanish, Russian, Chinese, Persian; every GUI text is translatable; missing keys fall back to English.
- `config.yml` and language files update themselves (new keys merged, comments + values kept, backup written).

### Fixes
- Playtime is now delta-based: safe on multi-server MySQL and no more lost time from overlapping saves.
- All disk/database work runs on one ordered storage thread; no main-thread I/O on join/quit; atomic YAML writes with `.bak`; corrupt files are quarantined instead of overwritten.
- If a player's data cannot be loaded, duty is refused for them rather than overwriting their stored inventory.
- Duty inventory capture no longer includes tool items; items cannot be duplicated or lost on quit, crash or reload.
- LuckPerms: duty prefixes/name tags are transient (never persisted), staff group removal is predicate based, no more stale nodes after reload.
- AFK timer is reset when going on duty; AFK actions (PAUSE/AUTO_OFF/KICK) are consistent.
- Offline player lookup no longer queries Mojang (no lag spikes).
- `/duty reset` requires `confirm`; Discord webhooks use a rate-limit aware sender with empty `allowed_mentions`.
- Many smaller issues in the language GUI, update checker/downloader, reload and shutdown paths.

### Behaviour changes (check your config)
- `duty.remember-duty-state` defaults to `false`.
- `storage.restore-inventory-on-rejoin` defaults to `false`.
- `update.auto-download` defaults to `false` for new configs (existing configs keep their value).
- With `storage.restore-session-on-join: false`, players are now cleanly put off duty on join.
- History command is `/duty log`.
