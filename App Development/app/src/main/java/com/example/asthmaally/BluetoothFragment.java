package com.example.asthmaally;


import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import android.content.pm.PackageManager;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Handler;
import android.os.Looper;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattDescriptor;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.util.Log;


public class BluetoothFragment extends Fragment {

    private int breathCount = 0; // Tracks the number of breaths (used for respiration rate)
    private List<Float> breatheDataList = new ArrayList<>(); // Used for the graph
    private Handler handler = new Handler();  // For managing timer to track respiration rate
    private Runnable countRunnable;  // To run the breath count timer
    private long startTime;


    // Nordic UART Service UUIDs
    private static final UUID UART_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID TX_CHARACTERISTIC_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");


    private BluetoothGatt bluetoothGatt1;
    private BluetoothGatt bluetoothGatt2;
    private TextView txtSpO2;
    private TextView txtRespiration;

    private LineChart chartBreathe;
    private LineDataSet dataSet;
    private LineData lineData;
    private int breatheIndex = 0;

    private MachineLearning machineLearning;
    List<Float> firstValues = new ArrayList<>();
    List<Float> secondValues = new ArrayList<>();

    List<Long> WheezeTimestamps = new ArrayList<>();




    TextView txtData;
    Button btnConnect;

    private final List<BluetoothDevice> bleDevices = new ArrayList<>();
    private final List<String> bleDeviceNames = new ArrayList<>();
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {
                    Log.d("BluetoothScan", "Discovered device: " + device.getAddress() + " | Name: " + device.getName());
                } else {
                    Log.d("BluetoothScan", "Discovered device: Permission denied");
                }

                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {

                    String deviceName = device.getName();
                    if (deviceName != null) {
                        deviceName = deviceName.trim();
                    }

                    if (deviceName != null && !deviceName.isEmpty() && !bleDevices.contains(device)) {
                        bleDevices.add(device);
                        bleDeviceNames.add(deviceName);
                    }
                }
            }
        }
    };

    private void showDevicePicker() {
        String[] devicesArray = bleDeviceNames.toArray(new String[0]);
        boolean[] checkedItems = new boolean[bleDeviceNames.size()];

        new AlertDialog.Builder(requireContext())
                .setTitle("Select 2 Bluetooth Devices")
                .setMultiChoiceItems(devicesArray, checkedItems, (dialog, which, isChecked) -> {
                    // This block is optional; handled later on confirm
                })
                .setPositiveButton("Connect", (dialog, which) -> {
                    List<BluetoothDevice> selectedDevices = new ArrayList<>();
                    for (int i = 0; i < checkedItems.length; i++) {
                        if (checkedItems[i]) selectedDevices.add(bleDevices.get(i));
                    }

                    if (selectedDevices.size() == 2) {
                        connectToDevices(selectedDevices.get(0), selectedDevices.get(1));
                    } else {
                        txtData.append("\nPlease select exactly 2 devices.");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    // Declare this at the class level if not already done
    private final List<Float> voltageBuffer = new ArrayList<>();
    private final List<Float> Wheeze = new ArrayList<>();
    private final List<Float> Sp02Buffer = new ArrayList<>();

    private void connectToDevices(BluetoothDevice d1, BluetoothDevice d2) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {

            bluetoothGatt1 = d1.connectGatt(requireContext(), false, gattCallback);
            bluetoothGatt2 = d2.connectGatt(requireContext(), false, gattCallback);

            txtData.append("\nConnected to: " + d1.getName() + " and " + d2.getName());
        } else {
            txtData.append("\nPermission denied to connect to devices.");
        }
    }
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices();
                } else {
                    requireActivity().runOnUiThread(() -> txtData.append("\nPermission denied to discover services."));
                }


            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                requireActivity().runOnUiThread(() -> txtData.append("\nDisconnected from device."));
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService uartService = gatt.getService(UART_SERVICE_UUID);
            if (uartService != null) {
                BluetoothGattCharacteristic txChar = uartService.getCharacteristic(TX_CHARACTERISTIC_UUID);
                if (txChar != null) {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gatt.setCharacteristicNotification(txChar, true);
                    }
                    // Set up descriptor for notifications
                    BluetoothGattDescriptor descriptor = txChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }

                } else {
                    requireActivity().runOnUiThread(() -> txtData.append("\nTX characteristic not found."));
                }
            } else {
                requireActivity().runOnUiThread(() -> txtData.append("\nUART Service not found."));
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (TX_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                String received = new String(data, StandardCharsets.UTF_8);
                String[] parts = received.split(",");
                if (gatt == bluetoothGatt1) {
                    startBreathCounting();
                    try {

                        if (parts.length >= 4) {
                                float externalsoundvalue = Float.parseFloat(parts[0].trim());
                                float accelerationvalue = Float.parseFloat(parts[1].trim());
                                firstValues.add(externalsoundvalue);
                                secondValues.add(accelerationvalue);
                                if (firstValues.size() >= 3) {
                                    int i = firstValues.size() - 2;  // index of the *current* value
                                    boolean isPeak1 = isPeakAt(firstValues, i);
                                    boolean isPeak2 = isPeakAt(secondValues, i);
                                    if (isPeak1 && isPeak2) {
                                        machineLearning.addnum_coughs_indiv(1);
                                        machineLearning.addcough_intensity_indiv(firstValues.get(i));
                                    }
                                float lungValue = Float.parseFloat(parts[2].trim());
                                requireActivity().runOnUiThread(() -> addBreatheEntry(lungValue));
                                Wheeze.add(lungValue);
                                if (Wheeze.size() >= 3) {
                                    int g = Wheeze.size() - 2;  // index of the *current* value
                                    boolean isWheezepeak = isPeakAt(Wheeze,g);
                                    if(isWheezepeak){
                                        long now = System.currentTimeMillis();
                                        WheezeTimestamps.add(now);
                                        WheezeTimestamps.removeIf(ts -> ts < now - 30_000);}
                                    if(WheezeTimestamps.size()>7){
                                        machineLearning.addwheeze_count_indiv(1);
                                        machineLearning.addwheeze_amplitude_indiv(lungValue);


                                    }
                                }



                                float voltageValue = Float.parseFloat(parts[3].trim());
                                voltageBuffer.add(voltageValue);

                                // Maintain only last 3 values
                                if (voltageBuffer.size() == 3) {
                                    float prev = voltageBuffer.get(0);
                                    float curr = voltageBuffer.get(1);
                                    float next = voltageBuffer.get(2);

                                    if (curr > prev && curr > next) {
                                        machineLearning.addresistance_indiv(curr);
                                        breathCount++;
                                    }

                                    // Remove oldest to slide the window
                                    voltageBuffer.remove(0);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e("BluetoothFragment", "Invalid data: " + received, e);
                    }
                }else if (gatt == bluetoothGatt2) {
                    //
                    try {
                        if (parts.length >= 2) {
                            float HR = Float.parseFloat(parts[0].trim());
                            machineLearning.addHR_indiv(HR);
                            float spO2Value = Float.parseFloat(parts[1].trim());
                            requireActivity().runOnUiThread(() -> txtSpO2.setText("SpO2: " + spO2Value));
                            Sp02Buffer.add(spO2Value);
                            // Maintain only last 3 values
                            if (Sp02Buffer.size() == 3) {
                                float prev = Sp02Buffer.get(0);
                                float curr = Sp02Buffer.get(1);
                                float next = Sp02Buffer.get(2);

                                if (curr < prev && curr < next) {
                                    machineLearning.addSpO2_min_indiv(curr);
                                }

                                // Remove oldest to slide the window
                                Sp02Buffer.remove(0);
                            }
                        }
                    } catch (Exception e) {
                        Log.e("BluetoothFragment", "Invalid SpO2 data from GATT1: " + received, e);
                    }
                }



            }
        }

    };

    private boolean isPeakAt(List<Float> values, int index) {
        if (index <= 0 || index >= values.size() - 1) return false;

        Float prev = values.get(index - 1);
        Float curr = values.get(index);
        Float next = values.get(index + 1);

        return curr > prev && curr > next;
    }

    private void startBreathCounting() {
        // Create a new Runnable that will run every second for 1 minute
        countRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsedTime = System.currentTimeMillis() - startTime;  // Calculate the time elapsed since start

                // Update every second if less than 1 minute has passed
                if (elapsedTime < 60000) {  // Check if less than 1 minute (60000 ms) has passed
                    handler.postDelayed(this, 1000);  // Re-run this every second (1000 ms)
                } else {
                    // After 1 minute, update the respiration rate and reset the count
                    updateRespirationRate();  // Update UI with the respiration rate
                    breathCount = 0;  // Reset the breath count for the next round
                    startTime = System.currentTimeMillis();  // Reset the start time for the next 1-minute cycle
                }
            }
        };

        // Start the timer immediately
        handler.post(countRunnable);
    }

    private void addBreatheEntry(float value) {
        breatheDataList.add(value); // Store data for graphing

        // Limit the number of graph entries to 20 (for smooth display)
        if (dataSet.getEntryCount() >= 20) {
            dataSet.removeFirst(); // Remove the oldest entry
            for (Entry e : dataSet.getValues()) {
                e.setX(e.getX() - 1); // Shift the x-axis
            }
            breatheIndex = 19; // Adjust the index after removing old data
        }

        // Add the new value to the graph (plotting data)
        dataSet.addEntry(new Entry(breatheIndex++, value));
        dataSet.notifyDataSetChanged();
        lineData.notifyDataChanged();
        chartBreathe.notifyDataSetChanged();
        chartBreathe.invalidate(); // Refresh the graph
    }

    private void updateRespirationRate() {
        requireActivity().runOnUiThread(() -> {
            String rate = "Respiration Rate: " + breathCount + " breaths/min";
            machineLearning.addresp_rate_indiv(breathCount);
            txtRespiration.setText(rate);  // Assuming `txtRespiration` is the TextView for respiration rate
        });
    }


    public BluetoothFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bluetooth, container, false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        machineLearning = new MachineLearning();
        txtSpO2 = view.findViewById(R.id.txtSpO2);
        txtRespiration = view.findViewById(R.id.txtRespiration);


        // Register BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        requireActivity().registerReceiver(receiver, filter);

        // Request permissions
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    requireActivity(),
                    new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, 1);
        }
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            txtData.setText("Bluetooth not supported on this device.");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        txtData = view.findViewById(R.id.txtData);
        btnConnect = view.findViewById(R.id.btnConnect);
        chartBreathe = view.findViewById(R.id.chartBreathe);
        dataSet = new LineDataSet(new ArrayList<>(), "Breathing");
        dataSet.setColor(Color.BLUE);
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setMode(LineDataSet.Mode.LINEAR);

        lineData = new LineData(dataSet);
        chartBreathe.setData(lineData);

        chartBreathe.getDescription().setEnabled(false);
        chartBreathe.getLegend().setEnabled(false);
        // Customize X-axis
        XAxis xAxis = chartBreathe.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextSize(12f);
        xAxis.setLabelRotationAngle(0f); // optional

