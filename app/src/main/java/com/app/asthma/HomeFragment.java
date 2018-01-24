package com.app.asthma;

/**
 * Created by siddartha on 11/6/16.
 */
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.mcc.ul.AiChanMode;
import com.mcc.ul.AiDevice;
import com.mcc.ul.AiInfo;
import com.mcc.ul.AiUnit;
import com.mcc.ul.DaqDevice;
import com.mcc.ul.DaqDeviceConnectionPermissionListener;
import com.mcc.ul.DaqDeviceDescriptor;
import com.mcc.ul.DaqDeviceInfo;
import com.mcc.ul.DaqDeviceManager;
import com.mcc.ul.ErrorInfo;
import com.mcc.ul.Range;
import com.mcc.ul.ULException;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


public class HomeFragment extends Fragment implements AdapterView.OnItemClickListener, AlertDialog.OnClickListener{

    public final static int ONE_PACKET_SIZE = 30;

    private DaqDeviceManager mDaqDeviceManager;
    private DaqDevice mDaqDevice;
    private AiDevice mAiDevice;

    private Timer mAInTimer;
    ArrayList<DaqDeviceDescriptor> daqDevInventory;
    public static final String DAQ_DATA = "DAQ_DATA";

    int userTimer;
    AiChanMode chanMode = AiChanMode.DIFFERENTIAL;
    int lowChan = 0;
    int highChan = 0;
    double sampleRate = 60;
    Range range = Range.BIP20VOLTS;
    long mTimerPeriod = (long)(1000/60);
    AiUnit units = AiUnit.VOLTS;

    private double[] buffer = new double[ONE_PACKET_SIZE];
    int bufferIndex = 0;

    double mAInData;
    int mSamplesRead;

    final static int MIN_TIMER_PERIOD = 10;











    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private boolean locationPermission;
    private boolean readPermission;
    private boolean writePermission;
    private Handler mHandler;
    OnDeviceSelectedListener mListener;
    BluetoothDevice selectedDevice;

    View inflatedView;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    Button searchButton;
    TextView syncStatus;
    TextView testStatus;
    TextView valuesText;


    EditText timerTime;

    private boolean searching=false;
    public boolean syncing = false;
    public enum SyncStatus{
        SYNC_ON,
        SYNC_COMPLETE,
        SYNC_OFF
    }


