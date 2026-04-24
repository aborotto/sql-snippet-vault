# QueryBook

A plugin for DataGrip and IntelliJ-based IDEs that lets you save, organize, and reuse SQL snippets without leaving the editor.

## Features

- **Folder organization** — Organize queries in nested folders, no depth limit
- **Drag & drop** — Reorder and move queries between folders with drop indicators
- **Search** — Filter by name or SQL code content
- **Execute queries** — Run saved queries directly as DataGrip scratch files
- **Copy to clipboard** — One-click SQL copy
- **Duplicate queries** — Create variations from existing snippets
- **Export / Import** — Portable JSON backups and team sharing
- **Keyboard shortcuts** — `Ctrl+Alt+Q` to save, `Delete` to delete, `F2` to rename
- **Query preview** — Hover to see SQL without opening
- **Folder counts** — Shows direct children count per folder

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

### Open the panel
Go to **View → Tool Windows → QueryBook** or click the bookmark icon on the right sidebar.

### Save a query
Three ways to save:
- Select SQL text and press `Ctrl+Alt+Q`
- Select SQL and right-click → **Save to QueryBook**
- Click the capture button in the QueryBook toolbar

The save dialog lets you name the query and choose the target folder.

### Organize queries
- Click **+ New Folder** in the toolbar to create folders
- Drag and drop queries/folders to reorganize
- Drop on the top half of a row to insert before it
- Drop on the bottom half of a folder to move into it

### Run a query
Select a query in the tree and click **Run Query** in the right panel. It opens as a DataGrip scratch file and executes automatically.

### Search
Type in the search box to filter by name. Toggle **SQL** to also search inside query bodies.

## Export & Import

### Export
Click **Export to JSON** in the toolbar. Your entire library is saved as a single portable file.

### Import
Click **Import from JSON** and select a previously exported file. Choose:
- **Merge** — Add imported queries alongside existing ones
- **Replace** — Clear library and replace with imported data

This is useful for backups and sharing with teammates.

### Export format example
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
        "isFolder": false,
        "createdAt": 1744286000000,
        "children": []
      }
    ]
  }
}
```

## Project Structure

```
src/main/kotlin/com/demo/
├── action/
│   ├── SaveQueryAction.kt          - Ctrl+Alt+Q and right-click save
│   └── SaveToQueryBookDialog.kt    - Save dialog and folder picker
├── model/
│   ├── QueryNode.kt                - Data model for queries and folders
│   ├── QueryStorage.kt             - Persistent state management
│   └── QueryEvents.kt              - Event publishing
├── service/
│   └── QueryImportExportService.kt - JSON import/export logic
└── ui/
    ├── QueryToolWindowFactory.kt   - Tool window entry point
    ├── QueryTreePanel.kt           - Left tree panel with search and drag-drop
    └── QueryEditorPanel.kt         - Right panel with SQL editor and run button
```

## Development

### Prerequisites
- JDK 21 or later
- IntelliJ IDEA (any edition) or DataGrip

### Get started
```bash
git clone https://github.com/aborotto/querybook.git
cd querybook
```

### Run locally
```bash
./gradlew runIde          # Opens a sandboxed IDE instance
```

### Build for release
```bash
./gradlew buildPlugin     # Output: build/distributions/QueryBook-*.zip
```

### Verify compatibility
```bash
./gradlew verifyPlugin    # Check against IntelliJ Platform requirements
```

## Roadmap

Planned features in development:

- [ ] **Cloud sharing** — Share and sync queries across devices and team members
- [ ] **Tags / Labels** — Tag queries for cross-folder filtering
- [ ] **Search highlighting** — Highlight matches in the editor when selecting search results
- [ ] **Settings page** — Configure defaults, shortcuts, and UI behavior
- [ ] **Bulk operations** — Multi-select for deleting or moving multiple queries at once
- [ ] **Markdown descriptions** — Render query descriptions as formatted markdown

## License

MIT License — see [LICENSE](LICENSE) for details.

## Contributing

Issues and pull requests are welcome. Please open an issue first to discuss major changes.

