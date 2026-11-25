# Engineering Blueprint: Native Android Root App for Primary Google Account & Play Store Ownership Management

## 1. Project Identity & Core Objective
Build a native Android root utility application (written in **Kotlin** + **Jetpack Compose**) that provides a safe, graphical interface to perform two root-level operations verified on **Android 16 (SDK 36)** (with backward compatibility for Android 10–15):
1. **System-Wide Primary Google Account Switcher:** Atomically swaps the `_id` order of Google accounts in Android's core account databases (`accounts_ce.db` and `accounts_de.db`) so any chosen Google account becomes the primary (`index 0`) system account without removing accounts or invalidating OAuth sessions.
2. **Per-App Google Play Store Ownership Manager:** Reassigns the owner account of specific installed applications (or batches of applications) inside Google Play Store's internal databases (`localappstate.db` and `library.db`) to control In-App Purchases (IAP/Billing), licensing, and app update channels—taking effect immediately without rebooting.

---

## 2. Recommended Tech Stack & Dependencies

| Layer | Technology / Library | Engineering Rationale |
| :--- | :--- | :--- |
| **Language** | **Kotlin (2.0+)** | Type-safe, coroutine-first asynchronous execution for root I/O and UI state management. |
| **UI Framework** | **Jetpack Compose (Material 3)** | Reactive UI for account lists, installed app lists with icons, multi-select batch actions, and backup management. |
| **Root IPC Layer** | **`com.github.topjohnwu.libsu`** (`core`, `service`, `nio`) | Industry standard for Magisk, KernelSU, and APatch. Provides a persistent `RootService` running as `uid=0` over AIDL/Binder. |
| **SQLite Engine** | **Android Built-in `android.database.sqlite.SQLiteDatabase`** | Executing SQLite mutations inside `RootService` on staged copies in cache avoids bundling external `sqlite3` binaries and eliminates dynamic linker symbol collisions (`LD_LIBRARY_PATH` vs `/system/lib64/libunwindstack.so`). |
| **App Metadata** | **Android `PackageManager`** | Resolves human-readable app labels and icons in the non-root UI process to enrich raw `package_name` rows from Play Store DBs. |
| **Concurrency** | **Kotlin Coroutines & `StateFlow`** | Offloads file copying, WAL checkpointing, SQLite transactions, and SELinux restoration off the main thread. |

### Gradle Dependencies Reference (`libs.versions.toml` / `build.gradle.kts`)
```kotlin
dependencies {
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("com.github.topjohnwu.libsu:service:6.0.0")
    implementation("com.github.topjohnwu.libsu:nio:6.0.0")
    // Plus standard Jetpack Compose Material 3, ViewModel, and Coroutines libraries
}
```

---

## 3. System Architecture (IPC & Root Boundary)

The application must strictly separate the **Unprivileged UI Process** from the **Privileged `RootService` Process (`uid=0`)**:

```text
+-------------------------------------------------------------------+
| Unprivileged App Process (UI / ViewModel / PackageManager)        |
| - Displays accounts, installed apps + icons, and backup snapshots |
| - Binds to RootAccountService via libsu RootService.bind()        |
+---------------------------------+---------------------------------+
                                  | AIDL / Binder IPC
                                  v
+-------------------------------------------------------------------+
| Privileged RootService Process (uid=0, SELinux: su / magisk)      |
| - Copies target DBs to isolated staging directory (/data/local/tmp)|
| - Opens staged DBs via SQLiteDatabase.openDatabase()              |
| - Runs WAL checkpoints, atomic transactions, & integrity_check    |
| - Restores original uid:gid, chmod 660, and SELinux labels        |
+-------------------------------------------------------------------+
```

