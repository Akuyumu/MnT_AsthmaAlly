package com.example.asthmaally;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import java.util.List;
import java.util.ArrayList;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.content.Context;
import android.graphics.Color;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;



public class QuestionsFragment extends Fragment {
    private final Questions[] questions = {
            new Questions("On average, during the past week, how often were you woken by your asthma during the night?",
                    new String[]{"Never", "Hardly ever", "A few times", "Several times","Many times","A great many times","Unable to sleep because of asthma"}),

            new Questions("On average, during the past week, how bad were your asthma symptoms when you woke up in the morning?",
                    new String[]{" No symptoms", "Very mild symptoms", "Mild symptoms", "Moderate symptoms","Quite severe symptoms"," Severe symptoms","Very severe symptoms"}),

            new Questions("In general, during the past week, how limited were you in your activities because of your asthma?",
                    new String[]{"Not limited at all", "Very slightly limited ", "Slightly limited", "Moderately limited","Very limited","Extremely limited","Totally limited"}),

            new Questions("In general, during the past week, how much shortness of breath did you experience because of your asthma?",
                    new String[]{"None", "A very little", "A little", "A moderate amount","Quite a lot","A great deal","A very great deal"}),

            new Questions("In general, during the past week, how much of the time did you wheeze?",
                    new String[]{"Not at all", "Hardly any of the time", "A little of the time", "A moderate amount of the time","A lot of the time","Most of the time","All the time"}),

            new Questions("On average, during the past week, how many puffs of short-acting bronchodilator have you used each day?",
                    new String[]{"0", "1–2 puffs most days", "3–4 puffs most days", "5–8 puffs most days","9–12 puffs most days","13–16 puffs most days","More than 16 puffs most days"})
    };

    private SharedPreferences prefs;

    String[] selectedAnswers = new String[questions.length];
    List<TextView> previousAnswerViews = new ArrayList<>();


    public QuestionsFragment() {
        // Required empty constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_questions, container, false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireContext().getSharedPreferences("WeeklyAnswers", Context.MODE_PRIVATE);

        LinearLayout container = view.findViewById(R.id.questionsContainer);

        for (int i = 0; i < questions.length; i++) {
            Questions q = questions[i];

            // Question Text
            TextView questionText = new TextView(getContext());
            questionText.setText((i + 1) + ". " + q.text);
            questionText.setTextSize(16);
            questionText.setPadding(0, 16, 0, 4);
            container.addView(questionText);

            // Previous answer display
            String prevAnswer = prefs.getString("q" + i, "No answer");
            TextView previous = new TextView(getContext());
            previous.setText("Previous answer: " + prevAnswer);
            previous.setTextSize(14);
            previous.setTextColor(Color.GRAY);
            container.addView(previous);
            previousAnswerViews.add(previous);

            // Options (RadioButtons)
            RadioGroup group = new RadioGroup(getContext());
            group.setOrientation(RadioGroup.VERTICAL);
            for (String option : q.options) {
                RadioButton rb = new RadioButton(getContext());
                rb.setText(option);
                group.addView(rb);
            }
            container.addView(group);

            // Save selected answer to preferences
            final int questionIndex = i;
            group.setOnCheckedChangeListener((radioGroup, checkedId) -> {
                RadioButton selected = group.findViewById(checkedId);
                if (selected != null) {
                    selectedAnswers[questionIndex] = selected.getText().toString();
                }
            });
        }
        TextView timerText = view.findViewById(R.id.timerText);
        Button btnSubmit = view.findViewById(R.id.btnSubmit);

// Get saved submission timestamp
        long lastSubmitTime = prefs.getLong("lastSubmitTime", 0);
        long now = System.currentTimeMillis();
        long twoWeek = 14 * 24 * 60 * 60 * 1000L;
        long timeLeft = (lastSubmitTime + twoWeek) - now;

        if (timeLeft > 0) {
            long daysLeft = timeLeft / (1000 * 60 * 60 * 24);
            timerText.setText("You can submit again in " + daysLeft + " day(s).");
            btnSubmit.setEnabled(false);
        } else {
            timerText.setText("You're eligible to submit this week's answers.");
            btnSubmit.setEnabled(true);
        }

        btnSubmit.setOnClickListener(v -> {
            boolean allAnswered = true;
            for (String ans : selectedAnswers) {
                if (ans == null) {
                    allAnswered = false;
                    break;
                }
            }
            if (!allAnswered) {
                Toast.makeText(getContext(), "Please answer all questions", Toast.LENGTH_SHORT).show();
                return;
            }
            SharedPreferences.Editor editor = prefs.edit();

            for (int i = 0; i < selectedAnswers.length; i++) {
                if (selectedAnswers[i] != null) {
                    editor.putString("q" + i, selectedAnswers[i]); // ✅ store only on submit
                }
            }
            // ✅ Apply changes to save all answers
            editor.apply();
            for (int i = 0; i < selectedAnswers.length; i++) {
                if (selectedAnswers[i] != null) {
                    previousAnswerViews.get(i).setText("Previous answer: " + selectedAnswers[i]);
                }
            }
            // Save timestamp
            prefs.edit().putLong("lastSubmitTime", System.currentTimeMillis()).apply();
            Toast.makeText(getContext(), "Submitted!", Toast.LENGTH_SHORT).show();
            btnSubmit.setEnabled(false);
            timerText.setText("You can submit again in 14 day(s).");
        });

    }

}
