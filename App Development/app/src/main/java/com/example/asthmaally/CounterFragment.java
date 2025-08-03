package com.example.asthmaally;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.HashSet;
import java.util.Set;

public class CounterFragment extends Fragment {

    private static final String PREFS_NAME = "AttackPrefs";
    private static final String KEY_COUNT = "attack_count";
    private static final String KEY_TIMESTAMP = "week_start_time";
    private MachineLearning machineLearning;

    private TextView txtAttackCount;
    private SharedPreferences prefs;

    Button btnInhaler;
    TextView txtInhalerCount;

    private static final String KEY_INHALER_COUNT = "key_inhaler_count";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_counter, container, false);

        Button btnAttack = view.findViewById(R.id.btnAttack);
        txtAttackCount = view.findViewById(R.id.txtAttackCount);

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        machineLearning = new MachineLearning();
        resetIfNewWeek();
        updateDisplay();

        btnAttack.setOnClickListener(v -> {
            int count = prefs.getInt(KEY_COUNT, 0);
            prefs.edit().putInt(KEY_COUNT, count + 1).apply();
            updateDisplay();
        });

        btnInhaler = view.findViewById(R.id.btnInhaler);
        txtInhalerCount = view.findViewById(R.id.txtInhalerCount);

        btnInhaler.setOnClickListener(v -> {
            int count = prefs.getInt(KEY_INHALER_COUNT, 0);
            prefs.edit().putInt(KEY_INHALER_COUNT, count + 1).apply();
            long now = System.currentTimeMillis();
            Set<String> timestamps = prefs.getStringSet("INHALER_LOG", new HashSet<>());
            timestamps = new HashSet<>(timestamps); // must create new set for editing
            timestamps.add(Long.toString(now));
            prefs.edit().putStringSet("INHALER_LOG", timestamps).apply();
            updateDisplay();
        });
        int todayCount = getInhalerPressesWithinDays(1);
        int last3DaysCount = getInhalerPressesWithinDays(3);
        machineLearning.setSABA_today(todayCount);
        machineLearning.setSABA_last_3_days(last3DaysCount);




        return view;
    }

    private void updateDisplay() {
        int count = prefs.getInt(KEY_COUNT, 0);
        if (txtAttackCount != null) {
            txtAttackCount.setText("Number of asthma attacks this week: " + count);}
        int inhalerCount = prefs.getInt(KEY_INHALER_COUNT, 0);
        if (txtInhalerCount != null) {
        txtInhalerCount.setText("Number of inhaler uses this week: " + inhalerCount);}
    }

    private void resetIfNewWeek() {
        long savedTime = prefs.getLong(KEY_TIMESTAMP, 0);
        long now = System.currentTimeMillis();
        long sevenDaysMillis = 7L * 24 * 60 * 60 * 1000;

        if (now - savedTime > sevenDaysMillis) {
            prefs.edit()
                    .putInt(KEY_COUNT, 0)
                    .putLong(KEY_TIMESTAMP, now)
                    .apply();
        }
    }
    private int getInhalerPressesWithinDays(int days) {
        Set<String> timestamps = prefs.getStringSet("INHALER_LOG", new HashSet<>());
        long now = System.currentTimeMillis();
        long cutoff = now - days * 24L * 60 * 60 * 1000;

        int count = 0;
        for (String tsStr : timestamps) {
            try {
                long ts = Long.parseLong(tsStr);
                if (ts >= cutoff) {
                    count++;
                }
            } catch (NumberFormatException ignored) {}
        }
        return count;
    }


}
