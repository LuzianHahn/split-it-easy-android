package com.nishantboro.splititeasy;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executors;

public class DatabaseImportExport {
    private static final String DB_NAME = "SplitItEasy";
    private final Context context;

    public DatabaseImportExport(Context context) {
        this.context = context;
    }

    public void exportDatabase(Uri treeUri) {
        try {
            // Close Room instance so all changes are written
            AppDatabase db = AppDatabase.getInstance(context);
            db.close();
            AppDatabase.clearInstance();
            // Count groups (async)
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    AppDatabase db2 = AppDatabase.getInstance(context);
                    int groupCount = db2.groupDao().getAllNonLive().size();
                    Log.i("Export", "Groups before export: " + groupCount);
                } catch (Exception e) {
                    Log.e("Export", "Error counting groups: " + e.getMessage());
                }
            });
            File dbFile = context.getDatabasePath(DB_NAME);
            if (!dbFile.exists()) {
                Toast.makeText(context, "Database not found", Toast.LENGTH_SHORT).show();
                return;
            }
            Uri dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri));
            String exportFileName = "splititeasy_export.db";
            Uri docUri = DocumentsContract.createDocument(context.getContentResolver(), dirUri, "application/octet-stream", exportFileName);
            if (docUri == null) {
                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show();
                return;
            }
            try (FileInputStream in = new FileInputStream(dbFile);
                 OutputStream out = context.getContentResolver().openOutputStream(docUri)) {
                if (out == null) throw new IOException("No OutputStream");
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                Toast.makeText(context, "Export successful", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Error during export: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public void importDatabase(Uri fileUri) {
        try {
            // Close Room instance
            AppDatabase db = AppDatabase.getInstance(context);
            db.close();
            // Set singleton to null
            AppDatabase.clearInstance();
            File dbFile = context.getDatabasePath(DB_NAME);
            try (OutputStream out = new java.io.FileOutputStream(dbFile);
                 java.io.InputStream in = context.getContentResolver().openInputStream(fileUri)) {
                if (in == null) throw new IOException("No InputStream");
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                // After import: log file size
                Log.i("Import", "DB-File size after import: " + dbFile.length());
                // Delete WAL/SHM files
                File walFile = new File(dbFile.getParent(), DB_NAME + "-wal");
                File shmFile = new File(dbFile.getParent(), DB_NAME + "-shm");
                boolean walDeleted = walFile.delete();
                boolean shmDeleted = shmFile.delete();
                Log.i("Import", "WAL/SHM deleted: " + walDeleted + "/" + shmDeleted);
                // After import: count groups (async)
                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        AppDatabase db2 = AppDatabase.getInstance(context);
                        int groupCount = db2.groupDao().getAllNonLive().size();
                        Log.i("Import", "Groups after import: " + groupCount);
                    } catch (Exception e) {
                        Log.e("Import", "Error counting groups: " + e.getMessage());
                    }
                });
                Toast.makeText(context, "Import successful. Data will be reloaded!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Error during import: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
