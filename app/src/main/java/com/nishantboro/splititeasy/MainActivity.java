package com.nishantboro.splititeasy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private DrawerLayout drawer;

    private static final int REQUEST_CODE_EXPORT_DB = 1001;
    private static final int REQUEST_CODE_IMPORT_DB = 1002;
    private static final String DB_NAME = "SplitItEasy"; // adjust if necessary

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // set toolbar
        Toolbar toolbar = findViewById(R.id.activity_main_toolbar);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setElevation(0); // removes shadow/elevation between toolbar and status bar
        }
        setTitle("");
        // set drawer
        drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();


        // get view references for "Groups" and "Create New group" Buttons
        View listGroups = findViewById(R.id.listGroups);
        View createNewGroup = findViewById(R.id.createNewGroup);

        // attach click listener to buttons
        listGroups.setOnClickListener(this);
        createNewGroup.setOnClickListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main_menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == R.id.mainMenuShare) {
            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            String shareBody = "Here is the share content body";
            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Subject Here");
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
            startActivity(Intent.createChooser(sharingIntent, "Share via"));
            return true;
        } else if(item.getItemId() == R.id.mainMenuExport) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CODE_EXPORT_DB);
            return true;
        } else if(item.getItemId() == R.id.mainMenuImport) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQUEST_CODE_IMPORT_DB);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_EXPORT_DB && resultCode == Activity.RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            exportDatabase(treeUri);
        } else if (requestCode == REQUEST_CODE_IMPORT_DB && resultCode == Activity.RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            // Show confirmation dialog before importing
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Import Database")
                .setMessage("Warning: Importing will overwrite your current data. Do you want to continue?")
                .setPositiveButton("Yes", (dialog, which) -> importDatabase(fileUri))
                .setNegativeButton("No", null)
                .show();
        }
    }

    private void exportDatabase(Uri treeUri) {
        try {
            // Close Room instance so all changes are written
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            db.close();
            AppDatabase.clearInstance();
            // Count groups (async)
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    AppDatabase db2 = AppDatabase.getInstance(getApplicationContext());
                    int groupCount = db2.groupDao().getAllNonLive().size();
                    Log.i("Export", "Groups before export: " + groupCount);
                } catch (Exception e) {
                    Log.e("Export", "Error counting groups: " + e.getMessage());
                }
            });
            File dbFile = getDatabasePath(DB_NAME);
            if (!dbFile.exists()) {
                Toast.makeText(this, "Database not found", Toast.LENGTH_SHORT).show();
                return;
            }
            Uri dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri));
            String exportFileName = "splititeasy_export.db";
            Uri docUri = DocumentsContract.createDocument(getContentResolver(), dirUri, "application/octet-stream", exportFileName);
            if (docUri == null) {
                Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
                return;
            }
            try (FileInputStream in = new FileInputStream(dbFile);
                 OutputStream out = getContentResolver().openOutputStream(docUri)) {
                if (out == null) throw new IOException("No OutputStream");
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                Toast.makeText(this, "Export successful", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error during export: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importDatabase(Uri fileUri) {
        try {
            // Close Room instance
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            db.close();
            // Set singleton to null (must be enabled in AppDatabase)
            AppDatabase.clearInstance();
            File dbFile = getDatabasePath(DB_NAME);
            try (OutputStream out = new java.io.FileOutputStream(dbFile);
                 java.io.InputStream in = getContentResolver().openInputStream(fileUri)) {
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
                        AppDatabase db2 = AppDatabase.getInstance(getApplicationContext());
                        int groupCount = db2.groupDao().getAllNonLive().size();
                        Log.i("Import", "Groups after import: " + groupCount);
                    } catch (Exception e) {
                        Log.e("Import", "Error counting groups: " + e.getMessage());
                    }
                });
                Toast.makeText(this, "Import successful. Data will be reloaded!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error during import: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // method for handling clicks on our buttons
    @Override
    public void onClick(View v) {
        Intent intent;
        switch(v.getId()) {
            case R.id.listGroups : intent = new Intent(this,GroupListActivity.class);startActivity(intent);break;
            case R.id.createNewGroup: intent = new Intent(this,CreateNewGroupActivity.class);startActivity(intent);break;
            default:break;
        }
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            // close the drawer if user clicks on back button while drawer is open
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}

