package io.celox.querycore.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import io.celox.querycore.R;
import io.celox.querycore.adapters.SimpleStringAdapter;
import io.celox.querycore.data.ConnectionEntity;
import io.celox.querycore.models.ConnectionInfo;
import io.celox.querycore.viewmodel.ConnectionViewModel;
import io.celox.querycore.viewmodel.DatabaseViewModel;

public class DatabaseBrowserFragment extends Fragment implements SimpleStringAdapter.OnItemClickListener {
    
    private ConnectionViewModel connectionViewModel;
    private DatabaseViewModel databaseViewModel;
    
    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private Button buttonQuery;
    private SearchView searchView;
    
    private SimpleStringAdapter adapter;
    private String currentDatabase = "";
    private int connectionId = -1;
    private ConnectionEntity connection;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_database_browser, container, false);
        
        // Initialize views
        tabLayout = view.findViewById(R.id.tab_layout);
        recyclerView = view.findViewById(R.id.recycler_view);
        progressBar = view.findViewById(R.id.progress_bar);
        emptyView = view.findViewById(R.id.text_view_empty);
        buttonQuery = view.findViewById(R.id.button_query);
        searchView = view.findViewById(R.id.search_view);
        
        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SimpleStringAdapter(this);
        recyclerView.setAdapter(adapter);
        
        // Set up tab listener
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                updateTabContent(tab.getPosition());
            }
            
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Not needed
            }
            
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Not needed
            }
        });
        
        // Set up search
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterItems(query);
                return true;
            }
            
            @Override
            public boolean onQueryTextChange(String newText) {
                filterItems(newText);
                return true;
            }
        });
        
        // Set up query button
        buttonQuery.setOnClickListener(v -> {
            if (!currentDatabase.isEmpty()) {
                Bundle args = new Bundle();
                args.putString("databaseName", currentDatabase);
                Navigation.findNavController(requireView()).navigate(
                        R.id.action_databaseBrowserFragment_to_queryFragment, args
                );
            } else {
                Toast.makeText(requireContext(), "Please select a database first", Toast.LENGTH_SHORT).show();
            }
        });
        
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize ViewModels
        connectionViewModel = new ViewModelProvider(requireActivity()).get(ConnectionViewModel.class);
        databaseViewModel = new ViewModelProvider(requireActivity()).get(DatabaseViewModel.class);
        
        // Get connection ID from arguments
        if (getArguments() != null) {
            connectionId = getArguments().getInt("connectionId", -1);
        }
        
        // Load connection details
        if (connectionId != -1) {
            connectionViewModel.getConnectionById(connectionId).observe(getViewLifecycleOwner(), connection -> {
                if (connection != null) {
                    this.connection = connection;
                    requireActivity().setTitle(connection.getName());
                    
                    // Connect to the database if not already connected
                    if (databaseViewModel.getIsConnected().getValue() != Boolean.TRUE) {
                        ConnectionInfo connectionInfo = connection.toConnectionInfo();
                        databaseViewModel.connect(connectionInfo);
                    }
                }
            });
        }
        
        // Observe connection state
        databaseViewModel.getIsConnected().observe(getViewLifecycleOwner(), isConnected -> {
            if (isConnected) {
                // Load databases
                showLoading();
                databaseViewModel.loadDatabases();
            } else {
                showEmpty("Not connected to database");
            }
        });
        
        // Observe databases
        databaseViewModel.getDatabases().observe(getViewLifecycleOwner(), databases -> {
            if (databases != null && !databases.isEmpty()) {
                if (tabLayout.getSelectedTabPosition() == 0) {
                    adapter.setItems(databases);
                    hideLoading();
                }
            } else {
                showEmpty("No databases available");
            }
        });
        
        // Observe tables
        databaseViewModel.getTables().observe(getViewLifecycleOwner(), tables -> {
            if (tables != null && !tables.isEmpty()) {
                if (tabLayout.getSelectedTabPosition() == 1) {
                    adapter.setItems(tables);
                    hideLoading();
                }
            } else {
                showEmpty("No tables available");
            }
        });
        
        // Observe errors
        databaseViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                hideLoading();
            }
        });
    }
    
    private void updateTabContent(int position) {
        if (position == 0) {
            // Databases tab
            if (databaseViewModel.getIsConnected().getValue() == Boolean.TRUE) {
                showLoading();
                databaseViewModel.loadDatabases();
            }
        } else if (position == 1) {
            // Tables tab
            if (databaseViewModel.getIsConnected().getValue() == Boolean.TRUE && !currentDatabase.isEmpty()) {
                showLoading();
                databaseViewModel.loadTables(currentDatabase);
            } else {
                showEmpty("Please select a database first");
            }
        }
    }
    
    private void filterItems(String query) {
        adapter.filter(query);
    }
    
    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
    }
    
    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }
    
    private void showEmpty(String message) {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setText(message);
    }
    
    @Override
    public void onItemClick(String item) {
        int tabPosition = tabLayout.getSelectedTabPosition();
        
        if (tabPosition == 0) {
            // Database clicked
            currentDatabase = item;
            showLoading();
            databaseViewModel.loadTables(currentDatabase);
            
            // Switch to tables tab
            tabLayout.selectTab(tabLayout.getTabAt(1));
        } else if (tabPosition == 1) {
            // Table clicked
            Bundle args = new Bundle();
            args.putString("databaseName", currentDatabase);
            args.putString("tableName", item);
            Navigation.findNavController(requireView()).navigate(
                    R.id.action_databaseBrowserFragment_to_tableViewFragment, args
            );
        }
    }
}