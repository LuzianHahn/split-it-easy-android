package com.nishantboro.splititeasy;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;


public class BalancesTabFragment extends Fragment {
    public String gName; // group name
    private String currency;
    private List<MemberEntity> members = new ArrayList<>();
    private List<HashMap<String,Object>> results = new ArrayList<>();
    private BalancesTabViewAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private TextView header;
    private List<BillEntity> currentBills = new ArrayList<>();

    private void calculateBalances(PriorityQueue<Balance> debtors,PriorityQueue<Balance> creditors) {
        if(getActivity() == null) {
            return;
        }
        // BillViewModel billViewModel = ViewModelProviders.of(this,new BillViewModelFactory(getActivity().getApplication(),gName)).get(BillViewModel.class);
        List<BigDecimal> preBalances = new ArrayList<>();
        BigDecimal sum = new BigDecimal("0");
        // Prepare a map to track how much each member has paid and owes
        HashMap<Integer, BigDecimal> paidMap = new HashMap<>();
        HashMap<Integer, BigDecimal> owesMap = new HashMap<>();
        for (MemberEntity member : members) {
            paidMap.put(member.id, BigDecimal.ZERO);
            owesMap.put(member.id, BigDecimal.ZERO);
        }
        // For each bill, split cost only among affected members
        for (BillEntity bill : currentBills) {
            // Parse affected members
            List<Integer> affectedIds = new ArrayList<>();
            if (bill.affectedMemberIds != null && !bill.affectedMemberIds.isEmpty()) {
                String[] ids = bill.affectedMemberIds.split(",");
                for (String id : ids) {
                    try { affectedIds.add(Integer.parseInt(id)); } catch (Exception ignored) {}
                }
            }
            if (affectedIds.isEmpty()) continue; // skip if no affected members
            BigDecimal billCost = new BigDecimal(bill.cost);
            BigDecimal split = billCost.divide(new BigDecimal(affectedIds.size()), 2, RoundingMode.HALF_EVEN);
            // Add to paid for the payer
            BigDecimal paid = paidMap.getOrDefault(bill.mid, BigDecimal.ZERO);
            paidMap.put(bill.mid, paid.add(billCost));
            // Add to owes for each affected member
            for (int id : affectedIds) {
                BigDecimal owes = owesMap.getOrDefault(id, BigDecimal.ZERO);
                owesMap.put(id, owes.add(split));
            }
        }
        // Calculate balances
        for (MemberEntity member : members) {
            BigDecimal paid = paidMap.getOrDefault(member.id, BigDecimal.ZERO);
            BigDecimal owes = owesMap.getOrDefault(member.id, BigDecimal.ZERO);
            BigDecimal balance = owes.subtract(paid); // positive: owes, negative: is owed
            if (balance.compareTo(new BigDecimal("0.49")) > 0) {
                creditors.add(new Balance(balance, member.name));
            } else if (balance.compareTo(new BigDecimal("-0.49")) < 0) {
                debtors.add(new Balance(balance.negate(), member.name));
            }
        }
    }

