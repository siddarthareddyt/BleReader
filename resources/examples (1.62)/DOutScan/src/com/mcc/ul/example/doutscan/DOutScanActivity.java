package com.mcc.ul.example.doutscan;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Timer;
import java.util.TimerTask;

import com.mcc.ul.DaqDevice;
import com.mcc.ul.DaqDeviceConnectionPermissionListener;
import com.mcc.ul.DaqDeviceDescriptor;
import com.mcc.ul.DaqDeviceInfo;
import com.mcc.ul.DaqDeviceManager;
import com.mcc.ul.DigitalDirection;
import com.mcc.ul.DigitalPortIOType;
import com.mcc.ul.DigitalPortType;
import com.mcc.ul.DioDevice;
import com.mcc.ul.DioInfo;
import com.mcc.ul.DioScanOption;
import com.mcc.ul.ErrorInfo;
import com.mcc.ul.PortInfo;
import com.mcc.ul.Range;
import com.mcc.ul.ScanDirection;
import com.mcc.ul.Status;
import com.mcc.ul.TriggerSourceType;
import com.mcc.ul.TriggerType;
import com.mcc.ul.ULException;
import com.mcc.ul.example.doutscan.R;
import com.mcc.ul.example.doutscan.NetDiscoveryInfoDialog;
import com.mcc.ul.example.doutscan.NetDiscoveryInfoDialog.NoticeDialogListener;

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
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

/*
 * Library Call Demonstrated:    DioDevice.dOutScan()

   Purpose:                      Writes values to the specified port.

   Demonstration:                Sends a digital output to the digital port.
   
   Other Library Calls:          DioDevice.getStatus()
                                 DioDevice.StopBackground()
                                 
                                 
   Steps:
   1. Create a DaqDeviceManager object
   2. Call DaqDeviceManager.getDaqDeviceInventory() to find available DAQ devices.
   3. Call DaqDeviceManager.createDevice() to create a DaqDevice object for the desired device 
   4. Call DaqDevice.requestConnectionPermission() to request permission for connecting to the device
   5. If Permission granted call DaqDevice.connect() to connect to the device
   6. Call DaqDevice.getDioDev() to retrieve the digital device object
   7. Write data the output buffer
   7. Call DioDevice.dOutScan() to start the scan operation
   8. Call DioDevice.getStatus() to check the status of the background operation
   9. Call DioDevice.stopBackground() when scan is completed
   
	Note: Declare the following permissions in your application manifest file (AndroidManifest.xml)
	<uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
*/

public class DOutScanActivity extends Activity {

	private DaqDeviceManager mDaqDeviceManager;
	private DaqDevice mDaqDevice;
	private DioDevice mDioDevice;
	boolean mUpdateStatus;
	NetDiscoveryInfoDialog mDiscoveryInfoDlg;
	
	// UI objects
	Button mDetectButton;
	Button mConnectButton;
	Button mDisconnectButton;
	Spinner mDaqDevSpinner;
	Spinner mPortSpinner;
	Spinner mOutputTypeSpinner;
	ToggleButton mStartButton;
	EditText mRateEditText;
	CheckBox mContinuousCheckBox;
	CheckBox mExtClockCheckBox;
	CheckBox mExtTrigCheckBox;
	
	Spinner mTrigTypeSpinner;
	
	ArrayAdapter<DaqDeviceDescriptor> mDaqDevInventoryAdapter;
	ArrayAdapter<PortInfo> mPortAdapter;
	ArrayAdapter<Range> mRangeAdapter;
	ArrayAdapter<Waveform> mOutputTypeAdapter;
	ArrayAdapter<TriggerType> mTrigTypeAdapter;
	TextView mStatusTextView;
	
