package com.mcc.ul.example.ainscan.continuous;

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
import com.mcc.ul.example.ainscan.continuous.NetDiscoveryInfoDialog;
import com.mcc.ul.example.ainscan.continuous.NetDiscoveryInfoDialog.NoticeDialogListener;
import com.mcc.ul.example.ainscan.continuous.R;
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
import android.view.Gravity;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

/*
 * Library Call Demonstrated:    AiDevice.aInScan(), continuous background mode

   Purpose:                      Scans a range of A/D Input Channels continuously
                             	 in the background and stores the data in an array.

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
   9. Call AiDevice.stopBackground() to stop the scan
   
	Note: Declare the following permissions in your application manifest file (AndroidManifest.xml)
	<uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
*/

public class AInScanContinuousActivity extends Activity {

	final static int TIMER_PERIOD = 100; //ms
	final static int GREEN = Color.parseColor("#165B12");
	final static double DEFAULT_RATE = 100.0;
	
	private DaqDeviceManager mDaqDeviceManager;
	private DaqDevice mDaqDevice;
	private AiDevice mAiDevice;
	
	private Timer mScanStatusTimer;
	boolean mUpdateStatus;
	NetDiscoveryInfoDialog mDiscoveryInfoDlg;
	
	// UI objects
	Button mDetectButton;
	Button mConnectButton;
	Button mDisconnectButton;
	Spinner mDaqDevSpinner;
	Spinner mLowChanSpinner;
	Spinner mHighChanSpinner;
	EditText mSamplesPerChanEditText;
	EditText mRateEditText;
	Spinner mChanModeSpinner;
	Spinner mRangeSpinner;
	Spinner mUnitSpinner;
	ToggleButton mStartButton;
	LinearLayout mScanDataLayout;
	TextView mScanDataTextView[];
	LinearLayout mScanChansLayout;
	TextView mScanChansTextView[];
	
	ArrayAdapter<DaqDeviceDescriptor> mDaqDevInventoryAdapter;
	ArrayAdapter<Integer> mLowChanAdapter;
	ArrayAdapter<Integer> mHighChanAdapter;
	ArrayAdapter<AiChanMode> mChanModeAdapter;
	ArrayAdapter<Range> mRangeAdapter;
	ArrayAdapter<AiUnit> mUnitAdapter;
	TextView mStatusTextView;
	
	double[][] mScanData;
	
	AiUnit mUnit;
	int mChanCount;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initActivity();
        
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
        		AiInfo aiInfo = mDaqDevice.getAiDev().getInfo();
        		
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
    	mScanData = null; //let garbage collector reclaim the existing data memory 
    	int lowChan = (Integer) mLowChanSpinner.getSelectedItem();
    	int highChan = (Integer) mHighChanSpinner.getSelectedItem();
    	AiChanMode mode = (AiChanMode) mChanModeSpinner.getSelectedItem();
    	Range range = (Range) mRangeSpinner.getSelectedItem();
    	int samplesPerChan = Integer.parseInt(mSamplesPerChanEditText.getText().toString());
    	double rate = Double.parseDouble(mRateEditText.getText().toString());
    	EnumSet<AiScanOption> options = EnumSet.of(AiScanOption.DEFAULTIO, AiScanOption.CONTINUOUS);
    	mChanCount = highChan >= lowChan ? highChan - lowChan + 1 : 1;
    	mScanData = new double[mChanCount][samplesPerChan];
    	mUnit = (AiUnit) mUnitSpinner.getSelectedItem();
    	
