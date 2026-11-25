package com.playswitch.ziadzero;

import android.os.ParcelFileDescriptor;

interface IRootAccountService {
    // Inspection
    String getDashboardSnapshotJson();
    String checkEnvironmentJson();
    String getGoogleAccountsJson();
    String getPlayAppsOwnershipJson();
    String getPlayActiveAccount();

    // Backup, Restore, Export & Import
    String listBackupsJson();
    String createManualBackup(String kind);
    String restoreBackup(String backupFolderName);
    boolean deleteBackup(String backupFolderName);
    ParcelFileDescriptor openBackupExportZip(String backupFolderName);
    String importBackupZip(in ParcelFileDescriptor zipFd);

    // Core Operations
    String swapPrimaryGoogleAccount(int targetId);
    String transferPlayAppOwnership(in List<String> packageNames, String targetEmail, String mode);
    String clearAppOAuthBindings(String packageName);

    // Power Actions
    void softReboot();
    void fullReboot();
}
