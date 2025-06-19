package com.nishantboro.splititeasy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/* Note that this activity can act as a Add Bill Activity or Edit Bill Activity based on the intent data we receive*/
public class AddEditBillActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    private EditText editTextItem;
    private EditText editTextCost;
    private String currency;
    private String gName;
    private String paidBy;
    private int memberId;
    private int requestCode;
    private int billId;
    private ListView affectedMembersListView;
    private ArrayAdapter<String> affectedMembersAdapter;
    private List<MemberEntity> allMembers = new ArrayList<>();
    private List<Integer> selectedAffectedMemberIds = new ArrayList<>();
    private TextView dateTextView;
    private long selectedDate;

    private void saveExpense() {
        String item = editTextItem.getText().toString();
        String cost = editTextCost.getText().toString();

        // check if the item name or cost is empty
        if (item.trim().isEmpty() || cost.trim().isEmpty()) {
            Toast.makeText(this, "Please enter a valid input", Toast.LENGTH_SHORT).show();
            return;
        }

        BillViewModel billViewModel = ViewModelProviders.of(this,new BillViewModelFactory(getApplication(),gName)).get(BillViewModel.class);

        // Compose affected member IDs as comma-separated string
        StringBuilder affectedIdsBuilder = new StringBuilder();
        for (int i = 0; i < selectedAffectedMemberIds.size(); i++) {
            affectedIdsBuilder.append(selectedAffectedMemberIds.get(i));
            if (i < selectedAffectedMemberIds.size() - 1) {
                affectedIdsBuilder.append(",");
            }
        }
        String affectedIds = affectedIdsBuilder.toString();

        if(requestCode == 1) { // 1 for Add Bill Activity
            // Round up the cost of the bill to 2 decimal places
            BigDecimal decimal = new BigDecimal(cost);
            BigDecimal res = decimal.setScale(2, RoundingMode.HALF_EVEN);

            // store to database
//            Log.d("1", Integer.toString(memberId));
            BillEntity bill = new BillEntity(memberId, item, res.toString(), gName, paidBy);
            bill.affectedMemberIds = affectedIds;
            bill.setDate(selectedDate);
            billViewModel.insert(bill);
        }
        if(requestCode == 2) { // 2 for Edit Bill Activity
            BillEntity bill = new BillEntity(memberId, item, cost, gName, paidBy);
            bill.setId(billId);
            bill.affectedMemberIds = affectedIds;
            bill.setDate(selectedDate);
            /* update the database. note that update operation in billViewModel looks for a row in BillEntity where the value of column("Id")  = billId
               and if found, updates other columns in the row */
            billViewModel.update(bill);
        }

        // updates the group currency
        GroupViewModel groupViewModel = ViewModelProviders.of(this).get(GroupViewModel.class);
        GroupEntity group = new GroupEntity(gName);
        group.gCurrency = currency;
        groupViewModel.update(group);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_new_bill);

        affectedMembersListView = findViewById(R.id.addBillAffectedMembersList);
        affectedMembersAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice);
        affectedMembersListView.setAdapter(affectedMembersAdapter);
        affectedMembersListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        // set toolbar
        Toolbar toolbar = findViewById(R.id.addBillToolbar);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        editTextItem = findViewById(R.id.addBillItemName);
        editTextCost = findViewById(R.id.addBillItemCost);
        // makes sure the user can enter up to 2 decimal places only in item cost field
        editTextCost.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}

            public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}

            public void afterTextChanged(Editable arg0) {
                String str = editTextCost.getText().toString();
                if (str.isEmpty()) return;
                String str2 = PerfectDecimal(str, 20, 2);

                if (!str2.equals(str)) {
                    editTextCost.setText(str2);
                    int pos = editTextCost.getText().length();
                    editTextCost.setSelection(pos);
                }
            }
        });

        // get data from the intent that started this activity
        Intent intent = getIntent();
        gName = intent.getStringExtra(GroupListActivity.EXTRA_TEXT_GNAME);
        // requestCode == 1 identifies an add bill intent and requestCode == 2 identifies an edit Bill intent
        requestCode = intent.getIntExtra("requestCode",0);
        memberId = intent.getIntExtra("billMemberId",-1);
        billId = intent.getIntExtra("billId",-1);
        currency = intent.getStringExtra("groupCurrency");

        // spinner for select currency
        Spinner spinner = findViewById(R.id.addBillItemCurrencySpinner);
        final ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,R.array.currencySymbols,android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        spinner.setOnItemSelectedListener(this);
        // set default spinner currency
        int spinnerPositionCurrency = spinnerAdapter.getPosition(currency);
        spinner.setSelection(spinnerPositionCurrency); // set spinner default selection


        // spinner for selecting paidBy Member
        final Spinner spinnerPaidBy = findViewById(R.id.addBillItemPaidBy);
        final AllMembersSpinnerAdapter allMembersSpinnerAdapter = new AllMembersSpinnerAdapter(this,new ArrayList<MemberEntity>());
        allMembersSpinnerAdapter.setDropDownViewResource(0);
        spinnerPaidBy.setAdapter(allMembersSpinnerAdapter);
        spinnerPaidBy.setOnItemSelectedListener(this);

        // get all current members of the group
        MemberViewModel memberViewModel = ViewModelProviders.of(this,new MemberViewModelFactory(getApplication(),gName)).get(MemberViewModel.class);
        memberViewModel.getAllMembers().observe(this, new Observer<List<MemberEntity>>() {
            @Override
            public void onChanged(List<MemberEntity> memberEntities) {
                allMembers.clear();
                allMembers.addAll(memberEntities);
                affectedMembersAdapter.clear();
                for (MemberEntity m : memberEntities) {
                    affectedMembersAdapter.add(m.name);
                }
                affectedMembersAdapter.notifyDataSetChanged();
                // By default, check all members (old behavior)
                for (int i = 0; i < memberEntities.size(); i++) {
                    affectedMembersListView.setItemChecked(i, true);
                    selectedAffectedMemberIds.add(memberEntities.get(i).id);
                }
                // If editing, restore previous selection
                if(requestCode == 2 && intent.hasExtra("billAffectedMemberIds")) {
                    String affectedIds = intent.getStringExtra("billAffectedMemberIds");
                    if (affectedIds != null && !affectedIds.isEmpty()) {
                        selectedAffectedMemberIds.clear();
                        String[] ids = affectedIds.split(",");
                        for (int i = 0; i < memberEntities.size(); i++) {
                            for (String id : ids) {
                                if (String.valueOf(memberEntities.get(i).id).equals(id)) {
                                    affectedMembersListView.setItemChecked(i, true);
                                    selectedAffectedMemberIds.add(memberEntities.get(i).id);
                                } else {
                                    affectedMembersListView.setItemChecked(i, false);
                                }
                            }
                        }
                    }
                }
            }
        });
        affectedMembersListView.setOnItemClickListener((parent, view, position, id) -> {
            int memberId = allMembers.get(position).id;
            if (affectedMembersListView.isItemChecked(position)) {
                if (!selectedAffectedMemberIds.contains(memberId))
                    selectedAffectedMemberIds.add(memberId);
            } else {
                selectedAffectedMemberIds.remove(Integer.valueOf(memberId));
            }
        });

        // get all current members of the group
        memberViewModel = ViewModelProviders.of(this,new MemberViewModelFactory(getApplication(),gName)).get(MemberViewModel.class);
        memberViewModel.getAllMembers().observe(this, new Observer<List<MemberEntity>>() {
            @Override
            public void onChanged(List<MemberEntity> memberEntities) {
                allMembersSpinnerAdapter.clear();
                allMembersSpinnerAdapter.addAll(memberEntities);
                allMembersSpinnerAdapter.notifyDataSetChanged();

                if(requestCode == 2) {
                    MemberEntity member = new MemberEntity(paidBy,gName);
                    member.setId(memberId);
                    int spinnerPositionPaidBy = allMembersSpinnerAdapter.getPosition(member);
                    spinnerPaidBy.setSelection(spinnerPositionPaidBy);
                }
            }

        });

        if(intent.hasExtra("billId")) {
            // Only edit bill intent sends "billId" with it
            // Get data from the edit bill intent that started this activity
            setTitle("Edit expense");
            editTextItem.setText(intent.getStringExtra("billName")); // set default text received from the intent
            editTextCost.setText(intent.getStringExtra("billCost")); // set default text received from the intent
            paidBy = intent.getStringExtra("billPaidBy");
            String dateStr = intent.getStringExtra("billDate");
            try {
                selectedDate = Long.parseLong(dateStr);
            } catch (NumberFormatException e) {
                selectedDate = System.currentTimeMillis();
            }
        } else {
            setTitle("Add an Expense");
            selectedDate = System.currentTimeMillis();
        }

        // Initialize date picker functionality
        dateTextView = findViewById(R.id.addBillDate);
        updateDateDisplay();

        // Set click listener for date picker
        dateTextView.setOnClickListener(v -> showDatePickerDialog());

        // Make it clear to the user that the date is clickable
        dateTextView.setBackgroundResource(android.R.drawable.editbox_background);
        dateTextView.setPadding(10, 10, 10, 10);
    }

    private void showDatePickerDialog() {
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(selectedDate);
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    Calendar calendar = Calendar.getInstance();
                    calendar.set(selectedYear, selectedMonth, selectedDay);
                    selectedDate = calendar.getTimeInMillis();
                    updateDateDisplay();
                }, year, month, day);
        datePickerDialog.show();
    }

    private void updateDateDisplay() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN);
        dateTextView.setText(dateFormat.format(new Date(selectedDate)));
    }

    public String PerfectDecimal(String str, int MAX_BEFORE_POINT, int MAX_DECIMAL){
        if(str.charAt(0) == '.') str = "0"+str;
        int max = str.length();

        StringBuilder rFinal = new StringBuilder();
        boolean after = false;
        int i = 0, up = 0, decimal = 0; char t;
        while(i < max){
            t = str.charAt(i);
            if(t != '.' && !after){
                up++;
                if(up > MAX_BEFORE_POINT) return rFinal.toString();
            }else if(t == '.'){
                after = true;
            }else{
                decimal++;
                if(decimal > MAX_DECIMAL)
                    return rFinal.toString();
            }
            rFinal.append(t);
            i++;
        }return rFinal.toString();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.add_bill_action_bar_menu,menu);
        return true;
    }

    // call this method when an option in the menu is selected
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == R.id.addBillToolbarMenu) {
            saveExpense();
        }
        finish(); // if the user clicks on back button close this activity
        return true;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        switch (parent.getId()) {
            case R.id.addBillItemCurrencySpinner:
                currency = parent.getItemAtPosition(position).toString();
                break;
//                Log.d("p", "selected currency");
            case R.id.addBillItemPaidBy:
//                Log.d("t", "selected paidBy");
                MemberEntity member = (MemberEntity) parent.getItemAtPosition(position);
                paidBy = member.name;
                memberId = member.id;
                break;
            default:break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}
