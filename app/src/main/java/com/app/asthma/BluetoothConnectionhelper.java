package com.app.asthma;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Stack;
import java.util.UUID;

/**
 * Created by siddartha on 11/7/16.
 */

public class BluetoothConnectionhelper {
    private final static String TAG = BluetoothConnectionhelper.class.getSimpleName();


    BluetoothLeService mBluetoothLeService;
    boolean mConnected = false;
    private String mDeviceName;
    private String mDeviceAddress;
    private int timerTime;
    Context context;

    public static final int PACKET_LENGTH=80;
    private static final byte WRITE_VALUE = 1;
    private final int START_BYTE[]={0};
    private final int END_BYTE[]={13,23};
    private final int SYNC_START_BYTE=68;
    private final int SYNC_END_BYTE=85;
    private boolean syncComplete= false;

    private long index=0;
    private long syncIndex=0;
    private enum PacketType{
        SYNC_START,
        SYNC_END,
        DATA_PACKET
    }
    private PrintWriter syncWriter;
    private PrintWriter realTimeWriter;

    public static final String TIMER_ELAPSE = "TIMER_ELAPSE";


    private List<BluetoothGattService> gattServices;
    private BluetoothGattService realTimeDataService;
    private BluetoothGattService breezingService;
    private BluetoothGattCharacteristic breezingCharacteristic;
    private BluetoothGattCharacteristic realTimeDataCharacteristic;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattService syncDataWriteService;
    private BluetoothGattCharacteristic syncDataWriteCharacteristic;
    private boolean notified=false;
    private boolean onSync = false;


    public BluetoothConnectionhelper(Context context, String address, String deviceName, int timerTime){
        this.context = context;
        mDeviceAddress = address;
        mDeviceName = deviceName;
        this.timerTime = timerTime;
    }

    public void startBluetoothService(){
        Intent gattServiceStartIntent = new Intent(context, BluetoothLeService.class);
        context.startService(gattServiceStartIntent);
    }

    public void bindBluetoothService(){
        Intent gattServiceBindIntent = new Intent(context, BluetoothLeService.class);
        context.bindService(gattServiceBindIntent, mServiceConnection, context.BIND_AUTO_CREATE);
    }

    public void setIndex(long index){
        this.index=index;
    }

    public boolean isOnSync(){return onSync;}

    public boolean isNotified(){return notified;}

    public boolean isSyncComplete(){return syncComplete;}

    public void setSyncComplete(boolean syncComplete) {
        this.syncComplete = syncComplete;
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                //context.finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
            startTimer();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };



    private void startTimer(){
        new CountDownTimer(timerTime, 1000) {

            public void onTick(long millisUntilFinished) {
            }

            public void onFinish() {
                mBluetoothLeService.disableNotifications();
                final Intent intent = new Intent(TIMER_ELAPSE);

                intent.putExtra(mBluetoothLeService.EXTRA_DATA, true);
                context.sendBroadcast(intent);
                disconnect();
            }
        }.start();
    }


    public void setMTU(int mtu){
        mBluetoothLeService.setMTU(mtu);
    }

    public boolean notifyOnCharFour(){

        if(mBluetoothLeService!=null){
            gattServices = mBluetoothLeService.getSupportedGattServices();
            for (BluetoothGattService service:gattServices) {
                if(service.getUuid().toString().equals("0000fff0-0000-1000-8000-00805f9b34fb"))
                {
                    breezingService = service;
                }
            }
            breezingCharacteristic = breezingService.getCharacteristic(UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb"));

            final int charaProp = breezingCharacteristic.getProperties();

            if (mNotifyCharacteristic != null) {
                mBluetoothLeService.setCharacteristicNotification(
                        mNotifyCharacteristic, true);
                mNotifyCharacteristic = null;
            }
            if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                mNotifyCharacteristic = breezingCharacteristic;
                mBluetoothLeService.setCharacteristicNotification(
                        breezingCharacteristic, true);
            }
            notified = true;
            return true;

        }

    return false;

    }

    public List<DataPacket> processNewData(byte[] currentByte, double[] daqData){
        Log.i("Process Length", currentByte.length + "");

        List<DataPacket> packets = new ArrayList<>();
        if(realTimeWriter == null)
            realTimeWriter=setUpRealTimeFile();

        if (currentByte.length == PACKET_LENGTH) {

            if(realTimeWriter == null)
                realTimeWriter = setUpRealTimeFile();

            for(int i = 0; i < currentByte.length; i+=8){
                DataPacket tempPacket = processData(currentByte, false, i);
                packets.add(tempPacket);
            }

            writeRealTimeToFile(packets, daqData);

        }
        return packets;
    }


