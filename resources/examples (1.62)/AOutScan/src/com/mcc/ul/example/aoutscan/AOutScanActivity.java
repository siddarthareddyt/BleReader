package com.mcc.ul.example.aoutscan;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Timer;
import java.util.TimerTask;

import com.mcc.ul.AoDevice;
import com.mcc.ul.AoScanOption;
import com.mcc.ul.AoInfo;
import com.mcc.ul.AoUnit;
import com.mcc.ul.DaqDevice;
import com.mcc.ul.DaqDeviceConnectionPermissionListener;
import com.mcc.ul.DaqDeviceDescriptor;
import com.mcc.ul.DaqDeviceInfo;
import com.mcc.ul.DaqDeviceManager;
import com.mcc.ul.ErrorInfo;
import com.mcc.ul.Range;
import com.mcc.ul.Status;
import com.mcc.ul.ULException;
import com.mcc.ul.example.aoutscan.NetDiscoveryInfoDialog;
import com.mcc.ul.example.aoutscan.NetDiscoveryInfoDialog.NoticeDialogListener;
import com.mcc.ul.example.aoutscan.R;
import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

/*
 * Library Call Demonstrated:    AoDevice.aOutScan()

   Purpose:                      Writes values to the specified D/A channel.

   Demonstration:                Sends a digital output to the D/A channel.
   
   Other Library Calls:          AoDevice.getStatus()
                                 AoDevice.StopBackground()
                                 
                                 
   Steps:
   1. Create a DaqDeviceManager object
   2. Call DaqDeviceManager.getDaqDeviceInventory() to find available DAQ devices.
   3. Call DaqDeviceManager.createDevice() to create a DaqDevice object for the desired device 
   4. Call DaqDevice.requestConnectionPermission() to request permission for connecting to the device
   5. If Permission granted call DaqDevice.connect() to connect to the device
   6. Call DaqDevice.getAoDev() to retrieve the analog output device object
   7. Write data the output buffer
   7. Call AoDevice.aOutScan() to start the scan operation
   8. Call AoDevice.getStatus() to check the status of the background operation
   9. Call AoDevice.stopBackground() when scan is completed
   
	Note: Declare the following permissions in your application manifest file (AndroidManifest.xml)
	<uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
*/

public class AOutScanActivity extends Activity {

	private DaqDeviceManager mDaqDeviceManager;
	private DaqDevice mDaqDevice;
	private AoDevice mAoDevice;
	boolean mUpdateStatus;
	NetDiscoveryInfoDialog mDiscoveryInfoDlg;
	
	// UI objects
	Button mDetectButton;
	Button mConnectButton;
	Button mDisconnectButton;
	Spinner mDaqDevSpinner;
	Spinner mChanSpinner;
	Spinner mRangeSpinner;
	Spinner mOutputTypeSpinner;
	ToggleButton mStartButton;
	EditText mRateEditText;
	CheckBox mContinuousCheckBox;
	
	ArrayAdapter<DaqDeviceDescriptor> mDaqDevInventoryAdapter;
	ArrayAdapter<Integer> mChanAdapter;
	ArrayAdapter<Range> mRangeAdapter;
	ArrayAdapter<Waveform> mOutputTypeAdapter;
	TextView mStatusTextView;
	
	double[][] mScanData;
	private Timer mScanStatusTimer;
	private boolean mScanStopped;
	
	final static int TIMER_PERIOD = 500; //ms
	final static int GREEN = Color.parseColor("#165B12");
	final static double DEFAULT_RATE = 10000.0;
	final static int DEFAULT_SAMPLE_COUNT = 10000;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initActivity();
        
        mDaqDevice = null;
        mAoDevice = null;
        mDaqDeviceManager = new DaqDeviceManager(this);
        
        mScanStatusTimer = null;
        mScanData = null;
        
