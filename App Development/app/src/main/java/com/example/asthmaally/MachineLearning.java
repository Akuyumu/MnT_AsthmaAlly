package com.example.asthmaally;



import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.icu.util.Calendar;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

public class MachineLearning {
    private final List<Float> resp_ratelist = new ArrayList<>();
    private final List<Long> resp_rateTimestamps = new ArrayList<>();
    private float resp_rate;
    private final List<Float> num_coughslist = new ArrayList<>();
    private final List<Long> num_coughsTimestamps = new ArrayList<>();
    private float num_coughs;
    private final List<Float> cough_intensitylist = new ArrayList<>();
    private final List<Long> cough_intensityTimestamps = new ArrayList<>();
    private float cough_intensity;
    private final List<Float> wheeze_countlist = new ArrayList<>();
    private final List<Long> wheeze_countTimestamps = new ArrayList<>();
    private float wheeze_count;
    private final List<Float> wheeze_amplitudelist = new ArrayList<>();
    private final List<Long> wheeze_amplitudeTimestamps = new ArrayList<>();
    private float wheeze_amplitude;
    private final List<Float> Sp02_minlist = new ArrayList<>();
    private final List<Long> Sp02_minTimestamps = new ArrayList<>();
    private float SpO2_min;
    private final List<Float> HRlist = new ArrayList<>();
    private final List<Long> HRTimestamps = new ArrayList<>();
    private float HR;
    private float SABA_today;
    private float SABA_last_3_days;
    private final List<Float> resistancelist = new ArrayList<>();
    private final List<Long> resistanceTimestamps = new ArrayList<>();
    private float peak_to_peak_resistance;
    private long startOfDay = getStartOfToday();
    private static final double[] COEFFICIENTS = {0.1811115360748873,
            0.026968439269346195,
            0.13293573363416797,
            -0.17067145372521966,
            0.25315700809617425,
            -0.5160718894689602,
            0.15445567800370652,
            -0.00028452898966881005,
            -0.584700663825511,
            -0.5515061604026594,
            };
    private static final double INTERCEPT = -5.4290128950479355;
    private static final double[] MEANS = {18.33895362180609,
            2.783467384092461,
            1.0,
            0.3037555544291334,
            0.9,
            0.2725901631778806,
            96.0334169983289,
            92.33770646536499,
            0.2,
            0.6,};
    private static final double[] STDS = {2.1164652683130027,
            0.20049138268817826,
            1.0645812948447542,
            0.10516477225004424,
            0.8698658900466592,
            0.047508960252750504,
            0.45968133656057913,
            4.016144465464146,
            0.4,
            0.8,
            };
    private static final double THRESHOLD = 0.11000000000000001;






    // Setters
    public void addresp_rate_indiv(float resp_rate_indiv) {
        long now = System.currentTimeMillis();
        if (now - startOfDay >= 24 * 60 * 60 * 1000) {
            resp_rate=computeDailyAverage(resp_ratelist); // before resetting
            resetData(resp_ratelist);
            startOfDay = getStartOfToday(); // reset for new day
        }
        resp_ratelist.add(resp_rate_indiv);
        resp_rateTimestamps.add(now);
    }

    public float getresp_rate() {
        return resp_rate;
    }

    public void addresistance_indiv(float resistance_indiv) {
        long now = System.currentTimeMillis();
        if (now - startOfDay >= 24 * 60 * 60 * 1000) {
            peak_to_peak_resistance=computeDailyAverage(resistancelist); // before resetting
            resetData(resistancelist);
            startOfDay = getStartOfToday(); // reset for new day
        }
        resistancelist.add(resistance_indiv);
        resistanceTimestamps.add(now);
    }

    public float getpeak_to_peak_resistance() {
        return peak_to_peak_resistance;
    }
    public void addnum_coughs_indiv(float num_coughs_indiv) {
        long now = System.currentTimeMillis();
        if (now - startOfDay >= 24 * 60 * 60 * 1000) {
            num_coughs=computeSum(num_coughslist); // before resetting
            resetData(num_coughslist);
            startOfDay = getStartOfToday(); // reset for new day
        }
        num_coughslist.add(num_coughs_indiv);
        num_coughsTimestamps.add(now);
    }

