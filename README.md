# QueryBook

A plugin for DataGrip and IntelliJ-based IDEs that lets you save, organize, and reuse SQL snippets without leaving the editor.

## Features

- **Folder organization** — Group queries in nested folders
- **Drag & drop** — Reorder and move queries between folders
- **Search** — Filter by name or SQL content
- **Execute queries** — Run saved queries directly as scratch files
- **Copy SQL** — Quick clipboard access
- **Duplicate** — Clone queries as starting points
- **Export / Import** — JSON-based backup and sharing
- **Keyboard shortcuts** — `Ctrl+Alt+Q` to save, `Delete` to delete, `F2` to rename
- **SQL preview** — Hover to preview query bodies
- **Folder counts** — Live child count badges

## Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+Alt+Q` | Save editor content to QueryBook |
| `Delete` | Delete selected item |
| `F2` | Rename selected item |
| `Double-click` | Rename |
| `Right-click` | Context menu |

## Installation

### JetBrains Marketplace (recommended)
1. Settings → Plugins → Marketplace
2. Search for **QueryBook**
3. Click Install and restart

### Manual
Download the latest `.zip` from [Releases](https://github.com/aborotto/querybook/releases), then:
1. Settings → Plugins → ⚙️ → Install Plugin from Disk
2. Select the file and restart

## Quick Start

1. **Open the panel** — View → Tool Windows → QueryBook

2. **Save a query** — Select SQL and press `Ctrl+Alt+Q` (or right-click → Save to QueryBook)

3. **Organize** — Use folders to group related queries. Drag and drop to reorganize.

4. **Run a query** — Select any query and click the Run button. It opens as a scratch file.

5. **Search** — Type in the search box. Toggle SQL mode to search query bodies.

## Export & Import

Click the **Export** button to save your library as JSON. Click **Import** to restore from a previously exported file.

Choose to **merge** the imported queries with existing ones or **replace** the entire library.

Example export format:
```json
{
  "version": 1,
  "exportedAt": "2026-04-10T14:30:00",
  "root": {
    "name": "Root",
    "isFolder": true,
    "children": [
      {
        "name": "Get all users",
        "sqlCode": "SELECT * FROM users ORDER BY created_at DESC",
        "description": "Daily monitoring query",
        "isFolder": false
      }
    ]
  }
}
```

## Project Structure

```
src/main/kotlin/com/demo/
├── action/               # Saving and dialogs
├── model/                # Data models and storage
├── service/              # Import/export logic
└── ui/                   # UI components (tree, editor, tool window)
```

## Development

Prerequisites: JDK 21+, IntelliJ IDEA or DataGrip

```bash
git clone https://github.com/aborotto/querybook.git
cd querybook
./gradlew runIde          # Run plugin locally
./gradlew buildPlugin     # Build distribution zip
./gradlew verifyPlugin    # Verify compatibility
```

## Roadmap

- [ ] **Cloud sharing** — Share and sync queries across devices
- [ ] **Tags / Labels** — Tag queries for cross-folder filtering
- [ ] **Search highlight** — Highlight matches in the editor
- [ ] **Settings page** — Configure defaults and behavior
- [ ] **Multi-select** — Bulk operations on multiple queries
- [ ] **Markdown descriptions** — Render notes as formatted markdown

## License

MIT License — see [LICENSE](LICENSE) for details.