// Customize Y-axis
        YAxis yAxisLeft = chartBreathe.getAxisLeft();
        yAxisLeft.setGranularity(1f);
        yAxisLeft.setTextSize(12f);

// Optionally hide right Y-axis
        chartBreathe.getAxisRight().setEnabled(false);


        btnConnect.setOnClickListener(v -> {

            bleDevices.clear();
            bleDeviceNames.clear();

            BluetoothLeScanner bleScanner = bluetoothAdapter.getBluetoothLeScanner();
            bleScanner.startScan(bleScanCallback); // <- BLE scanning instead of startDiscovery()

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                bleScanner.stopScan(bleScanCallback); // stop scanning
                bleDevices.removeIf(d -> {
                    String name = d.getName();
                    return name == null || name.trim().isEmpty();
                });
                bleDeviceNames.removeIf(name -> name == null || name.trim().isEmpty());
                showDevicePicker();                   // show selection dialog
            }, 2000);
        });
    }


    private final ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String deviceName = "Unknown Device";
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                String name = device.getName();
                if (name != null) {
                    deviceName = name;
                }
            }
            if (deviceName.equalsIgnoreCase("Unknown Device") || deviceName.trim().isEmpty()) {
                return; // Skip unnamed/unknown devices
            }
            if (!bleDevices.contains(device)) {
                bleDevices.add(device);
                bleDeviceNames.add(deviceName);

                // Sort both lists alphabetically by name
                List<Pair<String, BluetoothDevice>> combined = new ArrayList<>();
                for (int i = 0; i < bleDevices.size(); i++) {
                    combined.add(new Pair<>(bleDeviceNames.get(i), bleDevices.get(i)));
                }

                // Sort the pairs by name
                Collections.sort(combined, Comparator.comparing(pair -> pair.first));

                // Clear original lists and re-populate with sorted data
                bleDeviceNames.clear();
                bleDevices.clear();
                for (Pair<String, BluetoothDevice> pair : combined) {
                    bleDeviceNames.add(pair.first);
                    bleDevices.add(pair.second);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            txtData.append("\nBLE scan failed: " + errorCode);
        }
    };


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {

            if (bluetoothGatt1 != null) {
                bluetoothGatt1.close();
                bluetoothGatt1 = null;
            }
            if (bluetoothGatt2 != null) {
                bluetoothGatt2.close();
                bluetoothGatt2 = null;
            }

        } else {
            txtData.append("\nPermission denied to close GATT connections.");
        }

        requireActivity().unregisterReceiver(receiver);
    }


}