    	try {
			@SuppressWarnings("unused")
			
			//Collect the values by calling the aInScan function
			double actualScanRate = mAiDevice.aInScan(lowChan, highChan, mode, range, samplesPerChan, rate, options, mUnit, mScanData);
			
			addScanDataTextViews(mChanCount);
			startScanStatusTimer();
				
    	} catch (final ULException e) {
			updateStatus(e.getMessage(), true);
			mStartButton.setChecked(false);
		}
    			
    }
    
    private void scanStatusTimer() {
    	try {
    		// Check if the background operation has finished. If it has, then the background operation must be explicitly stopped
    		synchronized(this) {
    			if(mDaqDevice != null) {
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
					
					// display the scan data from each channel
			    	for(int i = 0; i < mChanCount; i++) {
			    		if(mUnit == AiUnit.COUNTS)
			    			mScanDataTextView[i].setText(String.format("%.0f", mScanData[i][scanStatus.currentIndex]));
			    		else
			    			mScanDataTextView[i].setText(String.format("%.10f", mScanData[i][scanStatus.currentIndex]));
			    	}
				}
				
				if(scanStatus.currentStatus == Status.IDLE) {
					if(scanStatus.errorInfo != ErrorInfo.NOERROR)
						updateStatus(scanStatus.errorInfo.toString(), true);	
					else
						updateStatus("Scan stopped", false);
					
					mStartButton.setChecked(false);
				}
				else
					updateStatus("Scan is running. Number of samples acquired : " + scanStatus.currentCount, false);		
			}
		});
    	
    }
    
    void addScanDataTextViews(int chanCount) {
    	
    	mScanDataLayout.removeAllViews();
    	mScanChansLayout.removeAllViews();
    	
    	mScanDataTextView= new TextView[chanCount];
    	mScanChansTextView = new TextView[chanCount];
    	
    	int lowChan = mLowChanAdapter.getItem(mLowChanSpinner.getSelectedItemPosition());
    	
    	for(int i = 0; i < chanCount; i++) {
    		
    		mScanChansTextView[i] = new TextView(this);
    		mScanChansTextView[i].setTextColor(Color.parseColor("#0E139F"));
    		mScanChansTextView[i].setText("Channel " + (lowChan + i) + ":");
    		
    		mScanDataTextView[i] = new TextView(this);
    		mScanDataTextView[i].setTextColor(Color.parseColor("#0B610B"));
    		mScanDataTextView[i].setBackgroundColor(Color.parseColor("#F2F2F2"));
    		
			mScanChansTextView[i].setGravity(Gravity.RIGHT);
    	
			LinearLayout.LayoutParams scanChansLayoutParams = new LinearLayout.LayoutParams(
				     LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

			LinearLayout.LayoutParams scanDatalayoutParams = new LinearLayout.LayoutParams(
				     LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			
			scanChansLayoutParams.setMargins(0, 0, 0, 12);
			scanDatalayoutParams.setMargins(0, 0, 0, 12);
			
			mScanDataLayout.addView(mScanDataTextView[i], scanChansLayoutParams);
			mScanChansLayout.addView(mScanChansTextView[i], scanDatalayoutParams);
    	}	
    		
    	// scroll to the bottom of the view to show the results
    	final ScrollView scrollview=((ScrollView) findViewById(R.id.scrollView1)); 
    	scrollview.post(new Runnable() {

            @Override
            public void run() {
            	mRateEditText.setFocusableInTouchMode(false);
            	mSamplesPerChanEditText.setFocusableInTouchMode(false);
            	scrollview.fullScroll(ScrollView.FOCUS_DOWN);
            	mRateEditText.setFocusableInTouchMode(true);
            	mSamplesPerChanEditText.setFocusableInTouchMode(true);	
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
	    		
	    		for(int chan = 0; chan < numChannels; chan++ )
	    		{
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
    	
    	mSamplesPerChanEditText =  (EditText)findViewById(R.id.editText_samplesPerChan);
    	mSamplesPerChanEditText.setEnabled(false);
    	
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
    	
    	mScanDataLayout = (LinearLayout) findViewById(R.id.layout_scanData);
    	mScanChansLayout = (LinearLayout) findViewById(R.id.layout_scanChans);
    	
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
        	mSamplesPerChanEditText.setEnabled(true);
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
	    	if(mDaqDevice != null) {
				mDaqDeviceManager.releaseDaqDevice(mDaqDevice);
	    	}
    	
	    	mDaqDevice = null;
	    	mAiDevice = null;
    	}
    	
        super.onDestroy();
    }
    
}