### 3.1 AIDL Contract (`IRootAccountService.aidl`)
```aidl
package com.example.accountowner;

interface IRootAccountService {
    // Inspection
    String getGoogleAccountsJson();
    String getPlayAppsOwnershipJson();
    String getPlayActiveAccount();

    // Core Operations
    String swapPrimaryGoogleAccount(int currentPrimaryId, int targetId);
    String transferPlayAppOwnership(in List<String> packageNames, String targetEmail);
    String clearAppOAuthBindings(String packageName);

    // Backup & Restore
    String listBackupsJson();
    String restoreBackup(String backupFolderName);
    boolean deleteBackup(String backupFolderName);

    // Power Actions
    void softReboot();
    void fullReboot();
}
```

---

## 4. Module 1: System Primary Google Account Swapper

### 4.1 Verified Android 16 Mechanics
* Android's `accounts` tables have **no** `is_primary` column.
* At boot, `AccountManagerService` reads `/data/system_ce/0/accounts_ce.db` and `/data/system_de/0/accounts_de.db` ordered by `_id ASC` and caches them in RAM.
* The Google account (`type = 'com.google'`) with the **lowest `_id`** (`index 0`) acts as the system-wide default account.
* **RAM Caching Constraint:** Modifying these DBs on disk while `system_server` is running does not update the active session until a **Soft Reboot (`stop; start`)** or **Full Reboot (`reboot`)** occurs. If the device runs too long after disk modification without rebooting, `system_server` may overwrite the DB with stale in-memory data. The UI must prominently prompt the user to reboot immediately after a swap.

### 4.2 Verified Android 16 Database Schemas

1. **Credential Encrypted DB (`/data/system_ce/0/accounts_ce.db`):**
   * `accounts`: `_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, type TEXT NOT NULL, password TEXT, UNIQUE(name,type)`
   * `authtokens`: `_id INTEGER PRIMARY KEY AUTOINCREMENT, accounts_id INTEGER NOT NULL, type TEXT NOT NULL, authtoken TEXT, UNIQUE (accounts_id,type)`
   * `extras`: `_id INTEGER PRIMARY KEY AUTOINCREMENT, accounts_id INTEGER, key TEXT NOT NULL, value TEXT, UNIQUE(accounts_id,key)`

2. **Device Encrypted DB (`/data/system_de/0/accounts_de.db`):**
   * `accounts`: `_id INTEGER PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, previous_name TEXT, last_password_entry_time_millis_epoch INTEGER DEFAULT 0, UNIQUE(name,type)`
   * `grants`: `accounts_id INTEGER NOT NULL, auth_token_type STRING NOT NULL, uid INTEGER NOT NULL, UNIQUE (accounts_id,auth_token_type,uid)`
   * `visibility`: `accounts_id INTEGER NOT NULL, _package TEXT NOT NULL, value INTEGER, PRIMARY KEY(accounts_id,_package)`
   > **Critical Schema Rule:** Android 16 `accounts_de.db` uses **`visibility`** (linked via `accounts_id`) and does **NOT** contain an `extras` table. Always check `sqlite_master` dynamically before running `UPDATE` on auxiliary tables (`authtokens`, `extras`, `grants`, `visibility`) to ensure cross-version safety.

### 4.3 Step-by-Step Safe Swap Algorithm
1. **Dynamic ID Resolution:** Never hardcode account IDs; query `SELECT _id, name FROM accounts WHERE type='com.google' ORDER BY _id ASC` at runtime to get `FIRST_ID` (`FIRST_EMAIL`) and `TARGET_ID` (`TARGET_EMAIL`). Verify both IDs exist and match in both `accounts_ce.db` and `accounts_de.db`.
2. **Backup & Staging:**
   * Create timestamped backup in `/data/adb/account_owner_backups/sys_<timestamp>/` containing `accounts_ce.db*`, `accounts_de.db*`, and `finsky.xml`.
   * Copy `accounts_ce.db*` and `accounts_de.db*` to `/data/local/tmp/acc_stage/`.
