package com.app.asthma;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.util.Log;

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

/**
 * Created by siddartha on 1/17/18.
 */

public class DaqReader {

    Context context;

    public final static int ONE_PACKET_SIZE = 10;

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

    public static DaqReader instance = null;


    private DaqReader(Context context, int userTimer){
        this.context = context;
        this.userTimer = userTimer;

        sampleRate = sampleRate * userTimer;

        mDaqDevice = null;
        mAiDevice = null;
        mDaqDeviceManager = new DaqDeviceManager(context);

        mAInTimer = null;
    }


    public static DaqReader getInstance(Context context, int userTimer){
        if(instance == null){
            synchronized (DaqReader.class) {
                if(instance == null){
                    instance = new DaqReader(context, userTimer);
                }
            }
        }
        return instance;
    }

    public void startDAQ(){
        detectAndSelectDaqDevice();
        connectToDaqDevice();
        startAIn();
    }

    private void detectAndSelectDaqDevice() {

        daqDevInventory = mDaqDeviceManager.getDaqDeviceInventory();

        if(daqDevInventory.size() <= 0)
            return;

        mDaqDevice = mDaqDeviceManager.createDaqDevice(daqDevInventory.get(0));
        DaqDeviceInfo devInfo = mDaqDevice.getInfo();

        if(devInfo.hasAiDev()) {

            mAiDevice =  mDaqDevice.getAiDev();
            AiInfo aiInfo = mAiDevice.getInfo();

            // Get the maximum supported scan rate
            double maxRate = aiInfo.getMaxScanRate();

            if(maxRate > 0.0) {
                if(sampleRate > maxRate)
                    sampleRate = maxRate;
            }

        }else {
            return;
        }
    }

    private void connectToDaqDevice() {
        if(mDaqDevice != null && mDaqDevice.hasConnectionPermission()) {
            mDeviceConnectionPermissionListener.onDaqDevicePermission(mDaqDevice.getDescriptor(), true);
        }
        else {
            try {
                mDaqDevice.requestConnectionPermission(mDeviceConnectionPermissionListener);
            } catch (ULException e) {
                return;
            }
        }
    }

    private void disconnectDaqDevice() {
        mDaqDevice.disconnect();

        stopAInTimer();
    }

    private void startAIn() {
        mAInData = 0; //let garbage collector reclaim the existing data memory

        mSamplesRead = 0;

        if(lowChan > highChan) {
            stopAInTimer();
            return;
        }

        startAInTimer();

    }


    private void startAInTimer() {

        long timerPeriod = mTimerPeriod;

        if(timerPeriod >= MIN_TIMER_PERIOD) {
            mAInTimer = new Timer();
            mAInTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    aInTimer();
                }

            }, 0, timerPeriod);
        }
    }



    private void stopAInTimer() {
        if(mAInTimer != null)
            mAInTimer.cancel();
    }

    private void aInTimer(){
        if(mSamplesRead >= sampleRate)
            return;

        mAInData = 0;

        try {
            synchronized(this) {
                if(mAiDevice != null) {
                    mAInData = mAiDevice.aIn(0, chanMode, range, units);
                    mSamplesRead++;
                    bufferPoint(mAInData);
                }
            }

        } catch (final ULException e) {
            stopAInTimer();

            if(e.getErrorInfo() == ErrorInfo.DEADDEV)
                disconnectDaqDevice();
            e.printStackTrace();
        }
    }

    private void bufferPoint(double point){
        Log.i("Buffering point: ", point+" ");
        if(bufferIndex < ONE_PACKET_SIZE){
            buffer[bufferIndex++] = point;
        }
    }

    public double[] readBuffer(){
        double[] buff = buffer.clone();
        buffer = new double[ONE_PACKET_SIZE];
        bufferIndex = 0;

        Log.i("Reading buffer: ", buff[0]+", "+buff[ONE_PACKET_SIZE-1]);

        return buff;
    }

    public void destroy(){
        if(mDaqDevice != null) {
            mDaqDeviceManager.releaseDaqDevice(mDaqDevice);
        }

        mDaqDevice = null;
    }



    public DaqDeviceConnectionPermissionListener mDeviceConnectionPermissionListener = new DaqDeviceConnectionPermissionListener() {
        public void onDaqDevicePermission(DaqDeviceDescriptor daqDeviceDescriptor, boolean permissionGranted) {
            if(permissionGranted)
            {
                try {
                    mDaqDevice.connect();

                } catch (Exception e) {
                    Log.i("Unable to connect to ", mDaqDevice.toString());
                }
            }
            else {
                Log.i("Permission denied", mDaqDevice.toString());
            }
        }
    };
}
