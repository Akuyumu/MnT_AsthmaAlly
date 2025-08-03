#libraries
import numpy as np
import pandas as pd
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
import json

# load synthetic data
df = pd.read_csv("C:/Users/User/synthetic_asthma_data.csv")

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

# Select and store what the app needs to run the prediction
model_package = {
    "coefficients": model.coef_[0].tolist(),
    "intercept": model.intercept_[0],
    "mean": scaler.mean_.tolist(),
    "std": scaler.scale_.tolist(),
    "feature_names": features.tolist(),
    "threshold": float(best_threshold)
}

# save to a JSON file
with open("model_weights.json", "w") as f:
    json.dump(model_package, f, indent=2)

print("✅ Model exported to model_weights.json")
