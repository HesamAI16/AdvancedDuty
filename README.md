# AdvancedDuty 2.0

All-in-one staff duty plugin for Paper/Spigot 1.19+ (Java 17): duty mode with separate inventories, vanish, freeze, inspect,
tools, profiles, scoreboard/bossbar, playtime + leaderboards, milestones & quota, staff chat, Discord, network mode,
LuckPerms + PlaceholderAPI integration, YAML / SQLite / MySQL / MariaDB storage. Everything is configurable.

## Install
1. Drop the jar in `plugins/`, start the server, edit `plugins/AdvancedDuty/config.yml`.
2. Optional: LuckPerms, PlaceholderAPI. For SQL storage set `storage.type` (YAML data is imported automatically).
3. `/duty reload` applies most changes; storage and proxy changes need a restart.

## Commands
| Command | Description |
|---|---|
| `/duty [on\|off\|toggle] [player] [reason] [-s]` | Change duty (`-s` = silent) |
| `/duty status [player]`, `playtime [player]`, `playtimetop [today\|week\|month\|all] [page]` | Info |
| `/duty log [player] [page]` | Duty history |
| `/duty list` / `/stafflist` | Online staff |
| `/duty goals`, `/duty quota [player\|report]` | Milestones and quota |
| `/duty vanish`, `freeze\|unfreeze <player>`, `inspect\|echest <player>` | Staff tools |
| `/duty gui`, `/duty scoreboard` | Menu, scoreboard toggle |
| `/duty staffchat [msg]` / `/sc [msg]` | Staff chat (no message = toggle mode) |
| `/duty reset <player> confirm`, `reload`, `languages`, `update`, `version` | Admin |

## Permissions
All nodes are in `plugin.yml`; `advancedduty.admin` grants everything except the exemption nodes
(`freeze.exempt`, `inspect.exempt`, `afk.exempt`, `quota.exempt`). Profiles use their own nodes (e.g. `advancedduty.profile.mod`).

## Placeholders (PlaceholderAPI, `%advancedduty_<name>%`)
`status`, `status_color`, `is_on_duty`, `duty_icon`, `session_duration`, `sessions`, `longest_session`, `rank`, `reason`,
`vanished`, `afk`, `frozen`, `staff_online`, `staff_online_total`, `staff_count`, `any_staff_online`, `staff_list`, `frozen_count`,
`quota_percent`, `quota_required`, `quota_remaining`, `quota_bar`, `quota_met`, plus playtime values (total/today/week/month, `_ms`, `_seconds`, `_minutes`, `_hours`).

## Developer API
```java
AdvancedDutyAPI.isOnDuty(player);
AdvancedDutyAPI.setOnDuty(player, true);   // returns DutyResult
```
Events: `DutyToggleEvent` (cancellable unless forced), `DutyStateChangeEvent`.

## Languages
`languages/en|es|ru|zh|fa.yml`. Copy one, translate it, set `language: yourfile`. Missing keys fall back to English.

## Build
`mvn package` (Java 17). Dependencies: Paper API, LuckPerms API, PlaceholderAPI (provided); HikariCP + slf4j are shaded.
