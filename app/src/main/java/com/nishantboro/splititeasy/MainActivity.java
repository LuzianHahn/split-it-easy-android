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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private DrawerLayout drawer;
    private DatabaseImportExport databaseImportExport;

    private static final int REQUEST_CODE_EXPORT_DB = 1001;
    private static final int REQUEST_CODE_IMPORT_DB = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        databaseImportExport = new DatabaseImportExport(this);

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
            databaseImportExport.exportDatabase(treeUri);
        } else if (requestCode == REQUEST_CODE_IMPORT_DB && resultCode == Activity.RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            // Show confirmation dialog before importing
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Import Database")
                .setMessage("Warning: Importing will overwrite your current data. Do you want to continue?")
                .setPositiveButton("Yes", (dialog, which) -> databaseImportExport.importDatabase(fileUri))
                .setNegativeButton("No", null)
                .show();
        }
    }

    // method for handling clicks on our buttons
    @Override
    public void onClick(View v) {
        Intent intent;
        int id = v.getId();
        if (id == R.id.listGroups) {
            intent = new Intent(this, GroupListActivity.class);
            startActivity(intent);
        } else if (id == R.id.createNewGroup) {
            intent = new Intent(this, CreateNewGroupActivity.class);
            startActivity(intent);
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
