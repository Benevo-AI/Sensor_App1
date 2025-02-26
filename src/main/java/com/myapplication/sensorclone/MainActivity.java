package com.myapplication.sensorclone;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope, magnetometer;
    private TextView txtX, sensorData1, directionTextView, degreeTextView;

    private ArrayList<float[]> accelerationData = new ArrayList<>();
    private ArrayList<float[]> gyroscopeData = new ArrayList<>();
    private ArrayList<Float> azimuthData = new ArrayList<>();

    private Handler handler = new Handler();
    private boolean isRecording = false;

    private Button ExpBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtX = findViewById(R.id.textxyz);
        sensorData1 = findViewById(R.id.textzyx);
        directionTextView = findViewById(R.id.directionText);
        degreeTextView = findViewById(R.id.degreeText);

        ExpBtn = findViewById(R.id.btnExport);
        ExpBtn.setOnClickListener(v -> exportcsv() );



        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (magnetometer != null) {
                sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }



        // Start collecting data every second
        startDataCollection();
    }

    private void exportcsv(){
        File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"sensor_data25.csv");

        if(!file.exists()){
            Toast.makeText(this, "CSV file does not found!!", Toast.LENGTH_SHORT).show();
        }


        Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_STREAM,fileUri);
        intent.putExtra(Intent.EXTRA_SUBJECT,"Sensor Data CSV File..");
        intent.putExtra(Intent.EXTRA_TEXT, "Attached is a sensor data file.");

        startActivity(Intent.createChooser(intent,"Share CSV via"));

    }
    private void startDataCollection() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    calculateAndSaveData();
                }
                isRecording = true;
                accelerationData.clear();
                gyroscopeData.clear();
                azimuthData.clear();
                handler.postDelayed(this, 1000); // Repeat every 1 second
            }
        }, 1000);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isRecording) return;

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerationData.add(event.values.clone());

            // 🔹 Update UI live
            runOnUiThread(() -> txtX.setText(String.format("X: %.2f\nY: %.2f\nZ: %.2f",
                    event.values[0], event.values[1], event.values[2])));
        }
        else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroscopeData.add(event.values.clone());

            // 🔹 Update UI live
            runOnUiThread(() -> sensorData1.setText(String.format("αX: %.2f rad/s²\nαY: %.2f rad/s²\nαZ: %.2f rad/s²",
                    event.values[0], event.values[1], event.values[2])));
        }
        else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            float azimuth = calculateAzimuth(event.values);
            azimuthData.add(azimuth);

            // 🔹 Update UI live
            String direction = getDirectionFromAzimuth(azimuth);
            runOnUiThread(() -> {
                degreeTextView.setText(String.format("Azimuth \n %.1f°", azimuth));
                directionTextView.setText("Direction  \n" + direction);
            });
        }
    }

    // Convert azimuth into a direction name
    private String getDirectionFromAzimuth(float azimuth) {
        if (azimuth >= 337.5 || azimuth < 22.5) return "North";
        if (azimuth >= 22.5 && azimuth < 67.5) return "North-East";
        if (azimuth >= 67.5 && azimuth < 112.5) return "East";
        if (azimuth >= 112.5 && azimuth < 157.5) return "South-East";
        if (azimuth >= 157.5 && azimuth < 202.5) return "South";
        if (azimuth >= 202.5 && azimuth < 247.5) return "South-West";
        if (azimuth >= 247.5 && azimuth < 292.5) return "West";
        if (azimuth >= 292.5 && azimuth < 337.5) return "North-West";
        return "Unknown";
    }



    private float calculateAzimuth(float[] magnetometerValues) {
        float azimuth = (float) Math.toDegrees(Math.atan2(magnetometerValues[1], magnetometerValues[0]));
        if (azimuth < 0) azimuth += 360;
        return azimuth;
    }

    private void calculateAndSaveData() {

        String timestamp = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                .format(new Date());

        float[] avgAcceleration = calculateAverage(accelerationData);
        float[] avgGyroscope = calculateAverage(gyroscopeData);
        float avgAzimuth = calculateAverageAzimuth(azimuthData);

        String data = String.format("%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f\n",
                timestamp,
                avgAcceleration[0], avgAcceleration[1], avgAcceleration[2],
                avgGyroscope[0], avgGyroscope[1], avgGyroscope[2],
                avgAzimuth);

        saveToCSV(data);
    }

    private float[] calculateAverage(ArrayList<float[]> data) {
        if (data.isEmpty()) return new float[]{0, 0, 0};

        float sumX = 0, sumY = 0, sumZ = 0;
        for (float[] values : data) {
            sumX += values[0];
            sumY += values[1];
            sumZ += values[2];
        }

        int size = data.size();
        return new float[]{sumX / size, sumY / size, sumZ / size};
    }

    private float calculateAverageAzimuth(ArrayList<Float> data) {
        if (data.isEmpty()) return 0;
        float sum = 0;
        for (float value : data) {
            sum += value;
        }
        return sum / data.size();
    }

    private void saveToCSV(String data) {
        String fileName = "sensor_data25.csv";
        File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName);
        boolean fileExists=file.exists();

        try (FileWriter writer = new FileWriter(file, true)) {
            if (!fileExists) {
                writer.append("Timestamp,AvgAcc_X,AvgAcc_Y,AvgAcc_Z,AvgAngAcc_X,AvgAngAcc_Y,AvgAngAcc_Z,AvgAzimuth\n");
            }


            writer.append(data);
            Toast.makeText(this, "Data saved to CSV", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error saving data", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        handler.removeCallbacksAndMessages(null);
    }
}