    public interface OnDeviceSelectedListener {
        public void OnDeviceSelected(BluetoothDevice selectedDevice, boolean connecSelect, int timerTime);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnDeviceSelectedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement OnArticleSelectedListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        mHandler = new Handler();


        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(getActivity(), R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            getActivity().finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(getActivity(), R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            getActivity().finish();
            return;
        }



//        mDaqDevice = null;
//        mAiDevice = null;
//        mDaqDeviceManager = new DaqDeviceManager(inflatedView.getContext());
//
//        mAInTimer = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
         inflatedView = inflater.inflate(R.layout.home_fragment, container, false);
        mLeDeviceListAdapter = new LeDeviceListAdapter(getActivity());

        syncStatus = (TextView)inflatedView.findViewById(R.id.syncStatus);
        syncStatus.setVisibility(View.INVISIBLE);

        testStatus = (TextView)inflatedView.findViewById(R.id.testStatus);
        //valuesText = (TextView)inflatedView.findViewById(R.id.valuesID);

        testStatus.setVisibility(View.INVISIBLE);

        timerTime = (EditText)inflatedView.findViewById(R.id.timerTime);

        searchButton=(Button) inflatedView.findViewById(R.id.search);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    writePermission = Permissions.selfPermissionGranted(inflatedView.getContext(), Permissions.PERMISSION_STORAGE_WRITE);
                    readPermission = Permissions.selfPermissionGranted(inflatedView.getContext(), Permissions.PERMISSION_STORAGE_READ);
                    locationPermission = Permissions.selfPermissionGranted(inflatedView.getContext(), Permissions.PERMISSION_LOCATION);
                    if(readPermission && writePermission && locationPermission) {
                        scanforDevices();
                    }
                    else{
                        requestPermissions(new String[]{Permissions.PERMISSION_STORAGE_READ,Permissions.PERMISSION_STORAGE_WRITE,
                                Permissions.PERMISSION_LOCATION}, Permissions.PERMISSION_ALL_RESULT);

                    }

                }else{
                    scanforDevices();
                }
            }
        });
        ListView listView = (ListView)inflatedView.findViewById(R.id.list);
        listView.setAdapter(mLeDeviceListAdapter);

        listView.setOnItemClickListener(this);
        return inflatedView;
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        selectedDevice = mLeDeviceListAdapter.getDevice(position);
        if (selectedDevice == null) return;
        if (mScanning) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
        }

        setTestStatus(View.INVISIBLE);
        mListener.OnDeviceSelected(selectedDevice,true, Integer.parseInt(timerTime.getText().toString()));

    }

    @Override
    public void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }


    @Override
    public void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    private void scanforDevices(){
        if(!mScanning){
            mLeDeviceListAdapter.clear();
            scanLeDevice(true);
        }else{
            scanLeDevice(false);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which){
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:
                // int which = -2
                mListener.OnDeviceSelected(selectedDevice,false, Integer.parseInt(timerTime.getText().toString()));
                dialog.dismiss();
                break;
            case DialogInterface.BUTTON_POSITIVE:
                // int which = -1
                mListener.OnDeviceSelected(selectedDevice,true, Integer.parseInt(timerTime.getText().toString()));
                dialog.dismiss();
                break;
        }
    }


    public void setTestStatus(int status){
        testStatus.setVisibility(status);
    }

    public void clearList(){ mLeDeviceListAdapter.clear();}


    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private Context context;

        public LeDeviceListAdapter(Context con) {
            super();
            context = con;
            mLeDevices = new ArrayList<BluetoothDevice>();
        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            if (view == null) {
                view = inflater.inflate(R.layout.listitem_device, viewGroup, false);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }

    private void scanLeDevice(final boolean enable){

        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    searchButton.setText("Search");
                }
            }, SCAN_PERIOD);

            mScanning = true;
            searchButton.setText("Stop");
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            searchButton.setText("Search");
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String deviceName = device.getName();
                            //if(deviceName!=null && deviceName.toUpperCase().contains(BLE_DEVICE_NAME)){
                                mLeDeviceListAdapter.addDevice(device);
                                mLeDeviceListAdapter.notifyDataSetChanged();
                            //}
                        }
                    });
                }
            };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            getActivity().finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case Permissions.PERMISSION_ALL_RESULT: {
                if(grantResults.length==3) {
                    boolean writePermission = (grantResults[0] == PackageManager.PERMISSION_GRANTED);
                    boolean readPermission = (grantResults[1] == PackageManager.PERMISSION_GRANTED);
                    boolean locationPermission = (grantResults[2] == PackageManager.PERMISSION_GRANTED);
                    if(writePermission && readPermission && locationPermission){
                        scanforDevices();
                    }
                    else{
                        String toastDisplay = "";

                        if(!writePermission && !readPermission) {
                            toastDisplay = "storage";
                        }
                        if(!locationPermission) {
                            toastDisplay = "location";
                        }
                        if(!locationPermission && !writePermission && !readPermission) {
                            toastDisplay = "location and storage";
                        }
                        if(!writePermission && !readPermission && !locationPermission) {
                            toastDisplay = "storage and location";
                        }
                        Toast.makeText(getActivity(), "Permission to access "+toastDisplay+" has been denied", Toast.LENGTH_LONG).show();
                    }
                }
                return;
            }


        }
    }





