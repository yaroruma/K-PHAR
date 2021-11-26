package com.example.capston;

import android.annotation.SuppressLint;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;


public class DistanceSettings extends AppCompatActivity {

    Button mMotor1;
    Button mMotor2;
    Button mMotor3;
    Button mMotor4;
    ImageButton mCW;
    ImageButton mCCW;
    Button mTest;
    TextView motor_select;
    TextView motor_angle;
    int m1 = 60, m2 = 60, m3 = 60, m4 = 60;
    int m1_min=0, m2_min=0, m3_min=0, m4_min = 0;
    int m1_max=99, m2_max=99, m3_max=99, m4_max=99;
    int m1_re = 0, m2_re = 0, m3_re = 0, m4_re = 0;
    int select = 0;

    String msg = "";
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.distancesettings);
        motor_select = (TextView) findViewById(R.id.motor_select);
        motor_angle = (TextView) findViewById(R.id.motor_angle);
        mMotor1 = (Button) findViewById(R.id.motor1);
        mMotor1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                select = 1;
                motor_select.setText("motor1 :");
                motor_angle.setText(Integer.toString(m1));
            }
        });

        mMotor2 = (Button) findViewById(R.id.motor2);
        mMotor2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                select = 2;
                motor_select.setText("motor2 :");
                motor_angle.setText(Integer.toString(m2));
            }
        });

        mMotor3 = (Button) findViewById(R.id.motor3);
        mMotor3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                select = 3;
                motor_select.setText("motor3 :");
                motor_angle.setText(Integer.toString(m3));
            }
        });

        mMotor4 = (Button) findViewById(R.id.motor4);
        mMotor4.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                select = 4;
                motor_select.setText("motor4 :");
                motor_angle.setText(Integer.toString(m4));
            }
        });

        mCW = (ImageButton) findViewById(R.id.cw);
        mCW.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(Bluetooth.max){
                    Toast.makeText(getApplicationContext(), "최대 각도입니다.", Toast.LENGTH_LONG).show();
                    Bluetooth.max = false;
                }
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        motor_angle.setText("CW");
                        msg = "Distance "+select+" c";
                        Bluetooth.sendData(msg);
                        Log.d("-----", msg);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        msg = "Distance "+select+" s";
                        Bluetooth.sendData(msg);
                        motor_angle.setText("STOP");
                        Log.d("-----", msg);
                        return true;
                }
                return false;
            }
        });


        mCCW = (ImageButton) findViewById(R.id.ccw);
        mCCW.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(Bluetooth.max){
                    Toast.makeText(getApplicationContext(), "최대 각도입니다.", Toast.LENGTH_LONG).show();
                    Bluetooth.max = false;
                }
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        msg = "Distance "+select+" C";
                        Bluetooth.sendData(msg);
                        motor_angle.setText("CCW");
                        Log.d("-----", msg);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        msg = "Distance "+select+" s";
                        Bluetooth.sendData(msg);
                        motor_angle.setText("STOP");
                        Log.d("-----", msg);
                        return true;
                }
                return false;
            }
        });
        mTest = (Button) findViewById(R.id.test);
        mTest.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                msg = "Test 0 0";
                Bluetooth.sendData(msg);
            }
        });
        Bluetooth.sendData("start");
    }


    @Override
    protected void onDestroy() {//앱이 종료될 때 데이터 수신 쓰레드 종료
        super.onDestroy();
        msg = "Distance 0 0";
        Bluetooth.sendData(msg);
    }
}