3. **Checkpoint WAL:** Execute `PRAGMA wal_checkpoint(TRUNCATE);` on both staged DBs.
4. **Atomic ID Swap Transaction:**
   * Compute `TEMP_ID = MAX(_id) + 1000` (e.g., `9999`) to guarantee no primary key collision.
   * Inside `db.beginTransaction()` / `db.setTransactionSuccessful()` / `db.endTransaction()`:
     * **CE DB:** Swap `_id` in `accounts`, and `accounts_id` in `authtokens` and `extras` (`FIRST_ID -> TEMP_ID`, `TARGET_ID -> FIRST_ID`, `TEMP_ID -> TARGET_ID`).
     * **DE DB:** Swap `_id` in `accounts`, and `accounts_id` in `grants` and `visibility` (`FIRST_ID -> TEMP_ID`, `TARGET_ID -> FIRST_ID`, `TEMP_ID -> TARGET_ID`).
   * Run `PRAGMA wal_checkpoint(TRUNCATE);` again so all data resides cleanly inside the main `.db` file.
5. **Integrity Verification:** Run `PRAGMA integrity_check;`. Abort immediately if the output is not `"ok"`.
6. **Atomic File Replacement & Security Context Restoration:**
   * Delete existing `-wal` and `-shm` files in `/data/system_ce/0/` and `/data/system_de/0/`.
   * Move staged `.db` files into `/data/system_ce/0/accounts_ce.db` and `/data/system_de/0/accounts_de.db`.
   * Restore exact ownership, permissions, and SELinux context:
     ```sh
     chown system:system /data/system_ce/0/accounts_ce.db /data/system_de/0/accounts_de.db
     chmod 660 /data/system_ce/0/accounts_ce.db /data/system_de/0/accounts_de.db
     chcon u:object_r:system_data_file:s0 /data/system_ce/0/accounts_ce.db /data/system_de/0/accounts_de.db
     restorecon -v /data/system_ce/0/accounts_ce.db /data/system_de/0/accounts_de.db
     ```
7. **Sync Google Play Active Account:**
   * In `/data/data/com.android.vending/shared_prefs/finsky.xml`, replace `<string name="account">FIRST_EMAIL</string>` with `<string name="account">TARGET_EMAIL</string>`, preserving original file `uid:gid`, mode, and SELinux label.

---

## 5. Module 2: Per-App Google Play Ownership Manager

### 5.1 Verified Play Store Database Structure
* **Location:** `/data/data/com.android.vending/databases/`
* **Journaling Mode:** `localappstate.db` and `library.db` use classic rollback `-journal` files (not WAL).
* **Lifecycle Constraint:** Execute `am force-stop com.android.vending` before reading/modifying the DBs, and execute `am force-stop com.android.vending` again afterward so Play Store reloads the updated ownership on next launch. **No device reboot is required.**

#### Table 1: `localappstate.db` $\rightarrow$ `appstate`
* `package_name` (`STRING`, Primary Key, cid `0`)
* `account` (`STRING`, cid `9`): Controls Play Store ownership, licensing, and In-App Billing account selection.
* `account_for_update` (`STRING`, cid `15`): Controls which Google account Play Store queries for app updates.

#### Table 2: `library.db` $\rightarrow$ `ownership`
* **6-Column Composite Primary Key:** `(account, library_id, backend, doc_id, doc_type, offer_type)`
  * `account` (`STRING`, pk `1`)
  * `library_id` (`STRING`, pk `2`, e.g., `'u-wl'`)
  * `backend` (`INTEGER`, pk `3`, e.g., `3`)
  * `doc_id` (`STRING`, pk `4`, equals `package_name`)
  * `doc_type` (`INTEGER`, pk `5`, e.g., `1`)
  * `offer_type` (`INTEGER`, pk `6`, e.g., `1`)

### 5.2 Real-World Edge Cases & Mandatory SQL Logic
1. **Multi-Account Duplicate Rows in `library.db`:**
   * Because `account` is part of the 6-column primary key, a single app (e.g., `com.whatsapp`) can simultaneously have multiple rows in `ownership` under different Google accounts on the same phone.
   * A direct `UPDATE ownership SET account = ? WHERE doc_id = ?` will throw `SQLiteConstraintException: UNIQUE constraint failed` if the target account already owns that app in `library.db`.
   * **Mandatory Pattern:** Always execute `UPDATE OR IGNORE` followed by `DELETE` of any remaining non-target rows for that `doc_id`.
