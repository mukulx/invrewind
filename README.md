# InvRewind

<div align="center">

**Highly configurable inventory backup and restore plugin for Paper, Purpur, and Folia.**

</div>

---

## Features

### Core Functionality
- **Automatic Backups** - Capture player inventories on death, world change, join, quit, and scheduled intervals
- **Force Backups** - Create manual backups on demand via GUI
- **GUI-Based Restoration** - Intuitive interface for browsing and restoring backups
- **Selective Restore** - Restore specific items: inventory, armor, offhand, ender chest, XP, or location
- **Export to Shulker** - Export backups as shulker boxes for easy item recovery

### Technical Features
- **Folia Compatible** - Full support for Folia's region-based threading
- **Multi-Database Support** - Choose between YAML, SQLite, or MySQL
- **Async Operations** - All database operations run asynchronously to prevent lag
- **Backup Limits** - Automatic cleanup of old backups per type
- **Pagination** - Handle large player counts and backup lists efficiently

### Backup Types
- **Death** - Automatic backup when player dies
- **World Change** - Backup when switching worlds (including custom dimensions)
- **Join** - Backup when player joins the server
- **Quit** - Backup when player leaves the server
- **Scheduled** - Automatic backups at regular intervals
- **Force** - Manual backups created by admins

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/invrewind` or `/ir` | Open player selection GUI | `invrewind.admin` |
| `/invrewind reload` | Reload configuration | `invrewind.admin` |
| `/invrewindforce <player\|all>` | Force backup for player(s) | `invrewind.admin` |

---

## Configuration

### Database Options

```yaml
database:
  type: yaml  # yaml, sqlite, or mysql
```

- **YAML** - File-based, easy to backup, good for small servers
- **SQLite** - File-based database, better performance for medium servers
- **MySQL** - External database, best for large/multi-server setups

### Auto-Backup Settings

```yaml
auto-backup:
  on-death: true
  on-world-change: true
  on-join: true
  on-quit: true
  
  scheduled:
    enabled: false
    interval: 30  # minutes
    require-movement: true
    min-items: 1
```

### Backup Limits

```yaml
auto-backup:
  limits:
    enabled: true
    max-per-type:
      death: 50
      world-change: 30
      join: 20
      quit: 20
      scheduled: 40
      force: 100
```

### Features

```yaml
features:
  save-inventory: true
  save-armor: true
  save-offhand: true
  save-enderchest: true
  save-health: true
  save-hunger: true
  save-xp: true
  save-location: true
```

### GUI Sounds

```yaml
gui:
  sounds-enabled: true  # Enable/disable all GUI sounds
```

---

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

---

## Support

- **Issues**: [GitHub Issues](https://github.com/mukulx/invrewind/issues)
- **Feature Requests**: [Request a Feature](https://github.com/mukulx/invrewind/issues/new?template=feature_request.md)

---

<div align="center">

**Made by [MukulX](https://github.com/mukulx)**

Star this repository if you find it useful!

</div>