    public void disconnect(){
        if(syncWriter!=null){
            syncWriter.flush();
            syncWriter.close();
        }
        if(realTimeWriter!=null){
            realTimeWriter.flush();
            realTimeWriter.close();
        }
        mBluetoothLeService.disconnect();
    }

    public void onDestroy(){
        disconnect();
        context.unbindService(mServiceConnection);
        mBluetoothLeService.stopForeground();
        mBluetoothLeService= null;
    }

    public PrintWriter setUpRealTimeFile(){
        try{
            File folder = new File(Environment.getExternalStorageDirectory() + "/ECGReader");
            if (!folder.exists()) {
                folder.mkdir();
            }
            DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy HH.mm.ss");
            Date date = new Date();
            String filename = folder + "/"+mDeviceName+"_" + dateFormat.format(date) + ".csv";

            File outputFile = new File(filename);
            PrintWriter mCurrentFile = new PrintWriter(new FileOutputStream(outputFile));


            MediaScannerConnection.scanFile(context,
                    new String[]{outputFile.toString()}, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            Log.i("ExternalStorage", "Scanned " + path + ":");
                            Log.i("ExternalStorage", "-> uri=" + uri);
                        }
                    });

            StringBuffer buff = new StringBuffer();
            buff.append("Index");
            buff.append(",");
            buff.append("Timestamp");
            buff.append(",");
            buff.append("AccelerometerX");
            buff.append(",");
            buff.append("AccelerometerY");
            buff.append(",");
            buff.append("AccelerometerZ");
            buff.append(",");
            buff.append("IR");
            buff.append(",");
            buff.append("ECG");
            buff.append(",");



            mCurrentFile.println(buff.toString());

            mCurrentFile.flush();

            return mCurrentFile;
        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void writeRealTimeToFile(List<DataPacket> packets, double[] daqData){

        if(realTimeWriter != null && packets.size() == daqData.length){

            for(int i = 0; i < packets.size(); i++){
                DataPacket dp = packets.get(i);
                double ecgPoint = daqData[i];

                StringBuffer buff = new StringBuffer();
                buff.setLength(0);
                buff.append(dp.getIndex());
                buff.append(",");
                buff.append(dp.getTimeStamp());
                buff.append(",");
                buff.append(dp.getAxisreading().getXaxis());
                buff.append(",");
                buff.append(dp.getAxisreading().getYaxis());
                buff.append(",");
                buff.append(dp.getAxisreading().getZaxis());
                buff.append(",");
                buff.append(dp.getMisc());
                buff.append(",");
                buff.append(ecgPoint);
                buff.append(",");

                realTimeWriter.println(buff.toString());
                realTimeWriter.flush();
            }
        }
    }

    public DataPacket processData(byte[] currentArray, boolean isSync, int start){
        DataPacket dp=new DataPacket();
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy HH.mm.ss");
        Date date = new Date();

        if(currentArray != null || currentArray.length > 0){
            dp.setIndex(isSync?syncIndex++:index++);
            dp.setTimeStamp(dateFormat.format(date));
            dp.setAxisreading(new Accelerometer(resolutionNoFactor(currentArray[start+0], currentArray[start+1]), resolutionNoFactor(currentArray[start+2], currentArray[start+3]), resolutionNoFactor(currentArray[start+4], currentArray[start+5])));
            dp.setMisc(resolutionNoFactor(currentArray[start+6], currentArray[start+7]));
        }

        return dp;
    }

    private double getAccelerometerXYZ(Byte xByte){
        return xByte.doubleValue();
    }
    private double resolutionNoFactor(Byte higher, Byte lower) {
        double combined = ((higher & 0xFF) << 8) + (lower & 0xFF);
        return combined;
    }
    private double resolutionFactor(Byte higher, Byte lower) {
        double combined = ((higher & 0xFF) << 8) + (lower & 0xFF);
        double factor = 4.3/4096;
        return (Math.round(combined*factor*100.0)/100.0);
    }

    private double humidityFactor(Byte higher, Byte lower) {
        double combined = ((higher & 0xFF) << 8) + (lower & 0xFF);
        double factor = 100/(Math.pow(2,16)-1);
        return (Math.round(combined*factor*100.0)/100.0);
    }

    private double temperatureFactor(Byte higher, Byte lower) {
        double combined = ((higher & 0xFF) << 8) + (lower & 0xFF);
        double factor = 175/(Math.pow(2,16)-1);
        DecimalFormat df = new DecimalFormat("###.###");
        return Double.valueOf(df.format(-45+(Math.round(combined*factor*100.0)/100.0)));
    }

    public void startServiceInForeground(){
        if(mBluetoothLeService!=null){
            mBluetoothLeService.startForeground();
        }
    }

    public void stopForeground(){
        if(mBluetoothLeService!=null){
            mBluetoothLeService.stopForeground();
        }
    }
}
