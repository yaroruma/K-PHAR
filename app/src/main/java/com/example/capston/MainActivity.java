package com.example.capston;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity
{
    Button mButtonsettings;
    Button mButtonDistanceSettings;
    Button mButtonETC;
    Button mButtonComplete;
    private static final int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 1;
    static final int REQUEST_ENABLE_BT = 10;
    int mPariedDeviceCount = 0;
    Set<BluetoothDevice> mDevices;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mRemoteDevie; // 블루투스 소켓
    BluetoothSocket mSocket = null;
    InputStream mInputStream = null;

    public native long loadCascade(String cascadeFileName );

    static public long cascadeClassifier_face = 0;
    static public long cascadeClassifier_side_face = 0;
    static public long cascadeClassifier_eye = 0;

    static public boolean mode = false;

    static {
        System.loadLibrary("opencv_java4");
        System.loadLibrary("native-lib");
    }

    Thread mWorkerThread = null;

    private void copyFile(String filename) {
        String baseDir = Environment.getExternalStorageDirectory().getPath();
        String pathDir = baseDir + File.separator + filename;

        AssetManager assetManager = this.getAssets();

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            Log.d("Main", "copyFile :: 다음 경로로 파일복사 "+ pathDir);
            inputStream = assetManager.open(filename);
            outputStream = new FileOutputStream(pathDir);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            inputStream.close();
            inputStream = null;
            outputStream.flush();
            outputStream.close();
            outputStream = null;
        } catch (Exception e) {
            Log.d("Main", "copyFile :: 파일 복사 중 예외 발생 "+e.toString() );
        }

    }

    private void read_cascade_file(){
        copyFile("haarcascade_frontalface_alt.xml");
        copyFile("haarcascade_profileface.xml");
        copyFile("haarcascade_eye_tree_eyeglasses.xml");

        Log.d("Main", "read_cascade_file:");

        cascadeClassifier_face = loadCascade( "haarcascade_frontalface_alt.xml");
        cascadeClassifier_side_face = loadCascade( "haarcascade_profileface.xml");
        cascadeClassifier_eye = loadCascade( "haarcascade_eye_tree_eyeglasses.xml");
    }
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals("STOPPED")) {
                stopService(new Intent((Context) MainActivity.this, CamService.class));
                //finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mButtonsettings = (Button) findViewById(R.id.settings);
        mButtonsettings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                Intent intent = new Intent(getApplicationContext(), Settings.class);
                startActivity(intent);

            }
        });
        mButtonDistanceSettings = (Button) findViewById(R.id.dist);
        mButtonDistanceSettings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                Intent intent = new Intent(getApplicationContext(), DistanceSettings.class);
                startActivity(intent);

            }
        });
        mButtonETC = (Button) findViewById(R.id.etc);
        mButtonETC.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bluetooth.sendData("Tracking 1 0");
                stopService(new Intent((Context)MainActivity.this, CamService.class));
            }

        });
        mButtonComplete = (Button) findViewById(R.id.complete);
        mButtonComplete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPermission();

            }

        });

        read_cascade_file();

        Log.d("Main", "read_cascade_file:");
        checkBluetooth();
    }
    public void getPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {   // 마시멜로우 이상일 경우
            if (!android.provider.Settings.canDrawOverlays(this)) {              // 체크
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE);
            } else {
                Intent intent = new Intent(getApplicationContext(), CamService.class);
                intent.setAction("ACTION_START");
                startService(intent);

                /*Intent intent_home = new Intent(Intent.ACTION_MAIN);
                intent_home.addCategory(Intent.CATEGORY_HOME);
                intent_home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent_home);*/
                finish();
            }
        } else {
            Intent intent = new Intent(getApplicationContext(), CamService.class);
            intent.setAction("ACTION_START");
            startService(intent);

            /*Intent intent_home = new Intent(Intent.ACTION_MAIN);
            intent_home.addCategory(Intent.CATEGORY_HOME);
            intent_home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent_home);*/
            finish();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE:
                if (!android.provider.Settings.canDrawOverlays(this)) {
                    // TODO 동의를 얻지 못했을 경우의 처리

                } else {
                    Intent intent = new Intent(getApplicationContext(), CamService.class);
                    intent.setAction("ACTION_START");
                    startService(intent);

                    /*Intent intent_home = new Intent(Intent.ACTION_MAIN);
                    intent_home.addCategory(Intent.CATEGORY_HOME);
                    intent_home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent_home);*/
                    finish();
                }
            case REQUEST_ENABLE_BT: // 블루투스가 사용가능 할 때
                if (resultCode == RESULT_OK) {
                    selectDevice();
                    break;
                }
                super.onActivityResult(requestCode, resultCode, data);
        }

    }
    void selectDevice() { // 블루투스 페어링 메소드
        mDevices = mBluetoothAdapter.getBondedDevices();
        mPariedDeviceCount = mDevices.size();

        if (mPariedDeviceCount == 0) { // 페어링된 장치가 없는 경우.
            Toast.makeText(getApplicationContext(), "페어링된 장치가 없습니다.", Toast.LENGTH_LONG).show();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("블루투스 장치 선택");
        List<String> listItems = new ArrayList<String>();
        for (BluetoothDevice device : mDevices) {
            listItems.add(device.getName());
        }
        listItems.add("취소");  // 취소 항목 추가.
        final CharSequence[] items = listItems.toArray(new CharSequence[listItems.size()]);
        listItems.toArray(new CharSequence[listItems.size()]);

        builder.setItems(items, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int item) { // 연결할 장치를 선택한 경우, 선택한 장치와 연결
                if (item == mPariedDeviceCount) {
                    Toast.makeText(getApplicationContext(), "연결할 장치를 선택하지 않았습니다.", Toast.LENGTH_LONG).show();
                } else {
                    connectToSelectedDevice(items[item].toString());
                }
            }

        });

        builder.setCancelable(false);
        AlertDialog alert = builder.create();
        alert.show();
    }
    BluetoothDevice getDeviceFromBondedList(String name) {     // 블루투스 장치의 이름을 페어링 된 장치 목록에서 검색
        Toast.makeText(getApplicationContext(), "장치를 선택해 주세요.", Toast.LENGTH_LONG).show();
        BluetoothDevice selectedDevice = null;
        for (BluetoothDevice deivce : mDevices) {
            if (name.equals(deivce.getName())) {
                selectedDevice = deivce;
                break;
            }
        }
        return selectedDevice;
    }

    void connectToSelectedDevice(String selectedDeviceName) {
        mRemoteDevie = getDeviceFromBondedList(selectedDeviceName);
        UUID uuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //uuid: 범용 고유 식별자, IP와 비슷한 개념

        try {

            // 데이터 수신 준비.
            Intent intent = new Intent(getApplicationContext(), Bluetooth.class);
            intent.putExtra(Intent.EXTRA_TEXT, mRemoteDevie.getAddress());
            startService(intent);

        } catch (Exception e) { // 블루투스 연결 중 오류 발생 시 오류 메시지 출력
            Toast.makeText(getApplicationContext(), "블루투스 연결 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
        }
    }
    void checkBluetooth() { //기기가 블루투스를 지원하는지 확인하는 메소드
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "기기가 블루투스를 지원하지 않습니다.", Toast.LENGTH_LONG).show();
        } else {
            if (!mBluetoothAdapter.isEnabled()) { // 블루투스 지원하며 비활성 상태인 경우.
                Toast.makeText(getApplicationContext(), "현재 블루투스가 비활성 상태입니다.", Toast.LENGTH_LONG).show();
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else // 블루투스 지원하며 활성 상태인 경우.
                selectDevice();
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        this.registerReceiver((BroadcastReceiver)this.receiver, new IntentFilter("STOPPED"));
    }

    @Override
    protected void onDestroy() {//앱이 종료될 때 데이터 수신 쓰레드 종료
        try {
            mWorkerThread.interrupt();
            mInputStream.close();
            mSocket.close();
            this.unregisterReceiver((BroadcastReceiver)this.receiver);
        } catch (Exception e) {
        }
        super.onDestroy();
    }
}