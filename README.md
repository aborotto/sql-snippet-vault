# SQLFolio

> **Your SQL query library, inside your IDE — shared with your team in real time.**

SQLFolio is a plugin for **DataGrip** and all IntelliJ-based IDEs that lets you save, organize, and reuse SQL snippets without leaving the editor. Connect a shared database and your entire team sees the same library, live.

---

## ✨ Features

### 📁 Query Library

| Feature | Description |
|---------|-------------|
| **Unlimited folders** | Create nested folders to any depth — no limits |
| **Drag & drop** | Reorder queries and move them between folders with mouse drag |
| **Rename** | Double-click or press `F2` to rename any item inline |
| **Delete** | Press `Delete` / `Backspace` or use the toolbar/context menu |
| **Duplicate** | Clone a query (automatically names it "Original (copy)") |
| **Folder child count** | Folders show how many items they contain |
| **Collapse all** | Collapse the entire tree with one click |
| **Export to JSON** | Export your full library to a portable `.json` file |
| **Import from JSON** | Restore a previously exported library |

### 🔍 Search

| Feature | Description |
|---------|-------------|
| **Search by name** | Filter the tree live as you type |
| **Search in SQL** | Toggle to also match inside query bodies |
| **Preview on hover** | Hover over any query to see the first 6 lines of SQL as a tooltip |
| **Context menu** | Right-click any item to rename, delete, duplicate or copy SQL |

### ⚡ Editor Integration

| Feature | Description |
|---------|-------------|
| **Run Query** | Executes the saved query directly as a scratch `.sql` file |
| **Copy SQL** | Copies the full SQL to clipboard with one click |
| **Capture from editor** | Saves selected SQL (or full editor content) via `Ctrl+Alt+Q` or toolbar button |
| **SQL syntax highlighting** | Full syntax-highlighted SQL editor with IntelliJ's language engine |
| **Description / notes** | Each query has a free-text notes field below the editor |

### 🔄 Team Sync (BYOB — Bring Your Own Backend)

Connect your own database and share your query library with your team in real time. No cloud, no subscriptions — your data stays on your infrastructure.

#### Supported backends

| Backend | Latency | Best for |
|---------|---------|----------|
| **PostgreSQL** | ≤ 200 ms | Teams with a shared DB — uses LISTEN/NOTIFY |
| **SQLite** | ≤ 1 s | Local teams / single machine — uses file watcher |
| **REST API** | Configurable | Custom server implementing the SQLFolio API |

#### How sync works

| Step | What happens |
|------|-------------|
| **Auto-push** | Every edit triggers a push **2 seconds** after your last keystroke (debounced) |
| **Auto-pull** | Changes from teammates arrive via PostgreSQL LISTEN/NOTIFY (≤ 200 ms) or polling |
| **Reconnect on save** | Clicking OK in Settings reconnects immediately — no IDE restart needed |
| **Conflict detection** | If two users edit simultaneously, a dialog asks: *Keep Mine* or *Take Server* |
| **Version history** | Every push archives the previous snapshot — configurable retention (default: last 10) |
| **Payload size limit** | Pushes are blocked if the JSON exceeds the configured limit (default: 5 MB) |

#### Database setup

The plugin auto-creates two tables on first sync:

```sql
-- Current live state (one row per workspace — always the full tree)
CREATE TABLE IF NOT EXISTS sqlfolio_workspaces (
    id      TEXT    PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    data    TEXT    NOT NULL
);

-- Rolling version history for rollback (last N snapshots)
CREATE TABLE IF NOT EXISTS sqlfolio_workspace_history (
    workspace_id TEXT    NOT NULL,
    version      INTEGER NOT NULL,
    data         TEXT    NOT NULL,
    saved_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY  (workspace_id, version)
);
```

> **Custom schema:** If your tables should live in a non-default schema (e.g. `project0`), enter it in the **Schema** field — the plugin will prefix all queries automatically.

#### Settings overview

| Field | Description |
|-------|-------------|
| **Workspace ID** | Shared name for your team's library. Click **Browse…** to pick an existing one from the DB |
| **Schema** | PostgreSQL schema for the tables (leave blank for `public`) |
| **Keep last N versions** | How many rollback snapshots to retain (1–100, default: 10) |
| **Max payload (KB)** | Maximum JSON size before a push is blocked (default: 5120 KB = 5 MB) |
| **Poll interval (sec)** | Fallback polling interval (PostgreSQL also uses real-time LISTEN/NOTIFY) |
| **Test Connection** | Checks DB connectivity only — never writes anything |
| **Create Tables Now** | Explicitly creates the two tables in the configured schema |

---

## 🚀 Installation

**From JetBrains Marketplace:**
1. Open Settings → Plugins → Marketplace
2. Search for **SQLFolio**
3. Install and restart

