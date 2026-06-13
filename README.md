# AdvancedDuty

> A powerful, fully configurable staff duty management plugin for Spigot & Paper servers.

---

## ✨ Features

- **Duty Toggle** — Staff can go on/off duty with `/duty on`, `/duty off`, or `/duty toggle`
- **Separate Inventories** — Completely separate inventory (including ender chest) for on-duty and off-duty states
- **Playtime Tracking** — Track how long each staff member has been on duty, with a leaderboard
- **AFK Detection** — Automatically pause, take off duty, or kick AFK staff with fully configurable triggers
- **Staff Chat** — Private staff chat with mention highlighting, sounds, and Discord forwarding
- **LuckPerms Integration** — Automatically add/remove groups and permissions when going on/off duty
- **Staff Visibility** — Glow effect and custom name tag prefix/suffix for off-duty staff
- **Discord Webhooks** — Send on/off duty events and staff chat messages to Discord
- **Duty Log** — Daily or single log files of all duty sessions
- **PlaceholderAPI Support** — Rich set of placeholders for scoreboards, tab lists, and more
- **Multi-language** — English, Spanish, Russian, and Chinese included out of the box
- **MySQL & YAML Storage** — Choose between file-based or database storage with HikariCP connection pooling

---

## 📋 Requirements

| Requirement | Version                                       |
|---|-----------------------------------------------|
| Spigot / Paper | 1.19.x – 1.20.x                               |
| Java | 8+                                            |
| LuckPerms | Any recent version (optional but recommended) |
| PlaceholderAPI | Any recent version (optional)                 |

---

## 🚀 Installation

1. Download `AdvancedDuty.jar` and place it in your `/plugins` folder
2. Restart your server
3. Edit `plugins/AdvancedDuty/config.yml` to your liking
4. Edit `plugins/AdvancedDuty/languages/en.yml` (or set another language)
5. Run `/duty reload` to apply changes without restarting

---

## 🔧 Commands

| Command | Description | Permission |
|---|---|---|
| `/duty on [reason]` | Go on duty | `advancedduty.use` |
| `/duty off` | Go off duty | `advancedduty.use` |
| `/duty toggle [reason]` | Toggle duty state | `advancedduty.use` |
| `/duty status [player]` | View duty status | `advancedduty.status` |
| `/duty playtime [player]` | Check playtime | `advancedduty.playtime` |
| `/duty playtimetop [page]` | Leaderboard | `advancedduty.playtime.top` |
| `/duty reset <player>` | Reset a player's playtime | `advancedduty.playtime.reset` |
| `/duty reload` | Reload config & language files | `advancedduty.reload` |
| `/duty version` | Show plugin version | — |
| `/staffchat <message>` | Send a staff chat message | `advancedduty.staffchat` |

---

## 🔑 Permissions

| Permission | Description | Default |
|---|---|---|
| `advancedduty.use` | Use `/duty on/off/toggle` | `false` |
| `advancedduty.staff` | Counted in playtime tracking | `false` |
| `advancedduty.status` | View own duty status | `false` |
| `advancedduty.status.others` | View other players' status | `false` |
| `advancedduty.playtime` | Check own playtime | `false` |
| `advancedduty.playtime.others` | Check other players' playtime | `false` |
| `advancedduty.playtime.top` | View leaderboard | `false` |
| `advancedduty.playtime.reset` | Reset a player's playtime | `op` |
| `advancedduty.staffchat` | Send staff chat messages | `false` |
| `advancedduty.staffchat.receive` | Receive staff chat (without being on duty) | `false` |
| `advancedduty.reload` | Reload the plugin | `op` |

---

## 📊 PlaceholderAPI Placeholders

### Global
| Placeholder | Description |
|---|---|
| `%advancedduty_staff_online%` | Number of staff currently on duty |
| `%advancedduty_staff_list%` | Comma-separated list of on-duty staff names |
| `%advancedduty_any_staff_online%` | `true` / `false` |
| `%advancedduty_top_1_name%` … `top_10_name` | Name of player at leaderboard rank |
| `%advancedduty_top_1_time%` … `top_10_time` | Formatted playtime at leaderboard rank |

### Per Player
| Placeholder | Description |
|---|---|
| `%advancedduty_status%` | Readable status label (from lang file) |
| `%advancedduty_status_color%` | Colored status label |
| `%advancedduty_is_on_duty%` | `true` / `false` |
| `%advancedduty_duty_icon%` | `✔` or `✘` |
| `%advancedduty_playtime%` | Total formatted playtime (e.g. `3h 22m 10s`) |
| `%advancedduty_playtime_hours%` | Total hours (integer) |
| `%advancedduty_playtime_minutes%` | Total minutes (integer) |
| `%advancedduty_playtime_seconds%` | Total seconds (integer) |
| `%advancedduty_playtime_ms%` | Total milliseconds (raw) |
| `%advancedduty_session_duration%` | Duration of current duty session |
| `%advancedduty_rank%` | Player's position in the leaderboard |
| `%advancedduty_has_session%` | `true` if player has an active session |

---

## 🌍 Languages

Built-in language files:

| File | Language |
|---|---|
| `en.yml` | English |
| `es.yml` | Spanish |
| `ru.yml` | Russian |
| `zh.yml` | Chinese (Simplified) |

To switch language, set `language: en` in `config.yml` to any of the above codes.  
To add your own language, copy `en.yml`, rename it, and translate the messages.

---

## 💾 Storage

**YAML** (default) — stores data in `plugins/AdvancedDuty/data/`. No setup required.

**MySQL** — for multi-server or high-performance setups. Set `storage.type: MYSQL` and fill in your database credentials in `config.yml`. Uses HikariCP connection pooling for reliability.

---

## 🤖 AFK Detection

When a staff member is on duty and becomes inactive, AdvancedDuty can:

- **PAUSE** — Pause their playtime counter until they return
- **AUTO_OFF** — Automatically take them off duty
- **KICK** — Kick them from the server

You can configure exactly which actions count as activity (movement, chat, commands, inventory clicks, etc.) under `duty.afk.track` in `config.yml`.

---

## 📣 Discord Integration

AdvancedDuty can send rich embeds to a Discord webhook when staff go on/off duty, and forward staff chat messages to a separate channel. Set `discord.enabled: true` and fill in your webhook URL to get started.

---

## ⚙️ Configuration

The full `config.yml` is heavily commented — every option has an explanation directly above it. Key sections:

- `duty.apply` — what happens when going on duty (gamemode, flight, heal)
- `duty.restore` — what gets restored when going off duty
- `duty.inventory` — separate inventory settings
- `duty.afk` — AFK detection and activity tracking
- `storage` — YAML or MySQL
- `hooks.luckperms` — staff toggle, duty meta, name tags, glow
- `staffchat` — format, sounds, mentions
- `discord` — webhook integration

---

## 📄 License

AdvancedDuty is free and open source. You are free to use and modify it for your server.

---

*Made with ❤️ by HesamAI*