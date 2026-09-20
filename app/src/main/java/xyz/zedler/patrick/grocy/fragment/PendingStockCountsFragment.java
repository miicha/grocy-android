/*
 * This file is part of Grocy Android.
 *
 * Grocy Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grocy Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grocy Android. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2020-2024 by Patrick Zedler and Dominic Zedler
 * Copyright (c) 2024-2026 by Patrick Zedler
 */

package xyz.zedler.patrick.grocy.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import xyz.zedler.patrick.grocy.Constants.FAB;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.activity.MainActivity;
import xyz.zedler.patrick.grocy.adapter.PendingStockCountAdapter;
import xyz.zedler.patrick.grocy.adapter.PendingStockCountAdapter.PendingStockCountAdapterListener;
import xyz.zedler.patrick.grocy.databinding.FragmentPendingStockCountsBinding;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.PendingStockCount;
import xyz.zedler.patrick.grocy.model.SnackbarMessage;
import xyz.zedler.patrick.grocy.viewmodel.PendingStockCountsViewModel;

/**
 * FORK (offline inventory): review and transfer the bookings that were entered while offline.
 */
public class PendingStockCountsFragment extends BaseFragment
    implements PendingStockCountAdapterListener {

  private final static String TAG = PendingStockCountsFragment.class.getSimpleName();

  private MainActivity activity;
  private FragmentPendingStockCountsBinding binding;
  private PendingStockCountsViewModel viewModel;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      ViewGroup container,
      Bundle savedInstanceState
  ) {
    binding = FragmentPendingStockCountsBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();

    if (binding != null) {
      binding.recycler.animate().cancel();
      binding = null;
    }
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    activity = (MainActivity) requireActivity();
    viewModel = new ViewModelProvider(this).get(PendingStockCountsViewModel.class);

    binding.setFragment(this);
    binding.setActivity(activity);
    binding.setViewModel(viewModel);
    binding.setLifecycleOwner(getViewLifecycleOwner());

    binding.recycler.setLayoutManager(
        new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
    );
    binding.recycler.setItemAnimator(new DefaultItemAnimator());

    viewModel.getItemsLive().observe(getViewLifecycleOwner(), items -> {
      if (items == null) {
        return;
      }
      binding.textEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
      if (binding.recycler.getAdapter() instanceof PendingStockCountAdapter) {
        ((PendingStockCountAdapter) binding.recycler.getAdapter()).updateData(items);
      } else {
        binding.recycler.setAdapter(
            new PendingStockCountAdapter(requireContext(), items, this)
        );
        binding.recycler.scheduleLayoutAnimation();
      }
    });

    viewModel.getEventHandler().observeEvent(getViewLifecycleOwner(), event -> {
      if (event.getType() == Event.SNACKBAR_MESSAGE) {
        activity.showSnackbar(
            ((SnackbarMessage) event).getSnackbar(activity.binding.coordinatorMain)
        );
      }
    });

    viewModel.loadFromDatabase();

    activity.getScrollBehavior().setNestedOverScrollFixEnabled(true);
    activity.getScrollBehavior().setUpScroll(binding.appBar, false, binding.scroll);
    activity.getScrollBehavior().setBottomBarVisibility(true);
    // showFab must be true here — updateFab below only swaps icon and listener, it does not
    // show a hidden FAB, and the FAB is the only way to start the transfer
    activity.updateBottomAppBar(true, R.menu.menu_empty, null);
    // FORK (offline inventory): the icon has to say whether transferring is possible at all,
    // otherwise the only feedback comes after pressing it
    viewModel.getOfflineLive().observe(getViewLifecycleOwner(), offlineMode -> {
      boolean blocked = offlineMode != null && offlineMode;
      activity.updateFab(
          blocked ? R.drawable.ic_round_cloud_pending : R.drawable.ic_round_cloud_sync,
          blocked
              ? R.string.msg_pending_stock_counts_offline_hint
              : R.string.action_transfer_pending_counts,
          blocked ? FAB.TAG.SYNC_BLOCKED : FAB.TAG.SYNC,
          true,
          () -> viewModel.transferAll()
      );
    });
  }

  public void showDiscardAllDialog() {
    new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Grocy_AlertDialog)
        .setTitle(R.string.action_discard_all)
        .setMessage(R.string.msg_pending_stock_counts_discard_all)
        .setPositiveButton(R.string.action_delete, (dialog, which) -> viewModel.deleteAll())
        .setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.cancel())
        .create().show();
  }

  @Override
  public void onDeleteClicked(PendingStockCount pendingStockCount) {
    viewModel.delete(pendingStockCount);
  }

  @NonNull
  @Override
  public String toString() {
    return TAG;
  }
}