    public float getnum_coughs() {
        return num_coughs;
    }
    public void addcough_intensity_indiv(float cough_intensity_indiv) {
        long now = System.currentTimeMillis();
        if (now - startOfDay >= 24 * 60 * 60 * 1000) {
            cough_intensity=computeDailyAverage(cough_intensitylist); // before resetting
            resetData(cough_intensitylist);
            startOfDay = getStartOfToday(); // reset for new day
        }
        cough_intensitylist.add(cough_intensity_indiv);
        cough_intensityTimestamps.add(now);
    }

    public float getcough_intensity() {
        return cough_intensity;
    }
    public void addwheeze_count_indiv(float wheeze_count_indiv) {
        long now = System.currentTimeMillis();
        if (now - startOfDay >= 24 * 60 * 60 * 1000) {
            wheeze_count=computeSum(wheeze_countlist); // before resetting
            resetData(wheeze_countlist);
            startOfDay = getStartOfToday(); // reset for new day
        }
        wheeze_countlist.add(wheeze_count_indiv);
        wheeze_countTimestamps.add(now);
    }

    public float getwheeze_count() {
        return wheeze_count;
    }
    public void addwheeze_amplitude_indiv(float wheeze_amplitude_indiv) {
        long now = System.currentTimeMillis();
        if (now - startOfDay >= 24 * 60 * 60 * 1000) {
            wheeze_amplitude=computeDailyAverage(wheeze_amplitudelist); // before resetting
            resetData(wheeze_amplitudelist);
            startOfDay = getStartOfToday(); // reset for new day
        }
        wheeze_amplitudelist.add(wheeze_amplitude_indiv);
        wheeze_amplitudeTimestamps.add(now);
    }

    public float getwheeze_amplitude() {
        return wheeze_amplitude;
    }
    public void addSpO2_min_indiv(float SpO2_min_indiv) {
        long now = System.currentTimeMillis();
        if (now - startOfDay >= 24 * 60 * 60 * 1000) {
            SpO2_min=computeDailyAverage(Sp02_minlist); // before resetting
            resetData(Sp02_minlist);
            startOfDay = getStartOfToday(); // reset for new day
        }
        Sp02_minlist.add(SpO2_min_indiv);
        Sp02_minTimestamps.add(now);
    }

    public float getSpO2_min() {
        return SpO2_min;
    }

    public void addHR_indiv(float HR_indiv) {
        long now = System.currentTimeMillis();
        if (now - startOfDay >= 24 * 60 * 60 * 1000) {
            HR=computeDailyAverage(HRlist); // before resetting
            resetData(HRlist);
            startOfDay = getStartOfToday(); // reset for new day
        }
        HRlist.add(HR_indiv);
        HRTimestamps.add(now);
    }

    public float getHR() {
        return HR;
    }
    public void setSABA_today(float SABA_today) {
        this.SABA_today = SABA_today;
    }
    public void setSABA_last_3_days(float SABA_last_3_days) {
        this.SABA_last_3_days = SABA_last_3_days;
    }

    private long getStartOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public float computeDailyAverage(List<Float> values) {
        if (values == null || values.isEmpty()) return 0;

        float sum = 0;
        for (float v : values) {
            sum += v;
        }

        return sum / values.size();
    }

    public void resetData(List<?> list) {
        if (list != null) {
            list.clear();
        }
    }

    public float computeSum(List<Float> values) {
        if (values == null || values.isEmpty()) return 0;

        float sum = 0;
        for (float v : values) {
            sum += v;
        }

        return sum ;
    }

    public void predictAndNotify(List<Float> features, Context context) {
        if (features.size() != COEFFICIENTS.length) {
            Log.e("MachineLearning", "Invalid number of features: " + features.size());
            return;
        }

        double score = INTERCEPT;
        for (int i = 0; i < COEFFICIENTS.length; i++) {
            double standardized = (features.get(i) - MEANS[i]) / STDS[i];
            score += COEFFICIENTS[i] * standardized;
        }

        double probability = 1.0 / (1.0 + Math.exp(-score));
        Log.d("MachineLearning", "Predicted probability: " + probability);

        if (probability > THRESHOLD) {
            sendRiskNotification(context, probability);
        }
    }

    public void sendRiskNotification(Context context, double probability) {
        String CHANNEL_ID = "asthma_alerts";

        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Create channel if needed (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Asthma Risk Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for asthma exacerbation risk");
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // make sure this exists
                .setContentTitle("⚠️ Asthma Risk Detected")
                .setContentText("Risk level: " + Math.round(probability * 100) +
                        "%. You may be at risk due to  in the next 3 days.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify(1001, builder.build());
    }

}



