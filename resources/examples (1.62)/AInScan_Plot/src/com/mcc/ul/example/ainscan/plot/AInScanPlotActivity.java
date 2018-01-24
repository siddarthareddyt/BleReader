package com.mcc.ul.example.ainscan.plot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Timer;
import java.util.TimerTask;

import com.mcc.ul.AiDevice;
import com.mcc.ul.AiInfo;
import com.mcc.ul.AiScanOption;
import com.mcc.ul.AiUnit;
import com.mcc.ul.AiChanMode;
import com.mcc.ul.DaqDevice;
import com.mcc.ul.DaqDeviceConnectionPermissionListener;
import com.mcc.ul.DaqDeviceDescriptor;
import com.mcc.ul.DaqDeviceInfo;
import com.mcc.ul.DaqDeviceManager;
import com.mcc.ul.ErrorInfo;
import com.mcc.ul.Range;
import com.mcc.ul.Status;
import com.mcc.ul.ULException;
import com.mcc.ul.example.ainscan.plot.NetDiscoveryInfoDialog;
import com.mcc.ul.example.ainscan.plot.NetDiscoveryInfoDialog.NoticeDialogListener;
import com.mcc.ul.example.ainscan.plot.R;
import android.os.Bundle;
import android.os.PowerManager;
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
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

/*
 * Library Call Demonstrated:    AiDevice.aInScan(), continuous background mode

   Purpose:                      Scans a range of A/D Input Channels continuously
                             	 in the background and plots the latest acquired data.

   Demonstration:                Continuously collects data on user-specified channels.
   
   Other Library Calls:          AiDevice.getStatus()
                                 AiDevice.StopBackground()

   Special Requirements:         Selected device must have an A/D converter.
                                
                                 
   Steps:
   1. Create a DaqDeviceManager object
   2. Call DaqDeviceManager.getDaqDeviceInventory() to find available DAQ devices.
   3. Call DaqDeviceManager.createDevice() to create a DaqDevice object for the desired device 
   4. Call DaqDevice.requestConnectionPermission() to request permission for connecting to the device
   5. If Permission granted call DaqDevice.connect() to connect to the device
   6. Call DaqDevice.getAiDev() to retrieve the analog input device object
   7. Call AiDevice.aInScan() to start the scan operation
   8. Call AiDevice.getStatus() to check the status of the background operation
   9. Call AiDevice.stopBackground() when scan is completed
   
	Note: Declare the following permissions in your application manifest file (AndroidManifest.xml)
	<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    
    In order to create large data arrays add android:largeHeap="true" to AndroidManifest.xml under <application> tag
*/

public class AInScanPlotActivity extends Activity {

	final static int TIMER_PERIOD = 10; //ms
	final static int GREEN = Color.parseColor("#165B12");
	final static double DEFAULT_RATE = 500.0;
	final static int DEFAULT_SAMPLE_PER_CHAN = 10000;
	final static int MAX_SAMPLES_TO_PLOT = 1000;
	final static int MAX_SAMPLES_TO_PLOT_HIGH_RATE = 100; 
	final static int HIGH_RATE = 10000;
	
	private DaqDeviceManager mDaqDeviceManager;
	private DaqDevice mDaqDevice;
	private AiDevice mAiDevice;
	
	private Timer mScanStatusTimer;
	private PowerManager.WakeLock mWakeLock = null;
	
	boolean mUpdateStatus;
	NetDiscoveryInfoDialog mDiscoveryInfoDlg;
	
	// UI objects
	Button mDetectButton;
	Button mConnectButton;
	Button mDisconnectButton;
	Spinner mDaqDevSpinner;
	Spinner mLowChanSpinner;
	Spinner mHighChanSpinner;
	EditText mSamplesToPlotEditText;
	EditText mRateEditText;
	Spinner mChanModeSpinner;
	Spinner mRangeSpinner;
	Spinner mUnitSpinner;
	ToggleButton mStartButton;
	TextView mScanDataTextView[];
	TextView mScanChansTextView[];
	
	ArrayAdapter<DaqDeviceDescriptor> mDaqDevInventoryAdapter;
	ArrayAdapter<Integer> mLowChanAdapter;
	ArrayAdapter<Integer> mHighChanAdapter;
	ArrayAdapter<AiChanMode> mChanModeAdapter;
	ArrayAdapter<Range> mRangeAdapter;
	ArrayAdapter<AiUnit> mUnitAdapter;
	TextView mStatusTextView;
	
