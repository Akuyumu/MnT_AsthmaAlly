#libraries
import numpy as np
import pandas as pd

# 6-MONTH ASTHMA PATIENT DATA SIMULATION ----------------------------------------------------

np.random.seed(42)
days = 180  # simulate randomised data for 6 months, 1 row = 1 day
data = []
saba_history = [] # pattern of frequent inhaler use over the past few days leading to the attack

for day in range(1, days + 1):
    # Default label
    exacerbation = 0

    # simulate baseline values (slightly abnormal due to moderate asthma)

    # simulate daily inhalation volume (L): slightly reduced for asthma patients
    inhalation_volume = np.random.normal(0.9, 0.05)  # healthy ≈ 1.0–1.2

    # simulate respiratory rate (breaths/min): normal is 12–18, slightly elevated for moderate asthma
    resp_rate = np.random.normal(18, 2)

    # simulated voltage fluctuation from chest band during breathing (V): reflects depth of breath
    peak_to_peak_voltage = np.random.normal(2.8, 0.2)

    # number of coughs per day: rare during normal periods
    num_coughs = np.random.poisson(1)

    # cough intensity: derived from accelerometer and sound signal amplitude (0–1 scale)
    cough_intensity = np.random.normal(0.3, 0.1)

    # wheeze count: # of wheeze events per day from lung sound recordings
    wheeze_count = np.random.poisson(1)

    # wheeze amplitude: loudness/severity of wheeze from stethoscope signal
    wheeze_amplitude = np.random.normal(0.25, 0.05)

    # minimum SpO₂ recorded today (%): normal is 97–99%, asthmatic patients may drop below 95%
    SpO2_min = np.random.normal(96, 0.5)

    # drop in SpO₂ from baseline (percentage points): higher drop indicates desaturation risk
    SpO2_drop = np.random.normal(1, 0.2)

    # maximum heart rate reached today (bpm): typically elevated during stress or attack
    HR_max = np.random.normal(92, 5)

    # variability in HR over time (bpm): influenced by exertion, breath difficulty, autonomic responses
    HR_variability = np.random.normal(10, 2)


    # simulate daily SABA use
    SABA_today = np.random.choice([0, 1], p=[0.85, 0.15])

    # mark exacerbation and override values if within attack period
    if 60 <= day <= 62 or 120 <= day <= 123 or 165 <= day <= 167:
        # when the patient is experiencing or approaching an exacerbation, physiological changes occur.
        # these values override the normal baseline for days 60–62, 120–123, and 165–167.

        # inhalation volume drops due to airway constriction and reduced lung expansion
        inhalation_volume = np.random.normal(0.7, 0.03)  # significantly lower than normal

        # respiratory rate spikes as the patient struggles to breathe
        resp_rate = np.random.normal(30, 3)  # tachypnea (~2x normal)

        # voltage amplitude increases due to shallow, rapid, and forceful breathing
        peak_to_peak_voltage = np.random.normal(3.2, 0.3)

        # frequent coughing due to airway irritation and mucus buildup
        num_coughs = np.random.poisson(6)

        # coughs become harsher, indicating more severe airway distress
        cough_intensity = np.random.normal(0.7, 0.1)

        # increased wheezing as airflow becomes more obstructed
        wheeze_count = np.random.poisson(6)

        # louder and more prolonged wheezing sounds during breathing
        wheeze_amplitude = np.random.normal(0.45, 0.05)

        # SpO₂ levels drop significantly due to poor gas exchange
        SpO2_min = np.random.normal(91, 1)  # below 92% is clinically concerning

        # large drop from the patient's usual oxygen levels
        SpO2_drop = np.random.normal(4, 0.5)

        # heart rate peaks due to stress, hypoxia, and increased respiratory effort
        HR_max = np.random.normal(110, 5)

        # greater fluctuations in heart rate as the body compensates for distress
        HR_variability = np.random.normal(20, 3)

        # emergency use of short-acting bronchodilator (e.g., salbutamol)
        SABA_today = 1 # used at least once today

        exacerbation = 1

    # append today's SABA to history (after override if needed)
    saba_history.append(SABA_today)

    # calculate accurate SABA usage over last 3 days (excluding today)
    # SABA_last_3_days calculated based on the last 3 SABA_today values
    if day >= 4:
        SABA_last_3_days = sum(saba_history[-4:-1])
    elif day == 3:
        SABA_last_3_days = sum(saba_history[-3:-1])
    elif day == 2:
        SABA_last_3_days = sum(saba_history[-2:-1])
    else:
        SABA_last_3_days = 0

    # append all values in order
    data.append([
        inhalation_volume, resp_rate, peak_to_peak_voltage, num_coughs, cough_intensity,
        wheeze_count, wheeze_amplitude, SpO2_min, SpO2_drop, HR_max,
        HR_variability, SABA_today, SABA_last_3_days, exacerbation
    ])

