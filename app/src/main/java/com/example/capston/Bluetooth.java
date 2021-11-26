package com.example.capston;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import android.view.View;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;

public class Bluetooth extends Service {
    static final int REQUEST_ENABLE_BT = 10;
    int mPariedDeviceCount = 0;
    Set<BluetoothDevice> mDevices;
    BluetoothAdapter mBluetoothAdapter;
    String sRemoteDevice;
    BluetoothDevice mRemoteDevie; // 블루투스 소켓
    BluetoothSocket mSocket = null;
    static OutputStream mOutputStream = null;
    InputStream mInputStream = null;
    static String mStrDelimiter = "\n";
    char mCharDelimiter = '\n';


    Thread mWorkerThread = null;
    byte[] readBuffer;
    String lastBuffer="";
    int readBufferPosition;
    String msg = "";
    static int m1 = 100;
    static int m2 = 100;
    static int m3 = 100;
    static int m4 = 100;
    static boolean max = false;
    public Bluetooth() {
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        sRemoteDevice = intent.getStringExtra(Intent.EXTRA_TEXT);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mRemoteDevie = mBluetoothAdapter.getRemoteDevice(sRemoteDevice);
        UUID uuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //uuid: 범용 고유 식별자, IP와 비슷한 개념

        try {
            mSocket = mRemoteDevie.createRfcommSocketToServiceRecord(uuid); // 블루투스 통신용 소켓 생성
            mSocket.connect();
            mOutputStream = mSocket.getOutputStream();
            mInputStream = mSocket.getInputStream(); // RX, TX와 비슷한 개념

            // 데이터 수신 준비.
            beginListenForData();

        } catch (Exception e) { // 블루투스 연결 중 오류 발생 시 오류 메시지 출력
            e.printStackTrace();
        }
        return START_NOT_STICKY;
    }
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
    static void sendData(String msg) { // 문자열 전송(쓰레드 사용 x)
        if (!msg.isEmpty()) {
            msg += mStrDelimiter;  // 문자열 종료표시 (\n)
            try {
                mOutputStream.write(msg.getBytes());  // 문자열을 Byte로 변환하여 전송.
            } catch (Exception e) {  // 문자열 전송 도중 오류 발생 시 오류 메시지 출력
                e.printStackTrace();
            }
        }
    }
    void beginListenForData() { // 블루투스 수신 메소드
        final Handler handler = new Handler();

        readBufferPosition = 0;
        readBuffer = new byte[1024];


        new Thread(new Runnable() { // 문자열 수신 쓰레드 선언
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        int byteAvailable = mInputStream.available();
                        if (byteAvailable > 0) {
                            byte[] packetBytes = new byte[byteAvailable];
                            mInputStream.read(packetBytes);
                            for (int i = 0; i < byteAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == mCharDelimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);

                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if(!data.equals(lastBuffer)) // 이전과 다른 값이 수신되면 packet 메소드 실행
                                                packet(data);
                                            lastBuffer = data;
                                        }

                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }

                    } catch (Exception e) {    // 데이터 수신 중 오류 발생 시 오류 메시지 출력
                        e.printStackTrace();
                    }
                }
            }

        }).start(); // 쓰레드 실행
        sendData("start");
    }
    void packet(String packet) { //받아온 문자열을 처리하는 메소드
        Log.d("Service", packet);

        StringTokenizer st = new StringTokenizer(packet); // 문자열을 공백을 기준으로 분리

        m1 = Integer.parseInt(st.nextToken());
        m2 = Integer.parseInt(st.nextToken());
        m3 = Integer.parseInt(st.nextToken());
        m4 = Integer.parseInt(st.nextToken());
        max = (m1>360);
    }
    @Override
    public void onDestroy() {//앱이 종료될 때 데이터 수신 쓰레드 종료
        try {
            mWorkerThread.interrupt();
            mInputStream.close();
            mSocket.close();
        } catch (Exception e) {
        }
        super.onDestroy();
    }
}