**Manual install:**
Download the `.zip` from [Releases](https://github.com/aborotto/sql-snippet-vault/releases) and install via Settings → Plugins → ⚙️ → Install Plugin from Disk.

---

## ⚙️ Quick Start

1. Open **View → Tool Windows → SQLFolio**
2. Select SQL in any editor and press `Ctrl+Alt+Q` to save it
3. Organize queries into folders with drag & drop
4. Click **Run Query** to execute directly

---

## 🔄 Setting Up Team Sync

### Self-Hosted Sync (BYOB)

1. Go to **Settings → Tools → SQLFolio Sync**
2. Choose your backend: **PostgreSQL** (recommended), SQLite, or REST API
3. Enter your JDBC URL, credentials, and schema name
4. Click **Browse…** next to Workspace ID to pick an existing workspace (or type a new name)
5. Click **Test Connection** to verify connectivity
6. Click **Create Tables Now** to initialize the tables in your schema
7. Enable sync → **OK**

> Everyone on the team must use the **same Workspace ID** to share the same library.

---

## ☁️ Cloud Sharing Setup

SQLFolio supports cloud-hosted databases for seamless team collaboration. Choose a cloud provider and configure your sync settings.

### Cloud Providers (Recommended)

#### **1. PostgreSQL Cloud**

**AWS RDS (PostgreSQL)**
1. Create a PostgreSQL database on AWS RDS
2. Copy the endpoint (e.g., `mydb.c9akciq32.us-east-1.rds.amazonaws.com`)
3. In SQLFolio Settings, use JDBC URL: `jdbc:postgresql://mydb.c9akciq32.us-east-1.rds.amazonaws.com:5432/queryvolume`
4. Enter your RDS admin username and password
5. Set Schema to `public` (or your desired schema)
6. Click **Test Connection** → **Create Tables Now**
7. Share the Workspace ID with your team

**Azure Database for PostgreSQL**
1. Create a PostgreSQL server on Azure
2. Copy the server name (e.g., `myserver.postgres.database.azure.com`)
3. In SQLFolio Settings, use JDBC URL: `jdbc:postgresql://myserver.postgres.database.azure.com:5432/querydb`
4. Enter your Azure credentials
5. Click **Test Connection** → **Create Tables Now**
6. Share the Workspace ID with your team

**Google Cloud SQL (PostgreSQL)**
1. Create a Cloud SQL PostgreSQL instance
2. Get the public IP address and configure authorized networks
3. In SQLFolio Settings, use JDBC URL: `jdbc:postgresql://<PUBLIC_IP>:5432/sqlfolio_db`
4. Enter your Cloud SQL credentials
5. Click **Test Connection** → **Create Tables Now**
6. Share the Workspace ID with your team

#### **2. SQLite Cloud**

For smaller teams, SQLite with cloud storage:

1. Store your SQLite database (`.db` file) in:
   - **Dropbox** → Configure sync folder path
   - **OneDrive** → Configure sync folder path
   - **Google Drive** → Configure sync folder path (via Drive for Desktop)
2. In SQLFolio Settings, select SQLite backend
3. Point to the synced database file path
4. Set Poll Interval to **3-5 seconds** for cloud sync
5. Share the file path with your team members

> **Note:** SQLite cloud requires the file to be on a synced folder accessible to all team members.

#### **3. REST API (Custom Backend)**

For teams with custom infrastructure:

1. Implement the [SQLFolio REST API spec](https://github.com/aborotto/sql-snippet-vault/wiki/REST-API)
2. Deploy to your cloud server
3. In SQLFolio Settings, select REST API backend
4. Enter the API base URL and authentication token (if required)
5. Click **Test Connection**
6. Share the API URL and token securely with your team

---

## 🔐 Security Best Practices

- **Never commit credentials** to version control
- **Use strong passwords** for cloud databases
- **Enable SSL/TLS** connections (PostgreSQL: `sslmode=require`)
- **Restrict network access** to your database (IP whitelisting)
- **Use IAM roles** where available (AWS, Azure, GCP)
- **Rotate credentials** regularly
- **Back up the tables** (`sqlfolio_workspaces`, `sqlfolio_workspace_history`) regularly

---

## ⌨️ Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+Alt+Q` | Save current SQL selection (or full editor) to SQLFolio |
| `Delete` / `Backspace` | Delete selected item |
| `F2` | Rename selected item |
| `Double-click` | Rename selected item |
| Right-click | Context menu: rename, delete, duplicate, copy SQL |

---

## 🛠️ Development

**Prerequisites:** JDK 21+, IntelliJ IDEA or DataGrip

```bash
git clone https://github.com/aborotto/sql-snippet-vault.git
cd sql-snippet-vault
./gradlew runIde
```

---

## 📄 License

MIT License