	double[][] mScanData;
	double[][] mPlotData;
	
	AiUnit mUnit;
	int mChanCount;
	int mSamplesToPlot;
	int mNextPlotIndex;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initActivity();
        
        // keep the system awake while this app is running
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE); 
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "AInScanPlot Tag");
		mWakeLock.acquire();
        
        mDaqDevice = null; 
        mDaqDeviceManager = new DaqDeviceManager(this);
        
        mScanStatusTimer = null;
        
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
    				startAInScan();
    			else
    				stopAInScan();
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
    		
    		// Check if this DAQ Device has an analog input device (subsystem)
        	if(devInfo.hasAiDev()) {
        		
        		mAiDevice = mDaqDevice.getAiDev();
        		AiInfo aiInfo = mAiDevice.getInfo();
        		
        		mChanModeAdapter.clear();
        		
        		mChanModeAdapter.notifyDataSetInvalidated();
        		
        		// Get supported channel modes
        		EnumSet<AiChanMode> chanModes = aiInfo.getChanModes();
        		mChanModeAdapter.addAll(chanModes);
        		
        		mUnitAdapter.clear();
        		
        		// Get supported units
        		EnumSet<AiUnit> units = aiInfo.getUnits();
        		mUnitAdapter.addAll(units);
        		
        		// Get the maximum supported scan rate
        		double maxRate = aiInfo.getMaxScanRate();
        		
        		double rate = Double.parseDouble(mRateEditText.getText().toString());

        		if(rate > maxRate)
        			mRateEditText.setText(String.valueOf(maxRate));
        	}
        	else
        		updateStatus("Selected device does not support analog input", true);
    	
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
	    	if(permissionGranted) {
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
						    	if(mDaqDevice.getInfo().hasAiDev())
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
    
    void startAInScan() {
    	int lowChan = (Integer) mLowChanSpinner.getSelectedItem();
    	int highChan = (Integer) mHighChanSpinner.getSelectedItem();
    	
    	AiChanMode mode = (AiChanMode) mChanModeSpinner.getSelectedItem();
    	Range range = (Range) mRangeSpinner.getSelectedItem();
    	mSamplesToPlot = Integer.parseInt(mSamplesToPlotEditText.getText().toString());
    	int samplesPerChan = DEFAULT_SAMPLE_PER_CHAN > mSamplesToPlot * 4 ? DEFAULT_SAMPLE_PER_CHAN : mSamplesToPlot * 4;
    	double rate = Double.parseDouble(mRateEditText.getText().toString());
    	EnumSet<AiScanOption> options = EnumSet.of(AiScanOption.DEFAULTIO, AiScanOption.CONTINUOUS);
    	mUnit = (AiUnit) mUnitSpinner.getSelectedItem();
    	mChanCount = highChan >= lowChan ? highChan - lowChan + 1 : 1;
    	mScanData = new double[mChanCount][samplesPerChan];
    	
    	int resolution = mDaqDevice.getAiDev().getInfo().getResolution();
    	
    	if(mSamplesToPlot > MAX_SAMPLES_TO_PLOT) {
    		updateStatus("Number of samples to plot is too high. Please set it to " + MAX_SAMPLES_TO_PLOT + " or less", true);
    		mStartButton.setChecked(false);
    		return;
    	}
    	if(rate > HIGH_RATE && mSamplesToPlot > MAX_SAMPLES_TO_PLOT_HIGH_RATE) {
    		updateStatus("Number of samples to plot is too high for the specified rate. Please set it to " + MAX_SAMPLES_TO_PLOT_HIGH_RATE + " or less", true);
    		mStartButton.setChecked(false);
    		return;
    	}
    	 
    	
    	try {
			@SuppressWarnings("unused")
			
			//Collect the values by calling the aInScan function
			double actualScanRate = mAiDevice.aInScan(lowChan, highChan, mode, range, samplesPerChan, rate, options, mUnit, mScanData);
			
			initPlot(lowChan, highChan, range, resolution);

			startScanStatusTimer();
				
    	} catch (final ULException e) {
			updateStatus(e.getMessage(), true);
			mStartButton.setChecked(false);
		}
    			
    }
    
    private void scanStatusTimer(){
    	try {
    		synchronized(this) {
    			if(mDaqDevice != null) {
		    		// Check if the background operation has finished. If it has, then the background operation must be explicitly stopped
		    		
					final Status scanStatus = mAiDevice.getStatus();
				
					if(scanStatus.currentStatus != Status.RUNNING) {
						// always call stopBackground upon completion...
						stopAInScan();
						
						stopScanStatusTimer();
						
						if(scanStatus.errorInfo == ErrorInfo.DEADDEV) {
							disconnectDaqDevice();
						}
					}
					
					displayScanData(scanStatus);
    			}
    		}
			
		} catch (final ULException e) {
			stopScanStatusTimer();
			
			updateStatus(e.getMessage(), true);
			e.printStackTrace();
		}
    }
    
    void displayScanData(final Status scanStatus) {
		runOnUiThread(new Runnable() {
			public void run() {
				if(scanStatus.currentIndex >= 0) {		
					if(scanStatus.currentIndex > mNextPlotIndex || (mNextPlotIndex - scanStatus.currentIndex) > mSamplesToPlot) {
    					
						int j = 0;
    					for(int ch = 0; ch < mScanData.length; ch++) {
    						j = 0;
    					
    						for(int i = mNextPlotIndex - mSamplesToPlot; i < mNextPlotIndex; i++) {
	    						mPlotData[ch][j] = mScanData[ch][i];
			    				j++;
	    					}
    					}
    					
    					DataChart.plot(mPlotData);
    					
    					mNextPlotIndex += mSamplesToPlot;
    					if(mNextPlotIndex > mScanData[0].length)
    						mNextPlotIndex = mSamplesToPlot;
					}	
				}
				
				if(scanStatus.currentStatus == Status.IDLE) {
					if(scanStatus.errorInfo != ErrorInfo.NOERROR)
						updateStatus(scanStatus.errorInfo.toString(), true);	
					else
						updateStatus("Scan stopped", false);
					
					mStartButton.setChecked(false);
				}
				else if(mDaqDevice != null && mDaqDevice.isConnected()){
					updateStatus("Scan is running. Number of samples acquired : " + scanStatus.currentCount, false);	
				}
			}
		});
    	
    }
    
    void initPlot(int lowChan, int highChan, Range range, int resolution) {
    	
    	mNextPlotIndex = mSamplesToPlot;
		mPlotData = new double[mChanCount][mSamplesToPlot];
		DataChart.init(this, lowChan, highChan, mSamplesToPlot, range, mUnit, resolution);
		
		// scroll to the bottom of the view to show the results
    	final ScrollView scrollview=((ScrollView) findViewById(R.id.scrollView1)); 
    	scrollview.post(new Runnable() {

            @Override
            public void run() {
            	mRateEditText.setFocusableInTouchMode(false);
            	mSamplesToPlotEditText.setFocusableInTouchMode(false);
            	scrollview.fullScroll(ScrollView.FOCUS_DOWN);
            	mRateEditText.setFocusableInTouchMode(true);
            	mSamplesToPlotEditText.setFocusableInTouchMode(true);	
            }
        });

    }
    
    void stopAInScan() {	
    	try {
    		if(mAiDevice != null)
    			mAiDevice.stopBackground();
		} catch (ULException e) {
			e.printStackTrace();
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
    	mStartButton.setChecked(false);
    	mStartButton.setEnabled(false);
    	
    	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
			}
    	});
    }
    
    public class OnChanModeSelectedListener implements OnItemSelectedListener {    
    	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {      
    		
    		AiDevice aiDevice = mDaqDevice.getAiDev();
    		
    		if(aiDevice != null) {
	    		AiInfo aiInfo = aiDevice.getInfo();
    		
	    		mLowChanAdapter.clear();
	    		mHighChanAdapter.clear();
	    		
	    		// Get number of analog input channels for the selected channel mode
	    		int numChannels = aiInfo.getNumChans(mChanModeAdapter.getItem(pos));
	    		
	    		for(int chan = 0; chan < numChannels; chan++ ) {
	    			mLowChanAdapter.add(chan);
	    			mHighChanAdapter.add(chan);
	    		}
	    		
	    		Range selectedRange = (Range) mRangeSpinner.getSelectedItem();	
	    		mRangeAdapter.clear();
	    		
	    		// Get supported ranges for the specified channel mode
	    		EnumSet<Range> ranges = aiInfo.getRanges(mChanModeAdapter.getItem(pos)); 
	    		
	    		mRangeAdapter.addAll(ranges);
	    		
	    		// set the range to current selected range if the new mode supports it
	    		int rangePos = mRangeAdapter.getPosition(selectedRange);
	    		if(rangePos != -1)
	    			mRangeSpinner.setSelection(rangePos);
	    		else
	    			mRangeSpinner.setSelection(0);
    		}
    		
    	}
    	
    	public void onNothingSelected(AdapterView<?> parent){// Do nothing.
    	}
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
    	
    	mLowChanSpinner = (Spinner) findViewById(R.id.spinner_lowChan);
    	mLowChanAdapter =  new ArrayAdapter <Integer> (this, android.R.layout.simple_spinner_item );
    	mLowChanAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mLowChanSpinner.setAdapter(mLowChanAdapter);
    	mLowChanSpinner.setEnabled(false);
    	
    	mHighChanSpinner = (Spinner) findViewById(R.id.spinner_highChan);
    	mHighChanAdapter =  new ArrayAdapter <Integer> (this, android.R.layout.simple_spinner_item );
    	mHighChanAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mHighChanSpinner.setAdapter(mHighChanAdapter);
    	mHighChanSpinner.setEnabled(false);
    	
    	mChanModeSpinner = (Spinner) findViewById(R.id.spinner_chanMode);
    	mChanModeAdapter =  new ArrayAdapter <AiChanMode> (this, android.R.layout.simple_spinner_item );
    	mChanModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mChanModeSpinner.setAdapter(mChanModeAdapter);
    	mChanModeSpinner.setOnItemSelectedListener(new OnChanModeSelectedListener());
    	mChanModeSpinner.setEnabled(false);
    	
    	mSamplesToPlotEditText =  (EditText)findViewById(R.id.editText_samplestoPlot);
    	mSamplesToPlotEditText.setEnabled(false);
    	
    	mRateEditText =  (EditText)findViewById(R.id.editText_rate);
		mRateEditText.setText(String.valueOf(DEFAULT_RATE));
    	mRateEditText.setEnabled(false);
    	
    	mRangeSpinner = (Spinner) findViewById(R.id.spinner_range);
    	mRangeAdapter =  new ArrayAdapter <Range> (this, android.R.layout.simple_spinner_item );
    	mRangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mRangeSpinner.setAdapter(mRangeAdapter);
    	mRangeSpinner.setEnabled(false);
    	
    	mUnitSpinner = (Spinner) findViewById(R.id.spinner_unit);
    	mUnitAdapter =  new ArrayAdapter <AiUnit> (this, android.R.layout.simple_spinner_item );
    	mUnitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mUnitSpinner.setAdapter(mUnitAdapter);
    	mUnitSpinner.setEnabled(false);
    	
    	mStatusTextView = (TextView) findViewById(R.id.textView_errInfo);
    	
    	mStartButton = (ToggleButton) findViewById(R.id.toggleButton_start);
    	mStartButton.setOnClickListener(mClickListener);
    	mStartButton.setEnabled(false);
    	
    	this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    	
    	updateStatus("Tap detect button. (If you need to detect a network DAQ device manually, press and hold detect button)", false);
    	
    	findViewById(R.id.mainLayout).setFocusableInTouchMode(true);
    	findViewById(R.id.mainLayout).requestFocus();
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
    
    private void stopScanStatusTimer() {
    	if(mScanStatusTimer != null)
    		mScanStatusTimer.cancel();
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
    
    private void updateActivity() {
    	
    	if(!mDaqDevInventoryAdapter.isEmpty()) {
    		mDaqDevSpinner.setEnabled(true);
    		mConnectButton.setEnabled(true);
        	mLowChanSpinner.setEnabled(true);
        	mHighChanSpinner.setEnabled(true);
        	mChanModeSpinner.setEnabled(true);
        	mRangeSpinner.setEnabled(true);
        	mUnitSpinner.setEnabled(true);
        	mSamplesToPlotEditText.setEnabled(true);
        	mRateEditText.setEnabled(true); 
        	
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
	    	if(mDaqDevice != null){
				mDaqDeviceManager.releaseDaqDevice(mDaqDevice);
	    	}
    	
	    	mDaqDevice = null;
    	}
    	
    	// Wake locks should be released in onPause, however in order to keep the system awake while
    	// scan is running and the application is minimized, the release method is called here 
    	if(mWakeLock != null) {
    		mWakeLock.release();
    		mWakeLock = null;
		}

    	
        super.onDestroy();
    }
    
}
