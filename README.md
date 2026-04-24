# SQLFolio

A plugin for DataGrip and IntelliJ-based IDEs that lets you save, organize, and reuse SQL snippets without leaving the editor.

## Features

- Folder organization - Organize queries in nested folders
- Drag and drop - Reorder and move queries between folders
- Search - Filter by name or SQL code content
- Execute queries - Run saved queries as scratch files
- Copy to clipboard - Quick SQL copy
- Duplicate - Create query variations
- Export / Import - Portable JSON backups  
- Keyboard shortcuts - Ctrl+Alt+Q to save, Delete to delete, F2 to rename
- Preview - Hover to see query SQL
- Folder counts - Shows child count per folder

## Installation

Download from JetBrains Marketplace or install manually from [Releases](https://github.com/aborotto/querybook/releases).

## Quick Start

1. Open View → Tool Windows → QueryBook
2. Select SQL and press Ctrl+Alt+Q to save
3. Organize queries in folders using drag and drop
4. Run queries directly from the panel

## Keyboard Shortcuts

- Ctrl+Alt+Q: Save to QueryBook
- Delete: Remove item
- F2: Rename item
- Right-click: Context menu

## Development

Prerequisites: JDK 21+, IntelliJ IDEA or DataGrip

```bash
git clone https://github.com/aborotto/querybook.git
cd querybook
./gradlew runIde
```

## Roadmap

- Cloud sharing
- Tags and labels
- Search highlighting
- Settings page
- Bulk operations  
- Markdown in descriptions

## License

MIT License

