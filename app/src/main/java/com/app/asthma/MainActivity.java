package com.app.asthma;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
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
import com.mcc.ul.Range;
import com.mcc.ul.ULException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Stack;
import java.util.Timer;

public class MainActivity extends AppCompatActivity implements HomeFragment.OnDeviceSelectedListener{
    private final static String TAG = MainActivity.class.getSimpleName();


    private Toolbar toolbar;
    private TabLayout tabLayout;
    private ViewPager viewPager;
    private boolean connecSelect;


    private boolean mConnected = false;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private DaqReader daqReader;

    private BluetoothConnectionhelper mConnectionHepler;
    private Stack<Integer> currentPacket;
    private ArrayList<DataPacket> historyPackets;
    private long index=0;
    private ViewPagerAdapter adapter;
    private RealTimeFragment realTimeFragment;
    private HomeFragment homeFragment;
    private TextView daqValue;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        daqValue = (TextView)findViewById(R.id.valueId);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        viewPager = (ViewPager) findViewById(R.id.viewpager);
        setupViewPager(viewPager);

        tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean writePermission = Permissions.selfPermissionGranted(this, Permissions.PERMISSION_STORAGE_WRITE);
            boolean readPermission = Permissions.selfPermissionGranted(this, Permissions.PERMISSION_STORAGE_READ);
            boolean locationPermission = Permissions.selfPermissionGranted(this, Permissions.PERMISSION_LOCATION);
            if(!writePermission || !readPermission || !locationPermission) {
                Permissions.requestPermissionsForM(MainActivity.this, new String[]{Permissions.PERMISSION_STORAGE_READ,Permissions.PERMISSION_STORAGE_WRITE,
                        Permissions.PERMISSION_LOCATION}, Permissions.PERMISSION_ALL_RESULT);

            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if(mConnected){
            mConnectionHepler.startServiceInForeground();
        }

        if(mGattUpdateReceiver!=null)
            try {
                unregisterReceiver(mGattUpdateReceiver);
            }catch(Exception e) {

            }
    }

    @Override
    public void onResume() {
        super.onResume();


        if(mConnectionHepler!=null){
            mConnectionHepler.bindBluetoothService();
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
            mConnectionHepler.stopForeground();
        }
    }


    private void setupViewPager(ViewPager viewPager) {
        adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new HomeFragment(), "Home");
//        adapter.addFragment(new RealTimeFragment(), "Realtime");
//        adapter.addFragment(new HistoryFragment(), "History");
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(2);

    }

    @Override
    public void OnDeviceSelected(BluetoothDevice device, boolean connecSelect, int timerTime){
        mDeviceName = device.getName();
        mDeviceAddress = device.getAddress();
        mConnectionHepler = new BluetoothConnectionhelper(getApplicationContext(), mDeviceAddress, mDeviceName, timerTime*1000);

        if(DaqReader.instance == null) daqReader = DaqReader.getInstance(getApplicationContext(), timerTime);
        this.connecSelect = connecSelect;
        if(!connecSelect) {
            mConnectionHepler.setSyncComplete(!connecSelect);
        }
        mConnectionHepler.startBluetoothService();
        mConnectionHepler.bindBluetoothService();

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }



    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                if(realTimeFragment!=null){
                    realTimeFragment.setDisplay(RealTimeFragment.DisplayLayout.DATA);
                }
                //updateConnectionState(R.string.connected);
                if(currentPacket==null){
                    currentPacket=new Stack<Integer>();
                }
                if(historyPackets==null){
                    historyPackets=new ArrayList<DataPacket>();
                }
                currentPacket.clear();
                historyPackets.clear();
                mConnectionHepler.setIndex(0);

                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                if(realTimeFragment!=null){
                    realTimeFragment.setDisplay(RealTimeFragment.DisplayLayout.NO_DATA);
                }

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                mConnectionHepler.setMTU(245);
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                byte[] thirtySecondData = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                double[] daqThirtyData = daqReader.readBuffer();

                List<DataPacket> currPacket = mConnectionHepler.processNewData(thirtySecondData, daqThirtyData);

            }else if(BluetoothConnectionhelper.TIMER_ELAPSE.equals(action)){
                if(homeFragment == null){
                    homeFragment = (HomeFragment) adapter.getItem(0);
                }
                homeFragment.setTestStatus(View.VISIBLE);
                homeFragment.clearList();
            }else if(DaqReader.DAQ_DATA.equals(action)){
                String dval = daqValue.getText().toString();
                double d = intent.getDoubleExtra(BluetoothLeService.EXTRA_DATA, -1);
            }

        }
    };

    public void connectServiceAndSyncData(boolean connecSelect){
        syncData(connecSelect);
    }

    public void syncData(boolean connecSelect){
        if(!mConnectionHepler.isNotified()){
//            if(!mConnectionHepler.notifyOnCharFour()){
//                Toast.makeText(this,"Bluetooth Connection failure", Toast.LENGTH_LONG);
//            };
        }
        homeFragment = (HomeFragment) adapter.getItem(0);
        if(connecSelect) {

            if (homeFragment != null)
               /// homeFragment.setSyncStatus(HomeFragment.SyncStatus.SYNC_ON);
            if (realTimeFragment != null) {
                realTimeFragment.setDisplay(RealTimeFragment.DisplayLayout.SYNC);
            }
        }else {
            if (realTimeFragment != null) {
                realTimeFragment.setDisplay(RealTimeFragment.DisplayLayout.DATA);
            }
        }
        //mConnectionHepler.syncData(connecSelect);
    }


    public void disconnect(){

        mConnectionHepler.setIndex(0);
        mConnectionHepler.disconnect();
        //homeFragment.setSyncStatus(HomeFragment.SyncStatus.SYNC_OFF);


        viewPager.setCurrentItem(0);

    }



    private void displayRealTimeData(DataPacket dataPacket){

        if(viewPager.getCurrentItem() != 1)
            viewPager.setCurrentItem(1);
        realTimeFragment = (RealTimeFragment) adapter.getItem(1);
        if(realTimeFragment!=null){
            realTimeFragment.setDisplay(RealTimeFragment.DisplayLayout.DATA);
            realTimeFragment.displayRealTimeData(dataPacket, mDeviceName);
        }
    }

    public boolean getMConnected(){
        return mConnected;
    }
    class ViewPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        public ViewPagerAdapter(FragmentManager manager) {
            super(manager);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        public void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitleList.get(position);
        }
    }


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothConnectionhelper.TIMER_ELAPSE);
        intentFilter.addAction(DaqReader.DAQ_DATA);
        return intentFilter;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        //Fragment fragment = getFragmentManager().findFragmentById()
        switch (requestCode) {
            case Permissions.PERMISSION_ALL_RESULT: {
                if(grantResults.length==3) {
                    boolean writePermission = (grantResults[0] == PackageManager.PERMISSION_GRANTED);
                    boolean readPermission = (grantResults[1] == PackageManager.PERMISSION_GRANTED);
                    boolean locationPermission = (grantResults[2] == PackageManager.PERMISSION_GRANTED);
                    if(!writePermission || !readPermission || !locationPermission) {
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
                        Toast.makeText(getApplicationContext(), "Permission to access "+toastDisplay+" has been denied", Toast.LENGTH_LONG).show();
                    }
                }
                return;
            }
        }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if(mConnectionHepler!=null){
            mConnectionHepler.onDestroy();
        }
    }
}