    private void calculateTransactions() {
        results.clear(); // remove previously calculated transactions before calculating again
        PriorityQueue<Balance> debtors = new PriorityQueue<>(members.size(),new BalanceComparator()); // debtors are members of the group who are owed money
        PriorityQueue<Balance> creditors = new PriorityQueue<>(members.size(),new BalanceComparator()); // creditors are members who have to pay money to the group

        calculateBalances(debtors,creditors);

        /*Algorithm: Pick the largest element from debtors and the largest from creditors. Ex: If debtors = {4,3} and creditors={2,7}, pick 4 as the largest debtor and 7 as the largest creditor.
        * Now, do a transaction between them. The debtor with a balance of 4 receives $4 from the creditor with a balance of 7 and hence, the debtor is eliminated from further
        * transactions. Repeat the same thing until and unless there are no creditors and debtors.
        *
        * The priority queues help us find the largest creditor and debtor in constant time. However, adding/removing a member takes O(log n) time to perform it.
        * Optimisation: This algorithm produces correct results but the no of transactions is not minimum. To minimize it, we could use the subset sum algorithm which is a NP problem.
        * The use of a NP solution could really slow down the app! */
        while(!creditors.isEmpty() && !debtors.isEmpty()) {
            Balance rich = creditors.peek(); // get the largest creditor
            Balance poor = debtors.peek(); // get the largest debtor
            if(rich == null || poor == null) {
                return;
            }
            String richName = rich.name;
            BigDecimal richBalance = rich.balance;
            creditors.remove(rich); // remove the creditor from the queue

            String poorName = poor.name;
            BigDecimal poorBalance = poor.balance;
            debtors.remove(poor); // remove the debtor from the queue

            BigDecimal min = richBalance.min(poorBalance);

            // calculate the amount to be send from creditor to debtor
            richBalance = richBalance.subtract(min);
            poorBalance = poorBalance.subtract(min);

            HashMap<String,Object> values = new HashMap<>(); // record the transaction details in a HashMap
            values.put("sender",richName);
            values.put("recipient",poorName);
            values.put("amount",currency.charAt(5) + min.toString());

            results.add(values);

            // Consider a member as settled if he has an outstanding balance between 0.00 and 0.49 else add him to the queue again
            int compare = 1;
            if(poorBalance.compareTo(new BigDecimal("0.49")) == compare) {
                // if the debtor is not yet settled(has a balance between 0.49 and inf) add him to the priority queue again so that he is available for further transactions to settle up his debts
                debtors.add(new Balance(poorBalance,poorName));
            }

            if(richBalance.compareTo(new BigDecimal("0.49")) == compare) {
                // if the creditor is not yet settled(has a balance between 0.49 and inf) add him to the priority queue again so that he is available for further transactions
                creditors.add(new Balance(richBalance,richName));
            }
        }
    }

    static BalancesTabFragment newInstance(String gName) {
        Bundle args = new Bundle();
        args.putString("group_name", gName);
        BalancesTabFragment f = new BalancesTabFragment();
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.balances_fragment,container,false);
        if(getArguments() == null || getActivity() == null) {
            return view;
        }
        gName = getArguments().getString("group_name"); // get group name from bundle

        recyclerView = view.findViewById(R.id.balancesRecyclerView);
        emptyView = view.findViewById(R.id.no_data);
        header = view.findViewById(R.id.balancesHeader);
        recyclerView.setHasFixedSize(true);
        adapter = new BalancesTabViewAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setAdapter(adapter);

        // Observe bills using LiveData
        BillViewModel billViewModel = ViewModelProviders.of(this,new BillViewModelFactory(getActivity().getApplication(),gName)).get(BillViewModel.class);
        billViewModel.getAllBills().observe(getViewLifecycleOwner(), bills -> {
            currentBills.clear();
            if (bills != null) currentBills.addAll(bills);
            runCalculations();
        });

        return view;
    }

    @Override
    public void onResume() {
        GroupViewModel groupViewModel = ViewModelProviders.of(this).get(GroupViewModel.class);
        if(getActivity() == null) {
            return;
        }
        MemberViewModel memberViewModel = ViewModelProviders.of(this,new MemberViewModelFactory(getActivity().getApplication(),gName)).get(MemberViewModel.class);
        members =  memberViewModel.getAllMembersNonLiveData(gName);
        // get latest currency picked by the user
        currency = groupViewModel.getGroupCurrencyNonLive(gName);
        runCalculations(); // run the algorithm (will use latest bills from LiveData)
        super.onResume();
    }

    private void resultEmptyCheck() {
        // if results[] is empty display"No one is owed money"
        if(results.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            header.setVisibility(View.GONE);
        } else  {
            recyclerView.setVisibility(View.VISIBLE);
            header.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            adapter.storeToList(results); // update the recycler view with the new results
        }
    }

    private void runCalculations() {
        if(!members.isEmpty()) {
            calculateTransactions();
            resultEmptyCheck();
        } else {
            results.clear();
            resultEmptyCheck();
        }
    }
}