        mDiscoveryInfoDlg = new NetDiscoveryInfoDialog();
        mDiscoveryInfoDlg.setNoticeDialogListener(new DiscoveryInfoEvents());
    }    
    
    private OnClickListener mClickListener = new  OnClickListener() {
    	public void onClick(View v) {
    		switch(v.getId()) {
    		case R.id.button_detect:
    			detectDaqDevices();
    		break;
    		case R.id.button_connect:
    			connectToDaqDevice();			
    		break;
    		case R.id.button_disconnect:
    			disconnectDaqDevice();
    			updateStatus("Disconnected from " + mDaqDevice, false);
    			break;
    		case R.id.toggleButton_start:
    			if(mStartButton.isChecked())
    				startAOutScan();
    			else {
    				mScanStopped = true;
    				stopAOutScan();
    			}
    				
    			break;
    		}
    	}
    };
    
    private void detectDaqDevices() {
    	
    	mDaqDevInventoryAdapter.clear();
    	
    	// Find available DAQ devices
    	ArrayList<DaqDeviceDescriptor> daqDevInventory = mDaqDeviceManager.getDaqDeviceInventory();
		 
    	// Add detected DAQ devices to spinner
    	mDaqDevInventoryAdapter.addAll(daqDevInventory);
		
    	
		if(daqDevInventory.size() > 0)
			updateStatus(daqDevInventory.size() + " DAQ device(s) detected", false);
		else
			updateStatus("No DAQ devices detected", false);
		
		updateActivity();
		
		mRateEditText.setFocusableInTouchMode(true);
    }
    
    public class OnDaqDeviceSelectedListener implements OnItemSelectedListener {    
    	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {      
    		if(mDaqDevice != null){
    			mDaqDeviceManager.releaseDaqDevice(mDaqDevice);
    			
    			if(mUpdateStatus)
    				updateStatus("", false);
    			else
    				mUpdateStatus = true;
    		}
    		
    		// Create a DaqDevice object for the selected device
    		mDaqDevice = mDaqDeviceManager.createDaqDevice(mDaqDevInventoryAdapter.getItem(pos));
    		
    		DaqDeviceInfo devInfo = mDaqDevice.getInfo();
    		
    		// Check if this DAQ Device has an analog output device (subsystem)
        	if(devInfo.hasAoDev()) {
        		
        		mAoDevice = mDaqDevice.getAoDev();
        		AoInfo aoInfo = mAoDevice.getInfo();
        		
        		if(aoInfo.hasPacer()) {
	        		mChanAdapter.clear();
	        		
	        		// Get number of analog output channels
	        		int numChannels = aoInfo.getTotalNumChans();
	        		
	        		for(int chan = 0; chan < numChannels; chan++ )
	        			mChanAdapter.add(chan);
	        		
	        		mRangeAdapter.clear();
	        		
	        		// Get supported ranges
	        		EnumSet<Range> ranges = aoInfo.getRanges(); 
	        		
	        		mRangeAdapter.addAll(ranges);
	        		
	        		// Get the maximum supported scan rate
	        		double maxRate = aoInfo.getMaxScanRate();
	
	        		double rate = Double.parseDouble(mRateEditText.getText().toString());
	
	        		if(rate > maxRate)
	        			mRateEditText.setText(String.valueOf(maxRate));
        		}
        		else
        			updateStatus("Selected device does not support analog output scan", true);
        	}
        	else {
        		updateStatus("Selected device does not support analog output", true);
        	}
    	
    	}    
    	public void onNothingSelected(AdapterView<?> parent){// Do nothing.
    	}
    }
    
    void connectToDaqDevice() {
    	updateStatus("Connecting to " + mDaqDevice, false);
		
		// Check if this device has connection permission
		if(mDaqDevice.hasConnectionPermission()) {
			// This device already has connection permission. try to connect to it 
			mDeviceConnectionPermissionListener.onDaqDevicePermission(mDaqDevice.getDescriptor(), true);
		}
		else {
			//Request permission for connecting to the selected device
			try {
				mDaqDevice.requestConnectionPermission(mDeviceConnectionPermissionListener);
			} catch (ULException e) {
				updateStatus(e.getMessage(), true);
			}
		}
    }
    
    public DaqDeviceConnectionPermissionListener mDeviceConnectionPermissionListener = new DaqDeviceConnectionPermissionListener() {  
	    public void onDaqDevicePermission(DaqDeviceDescriptor daqDeviceDescriptor, boolean permissionGranted) {
	    	if(permissionGranted)
	    	{
	    		try {
	    			//Establish connection to the DAQ device
					mDaqDevice.connect();
						
					runOnUiThread(new Runnable() {
						public void run() {
							mDetectButton.setEnabled(false);
					    	mDaqDevSpinner.setEnabled(false);
					    	mConnectButton.setEnabled(false);
					    	mDisconnectButton.setEnabled(true);
					    	
					    	try {
						    	if(mDaqDevice.getInfo().hasAoDev())
						    		mStartButton.setEnabled(true);
						    	
						    	updateStatus("Connected to " + mDaqDevice, false);
						    	// Disable screen rotation while a DAQ device is connected 
						    	lockScreenOrientation();
					    	
					    	} catch(NullPointerException e) {
					    		updateStatus("DaqDevice object no longer valid." + mDaqDevice, true);
					    	}
					    }
					});	
					
				} catch (Exception e) {
					updateStatus("Unable to connect to " + mDaqDevice + ". " + e.getMessage(), true);
				}
	    	}
	    	else {
				updateStatus("Permission denied to connect to " + mDaqDevice, true);
	    	}
	    }
    };
    
    void startAOutScan()
    {
    	mScanData = null; //let garbage collector reclaim the existing data memory 
    	int chan = (Integer) mChanSpinner.getSelectedItem();
    	Range range = (Range) mRangeSpinner.getSelectedItem();
    	int samplesPerChan = DEFAULT_SAMPLE_COUNT;
    	double rate = Double.parseDouble(mRateEditText.getText().toString());	
    	Waveform waveform = (Waveform) mOutputTypeSpinner.getSelectedItem();
    	mScanData = new double[1][samplesPerChan];
    	fillOutputData(mScanData, waveform, range);
    	EnumSet<AoScanOption> options = EnumSet.of(AoScanOption.DEFAULTIO);
    	
    	if(mContinuousCheckBox.isChecked())
    		options.add(AoScanOption.CONTINUOUS);
    	
    	mScanStopped = false;
    	
    	try {
			@SuppressWarnings("unused")
			
			//Generate output by calling the aOutScan function
			double actualScanRate = mAoDevice.aOutScan(chan, chan, range, samplesPerChan, rate, options, AoUnit.VOLTS, mScanData);
		
			startScanStatusTimer();
				
    	} catch (final ULException e) {
			updateStatus(e.getMessage(), true);
			mStartButton.setChecked(false);
		}
    			
    }
    
    private void scanStatusTimer(){
    	try {	
    		synchronized(this) {
    	    	if(mAoDevice != null) {
		    		// Check if the background operation has finished. If it has, then the background operation must be explicitly stopped	
					Status scanStatus = mAoDevice.getStatus();
				
					if(scanStatus.currentStatus != Status.RUNNING) {
						// always call stopBackground upon completion...
						stopAOutScan();
						
						stopScanStatusTimer();
						
						if(scanStatus.errorInfo == ErrorInfo.DEADDEV) {
							disconnectDaqDevice();
						}
					}
					
					displayCurrentStatus(scanStatus);
    	    	}
    		}
			
		} catch (final ULException e) {
			stopScanStatusTimer();
			
			updateStatus(e.getMessage(), true);
			
			e.printStackTrace();
		}
    }
    
    void stopAOutScan() {
    	
    	try {
    		if(mAoDevice != null)
    			mAoDevice.stopBackground();
		} catch (ULException e) {
			e.printStackTrace();
		}
    	
    }
 
 	void displayCurrentStatus(final Status scanStatus) {
		if(scanStatus.currentStatus == Status.IDLE) {
			if(scanStatus.errorInfo != ErrorInfo.NOERROR)
				updateStatus(scanStatus.errorInfo.toString(), true);	
			else if(mScanStopped)
				updateStatus("Scan stopped", false);
			else
				updateStatus("Scan completed", false);	
			runOnUiThread(new Runnable() {
				public void run() {
					mStartButton.setChecked(false);
				}
	 		});
		}
		else
			updateStatus("Scan is running. Number of samples transfered : " + scanStatus.currentCount, false);		
 	}
    
    void fillOutputData(double[][] data, Waveform waveForm, Range range)
    {
    	int numSamples = data[0].length;
    	
    	double amp = ((range.getMaxValue() + Math.abs(range.getMinValue())) / 2) * 0.9 ;	// amplitude scale
    	double offset = (range.getMaxValue() + range.getMinValue()) / 2;
    	
    	switch(waveForm) {
    	case SINEWAVE:
	    	for (int i = 0; i < numSamples; i++)
	    		data[0][i] = amp * Math.sin(i * Math.PI * 2.0 / (numSamples - 1.0)) + offset;
	    	break;
	    	
    	case SQUAREWAVE:
    		for (int i = 0; i < numSamples; i++) {
    			if(i % 2 == 0)
    				data[0][i] = amp + offset;
    			else
    				data[0][i] = offset - amp;
    		}	
    		break;
    		
    	case TRIANGLE:
    		for (int i = 0; i < numSamples; i++)
    			if(i < (numSamples / 2))
    				data[0][i] = (offset - amp) + (4.0 * i / numSamples ) * (amp);
    			else
    				data[0][i] = (offset + amp) - (4.0 * i / numSamples - 2.0) * (amp);
    		break;
    		
    	case SAWTOOTH:
    		for (int i = 0; i < numSamples; i++)
    				data[0][i] = (offset - amp) + (2.0 * i / numSamples ) * (amp);
    		break;
    	}
    }
    
    public class OnOutputTypeSelectedListener implements OnItemSelectedListener {    
    	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {      
    		
	    	if(mScanData != null) {
	    		Range range = (Range) mRangeSpinner.getSelectedItem();
	        	Waveform waveform = (Waveform) mOutputTypeSpinner.getSelectedItem();
	        	fillOutputData(mScanData, waveform, range);
    		}    
    	}
    	public void onNothingSelected(AdapterView<?> parent){// Do nothing.
    	}
    }

    
    private void disconnectDaqDevice() {
    	
    	mDaqDevice.disconnect();
		
    	runOnUiThread(new Runnable() {
			public void run() {
				mDetectButton.setEnabled(true);
		    	mDaqDevSpinner.setEnabled(true);
		    	mConnectButton.setEnabled(true);
		    	mDisconnectButton.setEnabled(false);
		    	mStartButton.setEnabled(false);
	    	
		    	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
			}
    	});
    }
    
    
    private void initActivity() {
    	mDetectButton = (Button)findViewById(R.id.button_detect);
    	mConnectButton = (Button)findViewById(R.id.button_connect);
    	mDisconnectButton = (Button)findViewById(R.id.button_disconnect);
    	
    	mDetectButton.setOnClickListener(mClickListener);
    	mDetectButton.setOnLongClickListener((OnLongClickListener) mLongClickListener);
    	
    	mConnectButton.setOnClickListener(mClickListener);
    	mConnectButton.setEnabled(false);
    	
    	mDisconnectButton.setOnClickListener(mClickListener);
    	mDisconnectButton.setEnabled(false);
    	
    	mDaqDevSpinner = (Spinner) findViewById(R.id.spinner_daqDev);
    	mDaqDevInventoryAdapter =  new ArrayAdapter <DaqDeviceDescriptor> (this, android.R.layout.simple_spinner_item );
    	mDaqDevInventoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mDaqDevSpinner.setAdapter(mDaqDevInventoryAdapter);
    	mDaqDevSpinner.setOnItemSelectedListener(new OnDaqDeviceSelectedListener());
    	mDaqDevSpinner.setEnabled(false);
    	
    	mChanSpinner = (Spinner) findViewById(R.id.spinner_chan);
    	mChanAdapter =  new ArrayAdapter <Integer> (this, android.R.layout.simple_spinner_item );
    	mChanAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mChanSpinner.setAdapter(mChanAdapter);
    	mChanSpinner.setEnabled(false);
    	
    	mRangeSpinner = (Spinner) findViewById(R.id.spinner_range);
    	mRangeAdapter =  new ArrayAdapter <Range> (this, android.R.layout.simple_spinner_item );
    	mRangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mRangeSpinner.setAdapter(mRangeAdapter);
    	mRangeSpinner.setEnabled(false);
    	
    	mOutputTypeSpinner = (Spinner) findViewById(R.id.spinner_outputType);
    	mOutputTypeAdapter =  new ArrayAdapter <Waveform> (this, android.R.layout.simple_spinner_item );
    	mOutputTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mOutputTypeSpinner.setAdapter(mOutputTypeAdapter);
    	mOutputTypeSpinner.setOnItemSelectedListener(new OnOutputTypeSelectedListener());
    	
    	mOutputTypeAdapter.addAll(EnumSet.allOf(Waveform.class));
    	
    	mRateEditText =  (EditText)findViewById(R.id.editText_rate);
    	mRateEditText.setText(String.valueOf(DEFAULT_RATE));
    	mRateEditText.setEnabled(false);
    	mRateEditText.setFocusableInTouchMode(false);
    	
    	mContinuousCheckBox = (CheckBox) findViewById(R.id.checkBox_continuous);
    	mContinuousCheckBox.setEnabled(false);
		
    	
    	mStatusTextView = (TextView) findViewById(R.id.textView_errInfo);
    	
    	mStartButton = (ToggleButton) findViewById(R.id.toggleButton_start);
    	mStartButton.setOnClickListener(mClickListener);
    	mStartButton.setEnabled(false);
    	
    	updateStatus("Tap detect button. (If you need to detect a network DAQ device manually, press and hold detect button)", false);
    }
    
    private void startScanStatusTimer() {	
    	updateStatus("Scan is running", false);
    	
    	mScanStatusTimer = new Timer();
		mScanStatusTimer.schedule(new TimerTask() {          
	        @Override
	        public void run() {
	        	scanStatusTimer();
	        }

	    }, 0, TIMER_PERIOD);
    }
    
   
    private void lockScreenOrientation()  {
	 
	    int orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
	    Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
	    int rotation = display.getRotation();
	    Configuration cfg = getResources().getConfiguration();
        
	    switch (rotation) {
	    case Surface.ROTATION_0:
	    	if(cfg.orientation == Configuration.ORIENTATION_PORTRAIT)
	    		orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
	    	else if(cfg.orientation == Configuration.ORIENTATION_LANDSCAPE)
	    		orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
	        break;
	    case Surface.ROTATION_90:
	    	if(cfg.orientation == Configuration.ORIENTATION_LANDSCAPE)
	    		orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
	    	else if(cfg.orientation == Configuration.ORIENTATION_PORTRAIT)
	    		orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
	        break;
	     default:
	    case Surface.ROTATION_180:
	    	if(cfg.orientation == Configuration.ORIENTATION_PORTRAIT)
	    		orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
	    	else if(cfg.orientation == Configuration.ORIENTATION_LANDSCAPE)
	    		 orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;	
	        break;
	    case Surface.ROTATION_270:
	    	if(cfg.orientation == Configuration.ORIENTATION_LANDSCAPE)
	    		 orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
	    	else if(cfg.orientation == Configuration.ORIENTATION_PORTRAIT)
	    		orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;	
	        break;
	    }
	
	    setRequestedOrientation(orientation);	 
    }
    
    void updateStatus(final String message, final boolean error) {
    	runOnUiThread(new Runnable() {
			public void run() {
				int textColor = GREEN;
		    	if(error)
		    		textColor = Color.RED;
		    	
		    	mStatusTextView.setTextColor(textColor);
				mStatusTextView.setText(message);	
			}
    	});
    }
    
    private void stopScanStatusTimer() {
    	if(mScanStatusTimer != null)
    		mScanStatusTimer.cancel();
    }
    
    private void updateActivity() {
    	
    	if(!mDaqDevInventoryAdapter.isEmpty()) {
    		mDaqDevSpinner.setEnabled(true);
    		mConnectButton.setEnabled(true);
        	mChanSpinner.setEnabled(true);
        	mRangeSpinner.setEnabled(true);
        	mRateEditText.setEnabled(true);
        	mContinuousCheckBox.setEnabled(true);
        	
        	mUpdateStatus = false;
        	
        	if(mDaqDevSpinner.getSelectedItemPosition() != 0)
        		mDaqDevSpinner.setSelection(0);
        	else
        		mDaqDevSpinner.getOnItemSelectedListener().onItemSelected(null, null, 0, 0);
    	}
    	else {
			mConnectButton.setEnabled(false);
			mDisconnectButton.setEnabled(false);
			mDaqDevSpinner.setEnabled(false);
		}
    }
    
    private void detectNetDaqDeviceManually(String host, int port) {
    	
    	mDaqDevInventoryAdapter.clear();
    	
    	DaqDeviceDescriptor netDevDescriptor = null;
    	
    	int discoveryTimeout = 10000; // ms
    		 
		try {
			netDevDescriptor = mDaqDeviceManager.getNetDaqDeviceDescriptor(null, host, port, discoveryTimeout);
		
    		if(netDevDescriptor != null) {
    			mDaqDevInventoryAdapter.add(netDevDescriptor);
    			updateStatus(netDevDescriptor.productName + " device detected", false);
    		}
    		else
    			updateStatus("No network DAQ devices detected", false);
			
			updateActivity();
    		
		} catch (ULException e) {
			updateStatus(e.getMessage(), true);
		}	  	 		
    }
    
    class DiscoveryInfoEvents implements NoticeDialogListener{

		@Override
		public void onDialogPositiveClick(DialogFragment dialog) {
			
			String host = mDiscoveryInfoDlg.getHostAddress();
			int port = mDiscoveryInfoDlg.getPort();
			detectNetDaqDeviceManually(host, port);	
		}

		@Override
		public void onDialogNegativeClick(DialogFragment dialog) {
		}
    	
    }
    
    private OnLongClickListener mLongClickListener = new OnLongClickListener() {

		@Override
		public boolean onLongClick(View v) {
			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(v.getContext());

			alertDialogBuilder.setMessage("Would you like to detect a network DAQ device manually?");
			alertDialogBuilder.setCancelable(false);
			alertDialogBuilder.setPositiveButton("Yes",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {						
					mDiscoveryInfoDlg.show(getFragmentManager(), "InfoTag");
				}
			});
			
			alertDialogBuilder.setNegativeButton("No",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					dialog.cancel();
				}
			});
			 
			AlertDialog alertDialog = alertDialogBuilder.create();
			alertDialog.show();
			
			return true;
		}        
    };
    
    @Override
    protected void onDestroy() {
    	stopScanStatusTimer();
    	
    	synchronized(this) {
	    	if(mDaqDevice != null)
				mDaqDeviceManager.releaseDaqDevice(mDaqDevice);
	    	
	    	mDaqDevice = null;
	    	mAoDevice = null;
    	}
    	
        super.onDestroy();
    } 
}
