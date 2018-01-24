package com.app.asthma;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Created by siddartha on 11/6/16.
 */


public class RealTimeFragment extends Fragment{
    Button disconnect;
    TextView deviceName;
    TextView heaterValue;
    TextView preheating;
    TextView ozoneHighValue;
    TextView ozoneLowValue;
    TextView temperatureValue;
    TextView humidityValue;
    //TextView activityValue;
    TextView batteryValue;
    TextView accelerometer;
    Button sync;
    RelativeLayout dataView;
    RelativeLayout noDataView;
    RelativeLayout syncStatuView;

    public boolean syncing = false;
    public enum DisplayLayout{
        DATA,
        NO_DATA,
        SYNC
    }




    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View inflatedView = inflater.inflate(R.layout.realtime_fragment, container, false);

        dataView = (RelativeLayout) inflatedView.findViewById(R.id.dataView);
        noDataView = (RelativeLayout)inflatedView.findViewById(R.id.noDataView);
        syncStatuView = (RelativeLayout)inflatedView.findViewById(R.id.syncStatusView);
        syncStatuView.setVisibility(View.INVISIBLE);

        MainActivity mainActivity = (MainActivity) getActivity();
        if(mainActivity.getMConnected()){
            noDataView.setVisibility(View.INVISIBLE);
        }else{
            dataView.setVisibility(View.INVISIBLE);
        }

        disconnect = (Button)inflatedView.findViewById(R.id.disconnect);
        deviceName = (TextView)inflatedView.findViewById(R.id.deviceName);
        heaterValue = (TextView)inflatedView.findViewById(R.id.heaterValue);
        preheating = (TextView)inflatedView.findViewById(R.id.preheating);
        ozoneHighValue = (TextView)inflatedView.findViewById(R.id.ozoneHighValue);
        ozoneLowValue = (TextView)inflatedView.findViewById(R.id.ozoneLowValue);
        temperatureValue = (TextView)inflatedView.findViewById(R.id.temperatureValue);
        humidityValue = (TextView)inflatedView.findViewById(R.id.humidityValue);
        //activityValue = (TextView)inflatedView.findViewById(R.id.activityValue);
        batteryValue = (TextView) inflatedView.findViewById(R.id.batteryValue);
        accelerometer = (TextView)inflatedView.findViewById(R.id.accelrometerValue);
        sync = (Button)inflatedView.findViewById(R.id.sync);

        disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //disconnect from ble
                MainActivity mainActivity = (MainActivity) getActivity();
                mainActivity.disconnect();

            }
        });

        sync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //sync on sync
                MainActivity mainActivity = (MainActivity) getActivity();
                mainActivity.syncData(true);
            }
        });

        return inflatedView;
    }



    @Override
    public void onResume(){
        super.onResume();

        MainActivity mainActivity = (MainActivity) getActivity();
        if(mainActivity.getMConnected()){

        }
    }

    public void displayRealTimeData(DataPacket dataPacket, String device){
            deviceName.setText(device);

            int heat = ((int) dataPacket.getMisc());
            ozoneHighValue.setText(String.valueOf(dataPacket.getAxisreading().getXaxis()));
            ozoneLowValue.setText(String.valueOf(dataPacket.getAxisreading().getXaxis()));
            temperatureValue.setText(String.valueOf(dataPacket.getAxisreading().getXaxis())+" C");
            humidityValue.setText(String.valueOf(dataPacket.getAxisreading().getXaxis())+" %");
            //activityValue.setText(String.valueOf(dataPacket.getIndex())+" g");
            batteryValue.setText(String.valueOf(dataPacket.getAxisreading().getXaxis())+" V");
            accelerometer.setText(dataPacket.getAxisreading().getXaxis()+" g");

            switch (heat){
                case 1:
                    preheating.setText("Preheating");
                    heaterValue.setText("");
                    break;
                case 2:
                    preheating.setText("Preheating Complete");
                    heaterValue.setText("Off");
                    break;
                case 3:
                    preheating.setText("Preheating Complete");
                    heaterValue.setText("On");
                    break;

            }

    }

    public void setDisplay(DisplayLayout display){
        if(display == DisplayLayout.DATA){
            syncing = false;
            dataView.setVisibility(View.VISIBLE);
            syncStatuView.setVisibility(View.INVISIBLE);
            noDataView.setVisibility(View.INVISIBLE);
        }else if(display == DisplayLayout.SYNC){
            syncing = true;
            syncStatuView.setVisibility(View.VISIBLE);
            dataView.setVisibility(View.INVISIBLE);
            noDataView.setVisibility(View.INVISIBLE);
        }else{
            syncing = false;
            syncStatuView.setVisibility(View.INVISIBLE);
            dataView.setVisibility(View.INVISIBLE);
            noDataView.setVisibility(View.VISIBLE);
        }
    }


}
