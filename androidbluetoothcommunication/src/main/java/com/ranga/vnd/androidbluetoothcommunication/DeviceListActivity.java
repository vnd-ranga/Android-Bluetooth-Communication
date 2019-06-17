package com.ranga.vnd.androidbluetoothcommunication;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.util.Objects;
import java.util.Set;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Toast;

import static com.ranga.vnd.androidbluetoothcommunication.BluetoothState.REQUEST_ENABLE_BT;
import static com.ranga.vnd.androidbluetoothcommunication.BluetoothState.REQUEST_ACCESS_COARSE_LOCATION;
import static com.ranga.vnd.androidbluetoothcommunication.BluetoothState.REQUEST_ACCESS_BLUETOOTH;

public class DeviceListActivity extends AppCompatActivity {
    // Debugging
    private static final String TAG = "BluetoothCommunication";
    private static final boolean D = true;

    // Member fields
    private BluetoothAdapter mBtAdapter;
    private ArrayAdapter<String> mPairedDevicesArrayAdapter;
    private Set<BluetoothDevice> pairedDevices;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup the window
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        int listId = getIntent().getIntExtra("layout_list", R.layout.activity_device_list);
        setContentView(listId);

        String strBluetoothDevices = getIntent().getStringExtra("bluetooth_devices");
        if (strBluetoothDevices == null)
            strBluetoothDevices = "Bluetooth Devices";
        setTitle(strBluetoothDevices);

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);

        // Initialize the button to perform device discovery
        Button scanButton = findViewById(R.id.button_scan);
        String strScanDevice = getIntent().getStringExtra("scan_for_devices");
        if (strScanDevice == null)
            strScanDevice = "SCAN FOR DEVICES";
        scanButton.setText(strScanDevice);
        scanButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (!mBtAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else{
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {  // Only ask for these permissions on runtime when running Android 6.0 or higher
                        switch (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_COARSE_LOCATION)) {
                            case PackageManager.PERMISSION_DENIED:
                                ((TextView) new AlertDialog.Builder(DeviceListActivity.this)
                                        .setTitle("Runtime Permissions up ahead")
                                        .setMessage(Html.fromHtml("<p>To find nearby bluetooth devices please click \"Allow\" on the runtime permissions popup.</p>" +
                                                "<p>For more info see <a href=\"http://developer.android.com/about/versions/marshmallow/android-6.0-changes.html#behavior-hardware-id\">here</a>.</p>"))
                                        .setNeutralButton("Okay", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                                    ActivityCompat.requestPermissions(DeviceListActivity.this,
                                                            new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                                            REQUEST_ACCESS_COARSE_LOCATION);
                                                }
                                            }
                                        })
                                        .show()
                                        .findViewById(android.R.id.message))
                                        .setMovementMethod(LinkMovementMethod.getInstance());       // Make the link clickable. Needs to be called after show(), in order to generate hyperlinks
                                break;
                            case PackageManager.PERMISSION_GRANTED:
                                doDiscovery();
                                break;
                        }
                    }
                }
            }
        });

        // Initialize array adapters. One for already paired devices
        // and one for newly discovered devices
        int layout_text = getIntent().getIntExtra("layout_text", R.layout.device_name);
        mPairedDevicesArrayAdapter = new ArrayAdapter<>(this, layout_text);

        // Find and set up the ListView for paired devices
        ListView pairedListView = findViewById(R.id.list_devices);
        pairedListView.setAdapter(mPairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListener);

        // Register for broadcasts when a device is discovered
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, intentFilter);
        // Get the local Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // Get a set of currently paired devices
        pairedDevices = mBtAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            String noDevices = "No devices found";
            mPairedDevicesArrayAdapter.add(noDevices);
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        // Make sure we're not doing discovery anymore
        if (mBtAdapter != null) {
            mBtAdapter.cancelDiscovery();
        }

        // Unregister broadcast listeners
        this.unregisterReceiver(mReceiver);
    }

    // Start device discover with the BluetoothAdapter
    private void doDiscovery() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "ble not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Remove all element from the list
        mPairedDevicesArrayAdapter.clear();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            String strNoFound = getIntent().getStringExtra("no_devices_found");
            if (strNoFound == null)
                strNoFound = "No devices found";
            mPairedDevicesArrayAdapter.add(strNoFound);
        }

        // Indicate scanning in the title
        String strScanning = getIntent().getStringExtra("scanning");
        if (strScanning == null)
            strScanning = "Scanning for devices...";
        setProgressBarIndeterminateVisibility(true);
        setTitle(strScanning);

        // Turn on sub-title for new devices
        // findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);
        // If we're already discovering, stop it
        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }

        // Request discover from BluetoothAdapter
        mBtAdapter.startDiscovery();
    }

    // The on-click listener for all devices in the ListViews
    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // Cancel discovery because it's costly and we're about to connect
            if (mBtAdapter.isDiscovering())
                mBtAdapter.cancelDiscovery();

            String strNoFound = getIntent().getStringExtra("no_devices_found");
            if (strNoFound == null)
                strNoFound = "No devices found";
            if (!((TextView) v).getText().toString().equals(strNoFound)) {
                // Get the device MAC address, which is the last 17 chars in the View
                String info = ((TextView) v).getText().toString();
                String address = info.substring(info.length() - 17);

                // Create the result Intent and include the MAC address
                Intent intent = new Intent();
                intent.putExtra(BluetoothState.EXTRA_DEVICE_ADDRESS, address);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {  // Only ask for these permissions on runtime when running Android 6.0 or higher
                    switch (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.BLUETOOTH_ADMIN)) {
                        case PackageManager.PERMISSION_DENIED:
                            ((TextView) new AlertDialog.Builder(DeviceListActivity.this)
                                    .setTitle("Runtime Permissions up ahead")
                                    .setMessage(Html.fromHtml("<p>To connect bluetooth devices please click \"Allow\" on the runtime permissions popup.</p>" +
                                            "<p>For more info see <a href=\"http://developer.android.com/about/versions/marshmallow/android-6.0-changes.html#behavior-hardware-id\">here</a>.</p>"))
                                    .setNeutralButton("Okay", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                                                ActivityCompat.requestPermissions(DeviceListActivity.this,
                                                        new String[]{Manifest.permission.BLUETOOTH_ADMIN},
                                                        REQUEST_ACCESS_BLUETOOTH);
                                            }
                                        }
                                    })
                                    .show()
                                    .findViewById(android.R.id.message))
                                    .setMovementMethod(LinkMovementMethod.getInstance());       // Make the link clickable. Needs to be called after show(), in order to generate hyperlinks
                            break;
                        case PackageManager.PERMISSION_GRANTED:
                            // Set result and finish this Activity
                            setResult(AppCompatActivity.RESULT_OK, intent);
                            finish();
                            break;
                    }
                }

            }
        }
    };

    // The BroadcastReceiver that listens for discovered devices and
    // changes the title when discovery is finished
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                // If it's already paired, skip it, because it's been listed already
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    String strNoFound = getIntent().getStringExtra("no_devices_found");
                    if (strNoFound == null)
                        strNoFound = "No devices found";

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        if (Objects.requireNonNull(mPairedDevicesArrayAdapter.getItem(0)).equals(strNoFound)) {
                            mPairedDevicesArrayAdapter.remove(strNoFound);
                        }
                    }
                    mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }

                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                String strSelectDevice = getIntent().getStringExtra("select_device");
                if (strSelectDevice == null)
                    strSelectDevice = "Select a device to connect";
                setTitle(strSelectDevice);
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == BluetoothState.REQUEST_CONNECT_DEVICE) {
            if(resultCode == AppCompatActivity.RESULT_OK)
                doDiscovery();
        } else if(requestCode == BluetoothState.REQUEST_ENABLE_BT) {
            if(resultCode == AppCompatActivity.RESULT_OK) {
                doDiscovery();
            } else {
                Toast.makeText(getApplicationContext()
                        , "Bluetooth was not enabled."
                        , Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_ACCESS_COARSE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permission was granted. Now you can call your method to open camera, fetch contact or whatever
                    doDiscovery();
                } else {
                    // Permission was denied.......
                    // You can again ask for permission from here
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
                break;
            }

        }
    }

}