2. **Sideloaded / Unlinked Apps:**
   * Apps installed via APK outside Play Store (e.g., `com.whatsapp.w4b`) appear in `appstate` with `account` as `NULL` or `""`. Updating `account` in `appstate` links the sideloaded app to the chosen Google account.

```sql
-- 1. Update localappstate.db (staged copy)
UPDATE appstate
SET account = ?,
    account_for_update = CASE
        WHEN account_for_update IS NOT NULL AND account_for_update != ''
        THEN ?
        ELSE account_for_update
    END
WHERE package_name = ?;

-- 2. Update library.db (staged copy)
UPDATE OR IGNORE ownership
SET account = ?
WHERE doc_id = ?;

DELETE FROM ownership
WHERE doc_id = ?
  AND account != ?;
```

### 5.3 Preserving Play Store File Ownership
* Dynamically read the owner UID/GID of `/data/data/com.android.vending/databases` (e.g., `u0_a173:u0_a173` via `stat -c "%u:%g"`).
* After moving modified `localappstate.db` and `library.db` back and removing stale `-journal` files, run `chown <uid>:<gid>`, `chmod 660`, and `restorecon -v` on all touched database files.

---

## 6. Module 3: Optional Per-App OAuth Binding Reset (`accounts_ce.db`)
If an app has already completed Google Sign-In (OAuth2) and continues to use an old account despite Play Store ownership transfer:
* Cached OAuth tokens are stored in `accounts_ce.db` $\rightarrow$ `authtokens` where `type LIKE 'perm.<package_name>:%'` or `type LIKE 'EXP:<package_name>:%'`.
* Deleting those rows for `<package_name>` (and optionally clearing the target app's cache/preferences) forces the app to re-prompt Google Sign-In using the new primary account.

---

## 7. Safety & Error-Handling Checklist for the Agent
1. **Never Run System Binaries with Modified `LD_LIBRARY_PATH`:** Do not inject Termux or custom library paths into root shells invoking `am`, `cmd`, `pm`, or `restorecon`.
2. **Staged Transactions Only:** Never open live `/data/system_ce/0/*` or `/data/data/com.android.vending/databases/*` directly in read-write mode. Always copy to `/data/local/tmp/`, mutate via Android's `SQLiteDatabase`, validate `PRAGMA integrity_check == "ok"`, and atomically replace.
3. **Automatic Snapshots:** Keep at least the last 5 timestamped backups before every mutation, allowing 1-click restoration from the UI.
4. **Power Actions:**
   * **Soft Reboot:** Execute `setprop ctl.restart zygote` or `stop; start` inside a detached `nohup` background subshell so killing the app's own UI process does not terminate the `start` command.
   * **Full Reboot:** Execute `svc power reboot` or `reboot`.

---

## 8. UI / UX Screen Blueprint (Jetpack Compose)
1. **Screen 1: Primary Account (`AccountsScreen`)**
   * Top highlight card showing the active Primary Account (`index 0`, lowest `_id`) and Play Store active `finsky.xml` account.
   * List of all registered `com.google` accounts with their `_id` badges and a **"Set as Primary"** button.
   * Sticky warning banner after a swap: *"Pending Reboot: Restart device or UI to apply primary account change in RAM"* with **[Soft Reboot]** and **[Full Reboot]** buttons.
2. **Screen 2: App Ownership (`AppsScreen`)**
   * Search bar + Filter chips (`All Apps`, `Unassigned / Sideloaded`, or filter by specific Google email).
   * App rows displaying App Icon, App Name, `package_name`, current `account` badge, and `account_for_update` badge.
   * Single-tap owner picker dialog + Multi-select mode for batch ownership transfer to a chosen Google account.
3. **Screen 3: Backups & Snapshots (`BackupsScreen`)**
   * Chronological list of automatic pre-mutation snapshots (`System Account Swap` vs `Play Ownership Backup`) with **[Restore]** and **[Delete]** actions.