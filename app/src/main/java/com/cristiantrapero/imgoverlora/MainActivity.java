package com.cristiantrapero.imgoverlora;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.dhaval2404.imagepicker.ImagePicker;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // Notifications config
    public static final String CHANNEL_ID = "101";
    public static final String CHANNEL_NAME = "notifications";

    // BLE buttons
    Button connectButton;
    Button disconnectButton;
    Button sendPictureButton;
    Button takePictureButton;

    // UI elements
    Uri imageUri;
    ImageView imageView;

    // Bytes to send
    InputStream imageStream;
    byte[] pictureByteArray;

    // Bluetooth configuration
    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothLeScanner scanner;
    BluetoothDevice bluetoothDevice;
    BluetoothGatt bluetoothGatt;
    BluetoothGattService service;
    BluetoothGattCharacteristic sendCharacteristic;

    // BLE characteristics
    // TODO public static final String MAC_ADDRESS = "80:7D:3A:93:6A:5A";
    public static final UUID UUID_SERVICE = UUID.fromString("62613134-6534-3437-6434-633739336563");
    public static final UUID UUID_SEND_CHARACTERISTIC = UUID.fromString("33613861-3230-3030-3231-636132343230");
    public static final String TAGBLESCANNER = "BLEScanner";
    public static final String TAGBLE = "BLE";
    UUID[] serviceUUIDs = new UUID[]{UUID_SERVICE};
    List<ScanFilter> filters = null;
    ScanSettings scanSettings = null;
    String[] bleName = new String[]{"LoPy-IMGoverLora"};

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);

        takePictureButton = findViewById(R.id.takePictureBtn);
        takePictureButton.setOnClickListener(view -> takePicture());

        sendPictureButton = findViewById(R.id.sendPictureBtn);
        sendPictureButton.setOnClickListener(v -> {
            Log.i(TAGBLE, "Send the image");
            pictureByteArray = new byte[2048];
            sendCharacteristic.setValue(pictureByteArray);
            sendCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            bluetoothGatt.writeCharacteristic(sendCharacteristic);
        });

        connectButton = findViewById(R.id.connectBLE);
        connectButton.setOnClickListener(v -> {
            // Start BLE
            startBLE();
        });

        disconnectButton = findViewById(R.id.disconnectBLE);
        disconnectButton.setOnClickListener(v -> {
            // Disconnect BLE
            stopBLE();
        });

        // Create notification channel
        createNotificationChannel();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            imageUri = data.getData();
            imageView.setImageURI(imageUri);

            // Convert to byte array
            try {
                imageStream = getContentResolver().openInputStream(imageUri);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            try {
                pictureByteArray = getBytes(imageStream);
                String str = new String(pictureByteArray, StandardCharsets.UTF_8); // for UTF-8 encoding

                Log.i("ByteArray:   ", String.valueOf(pictureByteArray.length));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void takePicture() {
        ImagePicker.with(this)
                .compress(1)            //Final image size will be less than 1 MB(Optional)
                .maxResultSize(1280, 720)    //Final image resolution will be less than 1080 x 1080(Optional)
                .start();
    }

    public byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    @SuppressLint("MissingPermission")
    private void startBLE() {
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
        }

        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner != null) {
            if (serviceUUIDs != null) {
                filters = new ArrayList<>();

                if (bleName != null) {
                    filters = new ArrayList<>();
                    for (String name : bleName) {
                        ScanFilter filter = new ScanFilter.Builder()
                                .setDeviceName(name)
                                .build();
                        filters.add(filter);
                    }
                }

                scanSettings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                        .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                        .setReportDelay(5000)
                        .build();
            }
            scanner.startScan(filters, scanSettings, scanCallback);
            Log.d(TAGBLESCANNER, "BLE scan started");
        } else {
            Log.e(TAGBLESCANNER, "Could not get scanner object");
        }
    }

    @SuppressLint("MissingPermission")
    private void stopBLE() {
        runOnUiThread(() -> {
            connectButton.setEnabled(true);
            disconnectButton.setEnabled(false);
            sendPictureButton.setEnabled(false);
        });
        bluetoothGatt.disconnect();
    }

    final private BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAGBLE, "Changes in Gatt connection state");

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAGBLE, "Stop BLE scan");
                scanner.stopScan(scanCallback);
                //showMessage("Conectado por BLE");
                runOnUiThread(() -> {
                    disconnectButton.setEnabled(true);
                    connectButton.setEnabled(false);
                    sendPictureButton.setEnabled(true);
                });
                gatt.discoverServices();
            } else {
                gatt.close();
            }
            super.onConnectionStateChange(gatt, status, newState);
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            final List<BluetoothGattService> services = gatt.getServices();
            Log.i("BLE", String.format(Locale.ENGLISH, "discovered %d services for '%s'", services.size(), services.get(0).getUuid()));

            service = gatt.getService(UUID_SERVICE);
            if (service != null) {
                Log.i(TAGBLE, "Service obtained");
                sendCharacteristic = service.getCharacteristic(UUID_SEND_CHARACTERISTIC);
                if (sendCharacteristic != null) {
                    Log.i(TAGBLE, "Send image characteristic found");
                    gatt.setCharacteristicNotification(sendCharacteristic, true);
                    gatt.requestMtu(512);
                } else {
                    Log.i(TAGBLE, "Send image characteristic not found");
                }
            }

            super.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.w("YEEEEEEEAHHHAHA", Integer.toString(mtu));
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            Log.w("STATUS:", Integer.toString(status));

            if (status == BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH) {
                Log.e("BluetoothGattCallback", "Write exceeded connection ATT MTU!");
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BluetoothGattCallback", "Wrote to characteristic $uuid | value: ${value.toHexString()}");

            }
            if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
                Log.e("BluetoothGattCallback", "Write not permitted for $uuid!");

            }
        }

        }

        ;

        final private ScanCallback scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                Log.d(TAGBLE, "BLE scan result");
                super.onScanResult(callbackType, result);
            }

            @SuppressLint("MissingPermission")
            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                Log.d(TAGBLE, "BLE scan results");
                bluetoothDevice = results.get(0).getDevice();
                bluetoothGatt = bluetoothDevice.connectGatt(getApplicationContext(), false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE);
                super.onBatchScanResults(results);
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.d(TAGBLE, "Scan error");
                super.onScanFailed(errorCode);
            }
        };

        private void createNotificationChannel() {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
                // Register the channel with the system; you can't change the importance
                // or other notification behaviors after this
                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                notificationManager.createNotificationChannel(channel);
            }
        }

    }