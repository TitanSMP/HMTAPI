# HMTAPI

**Turn your Minecraft server into a REST API.**

HMTAPI exposes your Paper server over plain HTTP and returns JSON. You pick the contents of each
endpoint in a small YAML config and can pull in live data from any PlaceholderAPI placeholder —
player balance, rank, online status, and so on.

```json
// GET /api/users/Notch
{
  "username": "Notch",
  "last_login": "2026/09/25 22:13:20",
  "balance": "1250.42"
}
```

## Features

- **Simple YAML config.** No code needed — just define the endpoints you want.
- **Any data you want.** Combine built-in placeholders with any PlaceholderAPI placeholder.
- **Per-player and global endpoints.** Serve server-wide data, or data about one specific player.
- **Reload without restart.** Update your config in-game and apply it immediately.
- **Safe by design.** API traffic never blocks the server's main thread.
- **No bloat.** No bundled libraries, no database, no metrics to maintain.

## Quick start

1. Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) — it is a hard
   requirement.
2. Drop the HMTAPI jar into your `plugins` folder.
3. Start the server once to generate `plugins/HMTAPI/config.yml`.
4. Edit your endpoints.
5. Run `/hmtapi reload` in-game. No restart needed.

```yaml
endpoints:
  users:
    require_player: true
    object:
      username: "{username}"
      last_login: "{last_login}"
      balance: "{papi:%vault_eco_balance%}"
```

Then try it out:

```bash
curl http://localhost:4567/api/users/Notch
```

## Requirements

|                 |                                                                           |
| --------------- | ------------------------------------------------------------------------- |
| Server          | Paper 26.3 or newer                                                       |
| Java            | 25                                                                        |
| Required plugin | [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) |

## Documentation

This page is only a short overview. The **full documentation** — every config option, all routes,
all placeholders, status codes, troubleshooting and security notes — lives here:

### [gaming.henrymeyer.de/projects/plugins/hmtapi/](https://gaming.henrymeyer.de/projects/plugins/hmtapi/)

## Credits

HMTAPI is a fork of [APIMachine](https://modrinth.com/project/9eIn1bYM), developed in cooperation
with HM Gaming for TitanSMP.

## Links

- Documentation: [gaming.henrymeyer.de/projects/plugins/hmtapi/](https://gaming.henrymeyer.de/projects/plugins/hmtapi/)

## License

[GPL-3.0](https://choosealicense.com/licenses/gpl-3.0/)