	long[][] mScanData;
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
        mDioDevice = null;
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
    				startDOutScan();
    			else {
    				mScanStopped = true;
    				stopDOutScan();
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
    		
    		mConnectButton.setEnabled(false);
    		
    		// Create a DaqDevice object for the selected device
    		mDaqDevice = mDaqDeviceManager.createDaqDevice(mDaqDevInventoryAdapter.getItem(pos));
    		
    		DaqDeviceInfo devInfo = mDaqDevice.getInfo();
    		
        	// Check if this DAQ Device supports digital output
        	if(devInfo.hasDioDev()) {
        		
        		mDioDevice = mDaqDevice.getDioDev();
        		DioInfo dioInfo = mDioDevice.getInfo();
        		
        		mPortAdapter.clear();
        		
        		if(dioInfo.hasPacer()) {
        			// Get number of ports
	        		int numOfPorts = dioInfo.getNumPorts();
	        		
	        		PortInfo portInfo;
	        		for(int portNum = 0; portNum < numOfPorts; portNum++) {
	        			portInfo = dioInfo.getPortInfo(portNum);
	        			mPortAdapter.add(portInfo);
	        		}
	        		
	        		// Get the maximum supported scan rate
	        		double maxRate = dioInfo.getMaxScanRate(ScanDirection.OUTPUT);
	        		
	        		double rate = Double.parseDouble(mRateEditText.getText().toString());
	
	        		if(rate > maxRate)
	        			mRateEditText.setText(String.valueOf(maxRate));
	        		
	        		EnumSet<TriggerType> triggerTypes = dioInfo.getTriggerTypes(ScanDirection.OUTPUT);
	        		
	        		mTrigTypeAdapter.clear();
	        		
	        		for(TriggerType triggerType: triggerTypes) {
	        			
	        			if(triggerType.getSourceType() == TriggerSourceType.DIGITAL)
	        				mTrigTypeAdapter.add(triggerType);
	        		}
	        		
	        		if(mTrigTypeAdapter.isEmpty()) {
	        			mTrigTypeSpinner.setEnabled(false);
	        			mExtTrigCheckBox.setEnabled(false);
	        			mExtTrigCheckBox.setChecked(false);	
	        		}
	        		else {
	        			mExtTrigCheckBox.setEnabled(true);
	        			mTrigTypeSpinner.setEnabled(mExtTrigCheckBox.isChecked());
	        		}
	        		
	        		mConnectButton.setEnabled(true);
        		}
        		else
        			updateStatus("Selected device does not support digital output scanning", true);
        	}
        	else
        		updateStatus("Selected device does not support digital output", true);
    	
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
						    	if(mDaqDevice.getInfo().hasDioDev())
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
    
    void startDOutScan()
    {
    	mScanData = null; //let garbage collector reclaim the existing data memory
    	PortInfo portInfo = (PortInfo) mPortSpinner.getSelectedItem();
    	DigitalPortType lowPort = portInfo.getType();
    	DigitalPortType highPort = portInfo.getType();
    	TriggerType triggerType = null;
    
    	int samplesPerPort = DEFAULT_SAMPLE_COUNT;
    	double rate = Double.parseDouble(mRateEditText.getText().toString());	
    	Waveform waveform = (Waveform) mOutputTypeSpinner.getSelectedItem();
    	mScanData = new long[1][samplesPerPort];
    	fillOutputData(mScanData, waveform);
    	EnumSet<DioScanOption> options = EnumSet.of(DioScanOption.DEFAULTIO);
    	
    	if(mContinuousCheckBox.isChecked())
    		options.add(DioScanOption.CONTINUOUS);
    	if(mExtClockCheckBox.isChecked())
    		options.add(DioScanOption.EXTCLOCK);
    	if(mExtTrigCheckBox.isChecked()) {
    		options.add(DioScanOption.EXTTRIGGER);
    		
    		triggerType = (TriggerType) mTrigTypeSpinner.getSelectedItem();
    	}
    	
    	mScanStopped = false;
    	
    	configureSelectedPort();
    	
    	try {
    		
    		// setup trigger
    		if(mExtTrigCheckBox.isChecked())
    			mDioDevice.setTrigger(triggerType, null, null, null, 0);
    		
			@SuppressWarnings("unused")
			
			//Generate output by calling the dOutScan function
			double actualScanRate = mDioDevice.dOutScan(lowPort, highPort, samplesPerPort, rate, options, mScanData);
		
			startScanStatusTimer();
				
    	} catch (final ULException e) {
			updateStatus(e.getMessage(), true);
			mStartButton.setChecked(false);
		}
    			
    }
    
    private void configureSelectedPort() {
    	PortInfo portInfo = (PortInfo) mPortSpinner.getSelectedItem();
    	
    	if(portInfo.getPortIOType() == DigitalPortIOType.IO || portInfo.getPortIOType() == DigitalPortIOType.BITIO) {
    		
    		try {
        		// Configure the specified port
    			mDioDevice.dConfigPort(portInfo.getType(), DigitalDirection.OUTPUT);
    		}
    		catch (final ULException e) {
    			runOnUiThread(new Runnable() {
    				public void run() {
    					updateStatus(e.getMessage(), true);
    				}
    			});
    			e.printStackTrace();
    		}	
    	}		
    }
    
    private void scanStatusTimer(){
    	try {	
    		synchronized(this) {
    	    	if(mDioDevice != null) {
		    		// Check if the background operation has finished. If it has, then the background operation must be explicitly stopped	
					Status scanStatus = mDioDevice.getStatus(ScanDirection.OUTPUT);
				
					if(scanStatus.currentStatus != Status.RUNNING) {
						// always call stopBackground upon completion...
						stopDOutScan();
						
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
    
    void stopDOutScan() {
    	
    	try {
    		if(mDioDevice != null)
    			mDioDevice.stopBackground(ScanDirection.OUTPUT);
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
    
    void fillOutputData(long[][] data, Waveform waveForm)
    {
    	int numSamples = data[0].length;
    	
    	PortInfo portInfo = (PortInfo) mPortSpinner.getSelectedItem();
    	long maxVal = (long) (Math.pow(2, portInfo.getNumBits()) - 1);
    	
    	long amp = maxVal;
    	long offset = amp / 2;
    	
    	switch(waveForm) {
    	
    	case SQUAREWAVE:
    		for (int i = 0; i < numSamples; i++) {
    			if(i % 2 == 0)
    				data[0][i] = maxVal;
    			else
    				data[0][i] = 0;
    		}	
    		break;
    		
    	case TRIANGLE:
    		for (int i = 0; i < numSamples; i++)
    			if(i < (numSamples / 2))
    				data[0][i] = (long) ((2.0 * i / numSamples) * amp);
    			else
    				data[0][i] = (long) (amp - ((2.0 * i / numSamples) - 1) * (amp));
    		break;
    		
    	case SAWTOOTH:
    		for (int i = 0; i < numSamples; i++)
    				data[0][i] = (long) ((float)i / (numSamples - 1) * amp);
    		break;
    		
    	case SINEWAVE:
	    	for (int i = 0; i < numSamples; i++)
	    		data[0][i] = (long) ((amp / 2) * Math.sin(i * Math.PI * 2 / (numSamples - 1)) + offset);
	    	break;
	    	
    	}
    }
    
    public class OnOutputTypeSelectedListener implements OnItemSelectedListener {    
    	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {      
    		
	    	if(mScanData != null) {
	        	Waveform waveform = (Waveform) mOutputTypeSpinner.getSelectedItem();
	        	fillOutputData(mScanData, waveform);
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
    
    private CompoundButton.OnCheckedChangeListener mOnCheckChangedListener = new CompoundButton.OnCheckedChangeListener() {

		@Override
		public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
			
			mTrigTypeSpinner.setEnabled(arg1);
		}
    	
    };
    
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
    	
    	mPortSpinner = (Spinner) findViewById(R.id.spinner_port);
    	mPortAdapter =  new ArrayAdapter <PortInfo> (this, android.R.layout.simple_spinner_item );
    	mPortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mPortSpinner.setAdapter(mPortAdapter);
    	mPortSpinner.setEnabled(false);
    	
    	mOutputTypeSpinner = (Spinner) findViewById(R.id.spinner_outputType);
    	mOutputTypeAdapter =  new ArrayAdapter <Waveform> (this, android.R.layout.simple_spinner_item );
    	mOutputTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mOutputTypeSpinner.setAdapter(mOutputTypeAdapter);
    	mOutputTypeSpinner.setOnItemSelectedListener(new OnOutputTypeSelectedListener());
    	
    	mOutputTypeAdapter.addAll(EnumSet.allOf(Waveform.class));
    	
    	mRateEditText =  (EditText)findViewById(R.id.editText_rate);
    	mRateEditText.setText(String.valueOf(DEFAULT_RATE));
    	mRateEditText.setFocusableInTouchMode(false);
    	
    	mContinuousCheckBox = (CheckBox) findViewById(R.id.checkBox_continuous);
    	mExtClockCheckBox = (CheckBox)findViewById(R.id.checkBox_extclock);
    	mExtTrigCheckBox = (CheckBox)findViewById(R.id.checkBox_exttrig);
    	mExtTrigCheckBox.setOnCheckedChangeListener(mOnCheckChangedListener);
    	
    	mTrigTypeSpinner = (Spinner) findViewById(R.id.spinner_trigType);
    	mTrigTypeAdapter =  new ArrayAdapter <TriggerType> (this, android.R.layout.simple_spinner_item );
    	mTrigTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mTrigTypeSpinner.setAdapter(mTrigTypeAdapter);
    	mTrigTypeSpinner.setEnabled(false);
		
    	
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
	    	mDioDevice = null;
    	}
    	
        super.onDestroy();
    } 
}