//    public void startDAQ(int userTimer) {
//        sampleRate *= userTimer;
//        detectAndSelectDaqDevice();
//        connectToDaqDevice();
//        startAIn();
//    }
//
//    private void detectAndSelectDaqDevice() {
//
//        daqDevInventory = mDaqDeviceManager.getDaqDeviceInventory();
//
//        if(daqDevInventory.size() > 0)
//            Toast.makeText(inflatedView.getContext(), daqDevInventory.size() + " DAQ device(s) detected ", Toast.LENGTH_LONG);
//        else
//        {
//            Toast.makeText(inflatedView.getContext(), "No DAQ devices detected", Toast.LENGTH_LONG);
//            return;
//        }
//
//        mDaqDevice = mDaqDeviceManager.createDaqDevice(daqDevInventory.get(0));
//        DaqDeviceInfo devInfo = mDaqDevice.getInfo();
//
//        if(devInfo.hasAiDev()) {
//
//            mAiDevice =  mDaqDevice.getAiDev();
//            AiInfo aiInfo = mAiDevice.getInfo();
//
//            // Get the maximum supported scan rate
//            double maxRate = aiInfo.getMaxScanRate();
//
//            if(maxRate > 0.0) {
//                if(sampleRate > maxRate)
//                    sampleRate = maxRate;
//            }
//
//        }else {
//            Toast.makeText(inflatedView.getContext(), "Selected device does not support analog input", Toast.LENGTH_LONG);
//            return;
//        }
//    }
//
//    private void connectToDaqDevice() {
//        if(mDaqDevice != null && mDaqDevice.hasConnectionPermission()) {
//            mDeviceConnectionPermissionListener.onDaqDevicePermission(mDaqDevice.getDescriptor(), true);
//        }
//        else {
//            try {
//                mDaqDevice.requestConnectionPermission(mDeviceConnectionPermissionListener);
//            } catch (ULException e) {
//                Toast.makeText(inflatedView.getContext(), e.getMessage(), Toast.LENGTH_LONG);
//                return;
//            }
//        }
//    }
//
//    private void disconnectDaqDevice() {
//        mDaqDevice.disconnect();
//
//        stopAInTimer();
//    }
//
//    private void startAIn() {
//        mAInData = 0; //let garbage collector reclaim the existing data memory
//
//        mSamplesRead = 0;
//
//        if(lowChan > highChan) {
//            stopAInTimer();
//            return;
//        }
//
//        startAInTimer();
//
//    }
//
//
//    private void startAInTimer() {
//
//        long timerPeriod = mTimerPeriod;
//
//        if(timerPeriod >= MIN_TIMER_PERIOD) {
//            mAInTimer = new Timer();
//            mAInTimer.schedule(new TimerTask() {
//                @Override
//                public void run() {
//                    aInTimer();
//                }
//
//            }, 0, timerPeriod);
//        }
//    }
//
//
//
//    private void stopAInTimer() {
//        if(mAInTimer != null)
//            mAInTimer.cancel();
//    }
//
//    private void aInTimer(){
//        if(mSamplesRead >= sampleRate)
//            return;
//
//        mAInData = 0;
//
//        try {
//            synchronized(this) {
//                if(mAiDevice != null) {
//                    mAInData = mAiDevice.aIn(0, chanMode, range, units);
//                    mSamplesRead++;
//                    bufferPoint(mAInData);
//                }
//            }
//
//        } catch (final ULException e) {
//            stopAInTimer();
//
//            if(e.getErrorInfo() == ErrorInfo.DEADDEV)
//                disconnectDaqDevice();
//            e.printStackTrace();
//        }
//    }
//
//    private void bufferPoint(double point){
//
//        if(bufferIndex < ONE_PACKET_SIZE){
//            buffer[bufferIndex++] = point;
//        }
//        showPoint(point);
//    }
//
//    public double[] readBuffer(){
//        double[] buff = buffer.clone();
//        buffer = new double[ONE_PACKET_SIZE];
//        bufferIndex = 0;
//        return buff;
//    }
//
//    public void showPoint(double point){
//        String values = valuesText.getText().toString();
//        valuesText.setText(values+", "+point);
//        Toast.makeText(inflatedView.getContext(), "new value- "+ point, Toast.LENGTH_SHORT);
//    }
//
//    public DaqDeviceConnectionPermissionListener mDeviceConnectionPermissionListener = new DaqDeviceConnectionPermissionListener() {
//        public void onDaqDevicePermission(DaqDeviceDescriptor daqDeviceDescriptor, boolean permissionGranted) {
//            if(permissionGranted)
//            {
//                try {
//                    mDaqDevice.connect();
//                    Toast.makeText(inflatedView.getContext(), "Connected to " + mDaqDevice, Toast.LENGTH_LONG);
//
//                } catch (Exception e) {
//                    Toast.makeText(inflatedView.getContext(), "Unable to connect to " + mDaqDevice, Toast.LENGTH_LONG);
//                }
//            }
//            else {
//                Toast.makeText(inflatedView.getContext(), "Permission denied to connect to ", Toast.LENGTH_LONG);
//            }
//        }
//    };



}
