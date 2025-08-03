package com.example.asthmaally;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class FragmentAdapter extends FragmentStateAdapter {

    public FragmentAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new BluetoothFragment();
            case 1:
                return new QuestionsFragment();
            case 2:
                return new CounterFragment();
            default:
                return new BluetoothFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3; // total number of tabs
    }
}

