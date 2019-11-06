package com.gaew.moneytracker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.lang.reflect.Type;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BudgetFragment extends Fragment implements ItemsAdapterListener, ActionMode.Callback {
    public static final int REQUEST_CODE = 100;
    public static final String COLOR = "color";
    public static final String TYPE = "fragmentType";

    private ItemsAdapter mAdapter;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ActionMode mActionMode;


    private Api mApi;


    public static BudgetFragment newInstancee(int colorId, String type) {

        BudgetFragment budgetFragment = new BudgetFragment();
        Bundle bundle = new Bundle(); //структура для хранения ключей
        bundle.putInt(COLOR, colorId);
        bundle.putString(TYPE, type);
        budgetFragment.setArguments(bundle);
        return budgetFragment;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mApi = ((LoftApp) getActivity().getApplication()).getApi();
        loadItems();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_budget, null); //имя нового лайаута


        RecyclerView recyclerView = view.findViewById(R.id.budget_item_list);
        mSwipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadItems();

            }
        });


        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));

        mAdapter = new ItemsAdapter(getArguments().getInt(COLOR));
        mAdapter.setmListener(this);
        recyclerView.setAdapter(mAdapter);

        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            int price;
            try {
                price = Integer.parseInt(data.getStringExtra("price"));

            } catch (NumberFormatException e) {
                price = 0;
            }
            final int realPrice = price;

            final String name = data.getStringExtra("name");


            final String token = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(MainActivity.TOKEN, "");

            Call<Status> call = mApi.addItem(new AddItemRequest(name, getArguments().getString(TYPE), price), token);
            call.enqueue(new Callback<Status>() {
                @Override
                public void onResponse(Call<Status> call, Response<Status> response) {

                    if (response.body().getStatus().equals("success")) {

                        loadItems();
                    }

                }

                @Override
                public void onFailure(Call<Status> call, Throwable t) {
                    t.printStackTrace();

                }
            });

        }
    }

    protected void loadItems() {
        final String token = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(MainActivity.TOKEN, "");
        Call<List<Item>> items = mApi.getItems(getArguments().getString(TYPE), token);  //когда пойдут айтемы с сервера мы должны в методе ОНреспонсе заполнить
        // наш адаптер новыми данными
        items.enqueue(new Callback<List<Item>>() {

            @Override
            public void onResponse(Call<List<Item>> call, Response<List<Item>> response) {
                mAdapter.clearItems();
                mSwipeRefreshLayout.setRefreshing(false);
                List<Item> items = response.body();
                for (Item item : items) {
                    mAdapter.addItem(item);


                }
                ((MainActivity) getActivity()).loadBalance();

            }

            @Override
            public void onFailure(Call<List<Item>> call, Throwable t) {
                mSwipeRefreshLayout.setRefreshing(false);

            }
        });


    }


    @Override
    public void onItemClick(Item item, int position) {
        mAdapter.clearItem(position);
        if (mActionMode != null) {
            mActionMode.setTitle(getString(R.string.selected, String.valueOf(mAdapter.getSelectedSize())));

        }

    }


    @Override
    public void onItemLongClick(Item item, int position) {
        if (mActionMode == null) {
            getActivity().startActionMode(this);
        }
        mAdapter.toggleItem(position);
        if (mActionMode != null) {
            mActionMode.setTitle(getString(R.string.selected, String.valueOf(mAdapter.getSelectedSize())));

        }


    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        mActionMode = actionMode;
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        MenuInflater menuInflater = new MenuInflater(getActivity());
        menuInflater.inflate(R.menu.menu_delete, menu);
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.remove) {

            new AlertDialog.Builder(getContext()).setMessage(R.string.confirmation)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            removeItems();


                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    }).show();


        }

        return true;
    }

    private void removeItems() {
        String token = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(MainActivity.TOKEN, "");
        List<Integer> selectedItems = mAdapter.getSelectedItemIds();
        for (Integer itemId : selectedItems) {

            Call<Status> call = mApi.removeItem(String.valueOf(itemId.intValue()), token);
            call.enqueue(new Callback<Status>() {
                @Override
                public void onResponse(Call<Status> call, Response<Status> response) {

                    loadItems();
                    mAdapter.clesrSelections();

                }

                @Override
                public void onFailure(Call<Status> call, Throwable t) {

                }
            });
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        mActionMode = null;
        mAdapter.clesrSelections();

    }
}
