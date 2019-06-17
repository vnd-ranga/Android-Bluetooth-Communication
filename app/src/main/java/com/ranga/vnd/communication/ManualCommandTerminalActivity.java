package com.ranga.vnd.communication;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.ranga.vnd.androidbluetoothcommunication.BluetoothCommunication;
import com.ranga.vnd.androidbluetoothcommunication.BluetoothState;
import com.ranga.vnd.androidbluetoothcommunication.DeviceListActivity;

public class ManualCommandTerminalActivity extends AppCompatActivity {

    BluetoothCommunication bluetoothCommunication;

    TextView textStatus, textRead;
    EditText etMessage;

    Menu menu;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_command_terminal);

        textRead = findViewById(R.id.textRead);
        textStatus = findViewById(R.id.textStatus);
        etMessage = findViewById(R.id.etMessage);

        bluetoothCommunication = new BluetoothCommunication(this);

        if(!bluetoothCommunication.isBluetoothAvailable()) {
            Toast.makeText(getApplicationContext()
                    , "Bluetooth is not available"
                    , Toast.LENGTH_SHORT).show();
            finish();
        }

        bluetoothCommunication.setOnDataReceivedListener(new BluetoothCommunication.OnDataReceivedListener() {
            public void onDataReceived(byte[] data, String message) {
                textRead.append(message + "\n");
            }
        });

        bluetoothCommunication.setBluetoothConnectionListener(new BluetoothCommunication.BluetoothConnectionListener() {
            public void onDeviceDisconnected() {
                textStatus.setText(getString(R.string.not_connect));
                menu.clear();
                getMenuInflater().inflate(R.menu.menu_connection, menu);
            }

            public void onDeviceConnectionFailed() {
                textStatus.setText(getString(R.string.failed_connect));
            }

            public void onDeviceConnected(String name, String address) {
                textStatus.setText(getString(R.string.connected));
                menu.clear();
                getMenuInflater().inflate(R.menu.menu_disconnection, menu);
            }
        });
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        getMenuInflater().inflate(R.menu.menu_connection, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(id == R.id.menu_android_connect) {
            bluetoothCommunication.setDeviceTarget(BluetoothState.DEVICE_ANDROID);
			/*
			if(bluetoothCommunication.getServiceState() == BluetoothState.STATE_CONNECTED)
    			bluetoothCommunication.disconnect();*/
            Intent intent = new Intent(getApplicationContext(), DeviceListActivity.class);
            startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);
        } else if(id == R.id.menu_device_connect) {
            bluetoothCommunication.setDeviceTarget(BluetoothState.DEVICE_OTHER);
			/*
			if(bluetoothCommunication.getServiceState() == BluetoothState.STATE_CONNECTED)
    			bluetoothCommunication.disconnect();*/
            Intent intent = new Intent(getApplicationContext(), DeviceListActivity.class);
            startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);
        } else if(id == R.id.menu_disconnect) {
            if(bluetoothCommunication.getServiceState() == BluetoothState.STATE_CONNECTED)
                bluetoothCommunication.disconnect();
        }
        return super.onOptionsItemSelected(item);
    }

    public void onDestroy() {
        super.onDestroy();
        bluetoothCommunication.stopService();
    }

    public void onStart() {
        super.onStart();
        if (!bluetoothCommunication.isBluetoothEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, BluetoothState.REQUEST_ENABLE_BT);
        } else {
            if(!bluetoothCommunication.isServiceAvailable()) {
                bluetoothCommunication.setupService();
                bluetoothCommunication.startService(BluetoothState.DEVICE_ANDROID);
                setup();
            }
        }
    }

    public void setup() {
        Button btnSend = findViewById(R.id.btnSend);
        btnSend.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                if(etMessage.getText().length() != 0) {
                    bluetoothCommunication.send(etMessage.getText().toString(), true);
                    etMessage.setText("");
                }
            }
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == BluetoothState.REQUEST_CONNECT_DEVICE) {
            if(resultCode == AppCompatActivity.RESULT_OK)
                bluetoothCommunication.connect(data);
        } else if(requestCode == BluetoothState.REQUEST_ENABLE_BT) {
            if(resultCode == AppCompatActivity.RESULT_OK) {
                bluetoothCommunication.setupService();
                bluetoothCommunication.startService(BluetoothState.DEVICE_ANDROID);
                setup();
            } else {
                Toast.makeText(getApplicationContext()
                        , "Bluetooth was not enabled."
                        , Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

}
