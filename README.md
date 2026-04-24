# QueryBook 📖

> **Your personal SQL library, always at your fingertips — right inside DataGrip and IntelliJ-based IDEs.**

Stop losing useful queries in editor history or scattered across files.  
QueryBook lets you save, organise, search and reuse SQL snippets without ever leaving your IDE.

---

## ✨ Features

| Feature | Description |
|---|---|
| 📁 **Folder organisation** | Group related queries in nested folders — unlimited depth |
| 🖱️ **Drag & drop** | Reorder and move queries between folders with a live visual drop indicator |
| 🔍 **Live search** | Filter by name; toggle **SQL mode** to also search inside query bodies |
| ▶ **One-click execution** | Run any saved query directly from the panel as a DataGrip scratch file |
| 📋 **Copy SQL** | Copy a query's SQL to clipboard in a single click |
| 🔁 **Duplicate** | Clone a query as a starting point for variations |
| 📤 **Export / Import** | Back up your library to a portable JSON file and share it with teammates |
| ⌨️ **Keyboard shortcuts** | `Ctrl+Alt+Q` to save, `Delete` to delete, `F2` to rename |
| 💡 **SQL preview tooltip** | Hover any query node to peek at its SQL without opening it |
| 🏷️ **Folder badges** | Folders show a live count of their direct children |

---

## ⌨️ Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+Alt+Q` | Save active editor's SQL (or selection) to QueryBook |
| `Delete` / `Backspace` | Delete selected query or folder |
| `F2` | Rename selected query or folder |
| `Double-click` | Rename selected item |
| Right-click | Open context menu (New, Rename, Delete, Duplicate, Copy SQL) |

---

## 📦 Installation

### From JetBrains Marketplace *(recommended)*
1. Open **Settings → Plugins → Marketplace**
2. Search for **QueryBook**
3. Click **Install** and restart the IDE

### Manual installation
1. Download the latest `.zip` from the [Releases](https://github.com/aborotto/querybook/releases) page
2. Open **Settings → Plugins → ⚙️ → Install Plugin from Disk…**
3. Select the downloaded `.zip` file and restart the IDE

---

## 🚀 Quick Start

### 1 — Open the panel
Go to **View → Tool Windows → QueryBook** (or click the bookmark icon on the right sidebar).

### 2 — Save your first query
Three ways to save a query:

| Method | How |
|---|---|
| **Keyboard** | Select SQL in any editor → `Ctrl+Alt+Q` |
| **Right-click** | Select SQL → right-click → **Save to QueryBook** |
| **Capture button** | Click ⬇ in the QueryBook toolbar to grab the active editor's content |

The save dialog lets you **name** the query and choose the **target folder**.

### 3 — Organise with folders
- Click **+ New Folder** in the toolbar (or right-click in the tree)
- **Drag and drop** queries and folders to reorganise them
  - Drop on the **top half** of a row → insert *before* that item
  - Drop on the **bottom half** of a folder → move *into* the folder

### 4 — Run a query
Select any query in the tree → click **▶ Run Query** in the right panel.  
QueryBook opens it as a scratch SQL file and triggers DataGrip's execute action automatically.

### 5 — Search
Type in the search box to filter by name.  
Click the **SQL** toggle button to also match against query bodies.

---

## 📤 Export & Import

### Export
Click the **↑ Export to JSON** button in the toolbar.  
Your entire library is saved to a single, human-readable `.json` file.

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

### Import
Click the **↓ Import from JSON** button and select a previously exported file.  
Choose between:
- **Merge** — adds imported queries alongside your existing ones
- **Replace** — clears your library and replaces it with the imported data

---

## 🗂️ Project Structure

```
src/
└── main/
    ├── kotlin/com/demo/
    │   ├── action/
    │   │   ├── SaveQueryAction.kt          ← Ctrl+Alt+Q / right-click save
    │   │   └── SaveToQueryBookDialog.kt    ← Name + folder picker dialog
    │   ├── model/
    │   │   ├── QueryNode.kt                ← Data model (queries & folders)
    │   │   ├── QueryStorage.kt             ← Persistent state service
    │   │   └── QueryEvents.kt              ← Message bus event
    │   ├── service/
    │   │   └── QueryImportExportService.kt ← JSON export / import
    │   └── ui/
    │       ├── QueryToolWindowFactory.kt   ← Tool window entry point
    │       ├── QueryTreePanel.kt           ← Left panel: tree + search + DnD
    │       └── QueryEditorPanel.kt         ← Right panel: SQL editor + run
    └── resources/
        └── META-INF/plugin.xml
```

---

## 🔧 Development

### Prerequisites
- **JDK 21** or later
- **IntelliJ IDEA** (any edition) or **DataGrip**

### Run the plugin locally
```bash
git clone https://github.com/aborotto/querybook.git
cd querybook
./gradlew runIde          # opens a sandboxed DataGrip instance
```

### Build the distributable
```bash
./gradlew buildPlugin     # output: build/distributions/QueryBook-*.zip
```

### Verify compatibility
```bash
./gradlew verifyPlugin
```

---

## 🛣️ Roadmap

- [ ] **Tags / Labels** — tag queries with custom labels for cross-folder filtering
- [ ] **Search highlight** — highlight matching text in the editor when a search result is selected
- [ ] **Settings page** — configure default folder, shortcuts, auto-expand behaviour
- [ ] **Multi-select** — select multiple queries for bulk move / delete
- [ ] **Markdown descriptions** — render the notes field as formatted markdown

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

*Built with ❤️ using the [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/)*
