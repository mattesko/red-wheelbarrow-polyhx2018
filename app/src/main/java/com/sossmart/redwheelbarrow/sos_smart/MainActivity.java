package com.sossmart.redwheelbarrow.sos_smart;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity  implements SensorEventListener{

    private float lastX, lastY, lastZ;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private float attractionForce = (float) 9.82; // Earth's Attraction Force
    private int thresholdG = 8;

    private float deltaXMax = 0;
    private float deltaYMax = 0;
    private float deltaZMax = 0;

    private float deltaX = 0;
    private float deltaY = 0;
    private float deltaZ = 0;

    private float deltaAccelMag = 0;
    private float deltaImpactG  = 0;
    private float previousDeltaAccelMag = 0;
    private float decelerationVelocity = 0;
    boolean deceleration = false;

    // GUI Variables
    private TextView currentX, currentY, currentZ, maxX, maxY, maxZ;
    private TextView accelMag, impactG;

    Button btn;
    private Button smsButton;

    private NotificationDecision notificationDecision;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // success! we have an accelerometer

            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            //vibrateThreshold = accelerometer.getMaximumRange() / 2;
        } else {
            // fail! we dont have an accelerometer!
        }

        // Ask for permission to send and receive sms
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.SEND_SMS}, 1);

        // This is for the button to change to the SMS activity
        smsButton = (Button) findViewById(R.id.smsButton);

        // Capture button clicks
        smsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent smsIntent = new Intent(MainActivity.this, SMS.class);
                startActivity(smsIntent);
            }
        });


        notificationDecision = new NotificationDecision();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(MainActivity.this, "Permission denied to send SMS messages", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }

    }

    public void initializeViews() { //GUI Init
        accelMag = (TextView) findViewById(R.id.accel_mag_text);
        impactG  = (TextView) findViewById(R.id.impact_g_text);

        currentX = (TextView) findViewById(R.id.currentX);
        currentY = (TextView) findViewById(R.id.currentY);
        currentZ = (TextView) findViewById(R.id.currentZ);

        maxX = (TextView) findViewById(R.id.maxX);
        maxY = (TextView) findViewById(R.id.maxY);
        maxZ = (TextView) findViewById(R.id.maxZ);

        btn = (Button) findViewById(R.id.btn);
    }

    //onResume() register the accelerometer for listening the events
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    //onPause() unregister the accelerometer for stop listening the events
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // clean current values
        displayCleanValues();
        // display the current x,y,z accelerometer values
        displayCurrentValues();
        // display the max x,y,z accelerometer values
        displayMaxValues();

        // get the change of the x,y,z values of the accelerometer
        deltaX = Math.abs(lastX - sensorEvent.values[0]);
        deltaY = Math.abs(lastY - sensorEvent.values[1]);
        deltaZ = Math.abs(lastZ - sensorEvent.values[2]);

        // alpha is calculated as t / (t + dT)
        // with t, the low-pass filter's time-constant
        // and dT, the event delivery rate

        final float alpha = (float) 0.8;
        float[] gravity = new float[3];

//        gravity[0] = alpha * gravity[0] + (1 - alpha) * sensorEvent.values[0];
//        gravity[1] = alpha * gravity[1] + (1 - alpha) * sensorEvent.values[1];
//        gravity[2] = alpha * gravity[2] + (1 - alpha) * sensorEvent.values[2];
//
//        deltaX = sensorEvent.values[0] - gravity[0];
//        deltaY = sensorEvent.values[1] - gravity[1];
//        deltaZ = sensorEvent.values[2] - gravity[2];
//
//        deltaX = (lastX - sensorEvent.values[0]);
//        deltaY = (lastY - sensorEvent.values[1]);
//        deltaZ = (lastZ - sensorEvent.values[2]);

        deltaAccelMag = (float) Math.sqrt( deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        deltaImpactG = deltaAccelMag / attractionForce;

        // if the change is below 2, it is just plain noise
//        if (deltaX < 2)
//            deltaX = 0;
//        if (deltaY < 2)
//            deltaY = 0;
//        if (deltaZ  vibrateThreshold) || (deltaY > vibrateThreshold) || (deltaZ > vibrateThreshold)) {
//            v.vibrate(50);
//        }

        decelerationVelocity = previousDeltaAccelMag - deltaAccelMag;
        if (Math.abs(decelerationVelocity) < 1 && decelerationVelocity < 0){
            deceleration = true;
        }

        // In an event of a crash:
        if(deltaImpactG > thresholdG && deceleration == true){ // If G is above threshold and in deceleration

            btn.setBackgroundColor(Color.GREEN); // Troubleshooting purposes

            // Start timer
            timerNotification(); // Send SMS if time runs out without any user interactions

            // Resets deceleration
            deceleration = false;
        }


        previousDeltaAccelMag = deltaAccelMag;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void timerNotification() {

        new CountDownTimer(30000, 1000) {  // Counting down to 30 000ms with intervals of 1 000ms

            public void onTick(long millisUntilFinished) {
                // Ask user if he/she is okay
                notificationDecision.showActionButtonsNotification();
                showToast("Count down..." + millisUntilFinished);
                // If answer, cancel timer
                //cancel();
            }

            public void onFinish() {
                // Send SMS if no answers
                showToast("BOOOOM!");
            }
        }.start();

    }



    // Clean display values
    public void displayCleanValues() {
        accelMag.setText("0.0");
        impactG.setText("0.0");

        currentX.setText("0.0");
        currentY.setText("0.0");
        currentZ.setText("0.0");
    }

    // display the current x,y,z accelerometer values
    public void displayCurrentValues() {
        accelMag.setText(Float.toString(deltaAccelMag));
        impactG.setText(Float.toString(deltaImpactG));

        currentX.setText(Float.toString(deltaX));
        currentY.setText(Float.toString(deltaY));
        currentZ.setText(Float.toString(deltaZ));
    }

    // display the max x,y,z accelerometer values
    public void displayMaxValues() {
        if (deltaX > deltaXMax) {
            deltaXMax = deltaX;
            maxX.setText(Float.toString(deltaXMax));
        }
        if (deltaY > deltaYMax) {
            deltaYMax = deltaY;
            maxY.setText(Float.toString(deltaYMax));
        }
        if (deltaZ > deltaZMax) {
            deltaZMax = deltaZ;
            maxZ.setText(Float.toString(deltaZMax));
        }
    }

    // Utility toast function cuz why not
    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