# define column names
columns = [
    'inhalation_volume', 'resp_rate', 'peak_to_peak_voltage',
    'num_coughs', 'cough_intensity', 'wheeze_count', 'wheeze_amplitude',
    'SpO2_min', 'SpO2_drop', 'HR_max', 'HR_variability',
    'SABA_today', 'SABA_last_3_days', 'exacerbation'
]

# create DataFrame and print
df = pd.DataFrame(data, columns=columns)
print(df.head(10))
print(df.tail(10))
print(df.describe())

# (uncomment to) export data
# df.to_csv("synthetic_asthma_data.csv", index=False)
# print("CSV file saved.")

# ASTHMA EXACERBATION PREDICTION MODEL USING LOGISTIC REGRESSION ----------------------------

# libraries
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import (
    classification_report,
    roc_auc_score,
    confusion_matrix,
    recall_score,
    precision_score,
    fbeta_score
)

# load synthetic data
df = pd.read_csv("C:\\Users\\User\\synthetic_asthma_data.csv")

# create early warning label (exacerbation in next 3 days)
# rolling max of 'exacerbation' over 4 days (today + next 3), shifted to today
df['Exacerbation_within_3_days'] = df['exacerbation'].rolling(window=4, min_periods=1).max().shift(-3).fillna(0).astype(int)

# lagged features (previous day's value)
for col in df.columns[:-2]:  # exclude 'exacerbation' and 'Exacerbation_within_3_days'
    df[f'{col}_lag1'] = df[col].shift(1)

# rolling 3-day averages and differences (rate of change)
for col in ['SpO2_min', 'HR_max', 'resp_rate']:
    df[f'{col}_mean3'] = df[col].rolling(window=3).mean()
    df[f'{col}_diff3'] = df[col] - df[col].rolling(window=3).mean()

# drop initial rows with NaN from lag/rolling calculations
df.dropna(inplace=True)

# normalize features using Days 1–30 (personal calibration)
calibration = df.iloc[:30]  # first 30 days as baseline
features = df.columns.drop(['exacerbation', 'Exacerbation_within_3_days']) 
scaler = StandardScaler()
df_scaled = df.copy()
df_scaled[features] = scaler.fit(calibration[features]).transform(df[features])

# prepare training & test data
X = df_scaled[features]  # feature matrix
y = df_scaled['Exacerbation_within_3_days']  # prediction target

# split: first 120 days for training, last 60 for testing
X_train, X_test = X[:120], X[120:]
y_train, y_test = y[:120], y[120:]

# logistic regression model
model = LogisticRegression(max_iter=1000)
model.fit(X_train, y_train)

# make predictions on test set
y_pred = model.predict(X_test)
y_proba = model.predict_proba(X_test)[:, 1]  # probability of class 1 (risk score)

# evaluate model performance
print("Confusion Matrix:")
print(confusion_matrix(y_test, y_pred))

print("\nClassification Report:")
print(classification_report(y_test, y_pred))

print("ROC AUC Score:", roc_auc_score(y_test, y_proba))

# threshold tuning prioritising recall, automate to maximise f2 score
print("\nThreshold tuning (maximize F2 score):")
thresholds = np.arange(0.05, 0.5, 0.01)
best_threshold = 0
best_f2 = 0

for t in thresholds:
    preds = (y_proba > t).astype(int)
    recall = recall_score(y_test, preds)
    precision = precision_score(y_test, preds)
    f2 = fbeta_score(y_test, preds, beta=2)
    print(f"Threshold: {t:.2f} | Recall: {recall:.2f} | Precision: {precision:.2f} | F2 Score: {f2:.2f}")
    if f2 > best_f2:
        best_f2 = f2
        best_threshold = t

print(f"\nSelected best threshold based on F2 Score: {best_threshold:.2f}")

# generate alerts
df_test = df.iloc[-len(y_test):].copy()
df_test['risk_score'] = y_proba

df_test['flagged_alert'] = (df_test['risk_score'] > best_threshold).astype(int)
df_test['alert_message'] = np.where(
    df_test['flagged_alert'] == 1,
    "You may be approaching an asthma exacerbation within 3 days based on your lung function, SpO₂ and increased inhaler use.",
    ""
)

# view or export alerts
alerts = df_test[df_test['flagged_alert'] == 1][['risk_score', 'alert_message']]
print("\nPredicted Alerts:")
print(alerts.head())
# (uncomment to)save to file
# alerts.to_csv("asthma_alerts.csv", index=False)

# summary of feature importance of indicators in model
coeffs = pd.Series(model.coef_[0], index=features)
print("\nTop Predictive Features:")
print(coeffs.sort_values(ascending=False)) 

