package com.mcc.ul.example.ain.log;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Timer;
import java.util.TimerTask;

import com.mcc.ul.AiDevice;
import com.mcc.ul.AiInfo;
import com.mcc.ul.AiUnit;
import com.mcc.ul.AiChanMode;
import com.mcc.ul.DaqDevice;
import com.mcc.ul.DaqDeviceConnectionPermissionListener;
import com.mcc.ul.DaqDeviceDescriptor;
import com.mcc.ul.DaqDeviceInfo;
import com.mcc.ul.DaqDeviceManager;
import com.mcc.ul.ErrorInfo;
import com.mcc.ul.Range;
import com.mcc.ul.ULException;
import com.mcc.ul.example.ain.log.NetDiscoveryInfoDialog;
import com.mcc.ul.example.ain.log.NetDiscoveryInfoDialog.NoticeDialogListener;
import com.mcc.ul.example.ain.log.R;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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
 * Library Call Demonstrated:    AiDevice.aIn()

   Purpose:                      Reads A/D Input Channels and stores data in a file.

   Demonstration:                Displays the analog input on user-specified channels and
    							 logs the acquired data in a file.

   Special Requirements:         Selected device must have an A/D converter.
                                
                                 
   Steps:
   1. Create a DaqDeviceManager object
   2. Call DaqDeviceManager.getDaqDeviceInventory() to find available DAQ devices.
   3. Call DaqDeviceManager.createDevice() to create a DaqDevice object for the desired device 
   4. Call DaqDevice.requestConnectionPermission() to request permission for connecting to the device
   5. If Permission granted call DaqDevice.connect() to connect to the device
   6. Call DaqDevice.getAiDev() to retrieve the analog input device object
   7. Call AiDevice.aIn() to read an A/D input channel
   
	Note: Declare the following permissions in your application manifest file (AndroidManifest.xml)
	<uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
*/

public class AInLogActivity extends Activity {

	final static int GREEN = Color.parseColor("#165B12");
	
	final static int DEFAULT_TIMER_PERIOD = 1000; //ms
	final static int MIN_TIMER_PERIOD = 10;
	
	final static String LOG_FOLDER = "mcc";
	final static String DEFAULT_LOG_FILE_NAME = "data.csv";
	
	private DaqDeviceManager mDaqDeviceManager;
	private DaqDevice mDaqDevice;
	private AiDevice mAiDevice;
	private LogFileManager mLogFileManager;
	
	private Timer mAInTimer;
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
	EditText mTimerPeriodEditText;
	Spinner mChanModeSpinner;
	Spinner mRangeSpinner;
	Spinner mUnitSpinner;
	ToggleButton mStartButton;
	LinearLayout mDataLayout;
	TextView mDataTextView[];
	LinearLayout mChansLayout;
	TextView mChansTextView[];
	TextView mFileNameEditText;
	Button mViewButton;
	Button mEmailButton;
	
	ArrayAdapter<DaqDeviceDescriptor> mDaqDevInventoryAdapter;
	ArrayAdapter<Integer> mLowChanAdapter;
	ArrayAdapter<Integer> mHighChanAdapter;
	ArrayAdapter<AiChanMode> mChanModeAdapter;
	ArrayAdapter<Range> mRangeAdapter;
	ArrayAdapter<AiUnit> mUnitAdapter;
	TextView mStatusTextView;
	
	double[] mAInData;
	AiUnit mUnit;
	int mLowChan;
	int mHighChan;
	AiChanMode mChanMode;
	Range mRange;
	int mSamplesPerChan;
	long mTimerPeriod;
	int mSamplesRead;
	String mFileName;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initActivity();
        
        mDaqDevice = null;
        mAiDevice = null;
        mDaqDeviceManager = new DaqDeviceManager(this);
        
        mLogFileManager = new LogFileManager(LOG_FOLDER, DEFAULT_LOG_FILE_NAME);
        
        mAInTimer = null;
        mFileName = new String(DEFAULT_LOG_FILE_NAME);
        
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
    			updateStatus("Disconnected from "+ mDaqDevice, false);
    			break;
    		case R.id.toggleButton_start:
    			if(mStartButton.isChecked())
    				startAIn();
    			else
    				stopAInTimer();
    			break;
    		case R.id.button_view:
    			viewFile();
    		break;
    		case R.id.button_email:
    			emailFile();
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
        		
        		mAiDevice =  mDaqDevice.getAiDev();
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
        		
        		if(maxRate > 0.0) {
        			double rate = Double.parseDouble(mTimerPeriodEditText.getText().toString());

	        		if(rate > maxRate)
	        			mTimerPeriodEditText.setText(String.valueOf(maxRate));
        		}
 		
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
					    		if(mDaqDevice.getInfo().hasAiDev())
					    			mStartButton.setEnabled(true);
					    	
						    	updateStatus("Connected to " + mDaqDevice, false);
						    	// Disable screen rotation while a DAQ device is connected 
						    	lockScreenOrientation();
						    	
					    	} catch(NullPointerException e) {
					    		updateStatus("Device object no longer valid." + mDaqDevice, true);
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
    
    void startAIn()
    {
    	mAInData = null; //let garbage collector reclaim the existing data memory 
    	mLowChan = (Integer) mLowChanSpinner.getSelectedItem();
    	mHighChan = (Integer) mHighChanSpinner.getSelectedItem();
    	mChanMode = (AiChanMode) mChanModeSpinner.getSelectedItem();
    	mRange = (Range) mRangeSpinner.getSelectedItem();
    	mSamplesPerChan = Integer.parseInt(mSamplesPerChanEditText.getText().toString());
    	mTimerPeriod = (long)Double.parseDouble(mTimerPeriodEditText.getText().toString());	
    	int chanCount = mHighChan >= mLowChan ? mHighChan - mLowChan + 1 : 1;
    	mAInData = new double[chanCount];
    	mUnit = (AiUnit) mUnitSpinner.getSelectedItem();
    	
    	mSamplesRead = 0;
    	
    	if(mLowChan > mHighChan) {
    		stopAInTimer();
			updateStatus("High channel must be greater or equal low channel", true);
			return;
    	}
	
		addDataTextViews(chanCount);
		
		mFileName = mFileNameEditText.getText().toString();
				
		if(LogFileManager.isValidCsvFile(mFileName)) {
			
			mLogFileManager.setFileName(mFileName);
			
			if(mLogFileManager.fileExists())
				requestPermisionToRecreateLogFile();
			else
				startAInTimer();
				
		} else {
			stopAInTimer();
			updateStatus("Invalid file extension. The file extension must be .csv", true);
		}
    }
    
    private void aInTimer(){
    	if(mSamplesRead >= mSamplesPerChan)
    		return;
    	
    	try {
    		synchronized(this) {
    			if(mAiDevice != null) {
		    		// Read Analog data from the specified channel
    				int index = 0;
    				for(int ch = mLowChan; ch <= mHighChan; ch++) {
    		    		mAInData[index] = mAiDevice.aIn(ch, mChanMode, mRange, mUnit);
    		    		index++;
    				}
    				
    				mSamplesRead++;
    				
    				displayAndLogData();
    			}
    		}
    		
		} catch (final ULException e) {
			runOnUiThread(new Runnable() {
				public void run() {
					
					stopAInTimer();
					
					if(e.getErrorInfo() == ErrorInfo.DEADDEV)
						disconnectDaqDevice();
					
					updateStatus(e.getMessage(), true);
				}
			});
			e.printStackTrace();
		}
    }
    
    void displayAndLogData() {
    	
		runOnUiThread(new Runnable() {
			public void run() {
				
				int chanCount = mAInData.length;
				
				// display the scan data from each channel
		    	for(int i = 0; i < chanCount; i++) {
		    		
		    		if(mUnit == AiUnit.COUNTS)
		    			mDataTextView[i].setText(String.format("%.0f", mAInData[i]));
		    		else
		    			mDataTextView[i].setText(String.format("%.6f", mAInData[i]));
		    	}
		    	
		    	if(!mLogFileManager.writeData(mAInData, mUnit)){
	        		updateStatus("Unable to write to file", true);
	        		stopAInTimer();
	        		return;
		    	}
		    	
		    	if(mSamplesRead >= mSamplesPerChan) {
					stopAInTimer();
		    	}
				
				updateStatus("Number of samples acquired : " + mSamplesRead, false);
				
			}
		});
    	
    }
    
    void addDataTextViews(int chanCount) {
    	
    	mDataLayout.removeAllViews();
    	mChansLayout.removeAllViews();
    	
    	mDataTextView= new TextView[chanCount];
    	mChansTextView = new TextView[chanCount];
    	
    	int lowChan = (Integer) mLowChanSpinner.getSelectedItem();
    	
    	for(int i = 0; i < chanCount; i++) {
    		
    		mChansTextView[i] = new TextView(this);
    		mChansTextView[i].setTextColor(Color.parseColor("#0E139F"));
    		mChansTextView[i].setText("Channel " + (lowChan + i) + ":");
    		
    		mDataTextView[i] = new TextView(this);
    		mDataTextView[i].setTextColor(Color.parseColor("#0B610B"));
    		mDataTextView[i].setBackgroundColor(Color.parseColor("#F2F2F2"));
    		
			mChansTextView[i].setGravity(Gravity.RIGHT);
    	
			LinearLayout.LayoutParams scanChansLayoutParams = new LinearLayout.LayoutParams(
				     LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

			LinearLayout.LayoutParams scanDatalayoutParams = new LinearLayout.LayoutParams(
				     LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			
			scanChansLayoutParams.setMargins(0, 0, 0, 12);
			scanDatalayoutParams.setMargins(0, 0, 0, 12);
			
			mDataLayout.addView(mDataTextView[i], scanChansLayoutParams);
			mChansLayout.addView(mChansTextView[i], scanDatalayoutParams);
    	}	
    	
    	mChansTextView[chanCount - 1].setPadding(0, 0, 0, 50);
    		
    	// scroll to the bottom of the view to show the results
    	final ScrollView scrollview=((ScrollView) findViewById(R.id.scrollView1)); 
    	scrollview.post(new Runnable() {

            @Override
            public void run() {
            	mTimerPeriodEditText.setFocusableInTouchMode(false);
            	mSamplesPerChanEditText.setFocusableInTouchMode(false);
            	scrollview.fullScroll(ScrollView.FOCUS_DOWN);
            	mTimerPeriodEditText.setFocusableInTouchMode(true);
            	mSamplesPerChanEditText.setFocusableInTouchMode(true);	
            }
        });
    }
    
    private void disconnectDaqDevice() {
    	mDaqDevice.disconnect();
    	
    	stopAInTimer();
    	
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
    	
    	mSamplesPerChanEditText =  (EditText)findViewById(R.id.editText_samplesPerChan);
    	mSamplesPerChanEditText.setEnabled(false);
    	
    	mTimerPeriodEditText =  (EditText)findViewById(R.id.editText_period);
    	mTimerPeriodEditText.setText(String.valueOf(DEFAULT_TIMER_PERIOD));
    	mTimerPeriodEditText.setEnabled(false);
    	
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
    	
    	mFileNameEditText =  (EditText)findViewById(R.id.editText_fileName);
    	mFileNameEditText.setText(DEFAULT_LOG_FILE_NAME);
    	
    	mStartButton = (ToggleButton) findViewById(R.id.toggleButton_start);
    	mStartButton.setOnClickListener(mClickListener);
    	mStartButton.setEnabled(false);
    	
    	mViewButton = (Button)findViewById(R.id.button_view);
    	mViewButton.setOnClickListener(mClickListener);
    	
    	mEmailButton = (Button)findViewById(R.id.button_email);
    	mEmailButton.setOnClickListener(mClickListener);
    	
    	String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + LOG_FOLDER + File.separator;// +LOG_FILE_NAME;
    	((TextView) findViewById(R.id.textView_logFilePath)).setText(filePath);
    	
    	mDataLayout = (LinearLayout) findViewById(R.id.layout_scanData);
    	mChansLayout = (LinearLayout) findViewById(R.id.layout_scanChans);
    	
    	this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    	
    	updateStatus("Tap detect button. (If you need to detect a network DAQ device manually, press and hold detect button)", false);
    	
    	findViewById(R.id.mainLayout).setFocusableInTouchMode(true);
    	findViewById(R.id.mainLayout).requestFocus();
    }
    
    private void startAInTimer() {
    	
    	if(!mLogFileManager.createHeader(mDaqDevice, mLowChan, mHighChan, mTimerPeriod)) {
    		updateStatus("Unable to create file", true);
    		mStartButton.setChecked(false);
    		return;
    	}
    	
    	mFileNameEditText.setEnabled(false);
    	
    	long timerPeriod = (long) Double.parseDouble(mTimerPeriodEditText.getText().toString());
    	
    	if(timerPeriod >= MIN_TIMER_PERIOD) {
	    	
	    	mAInTimer = new Timer();
			mAInTimer.schedule(new TimerTask() {          
		        @Override
		        public void run() {
		        	aInTimer();
		        }
	
		    }, 0, timerPeriod);
    	} else {
    		mStartButton.setChecked(false);
    		
    		updateStatus("Timer period must be greater than or equal " + MIN_TIMER_PERIOD, true);

    	}
    }
    
    private void stopAInTimer() {
    	if(mAInTimer != null)
    		mAInTimer.cancel();
    	
    	mStartButton.setChecked(false);
    	mFileNameEditText.setEnabled(true);
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
    
    
    private void requestPermisionToRecreateLogFile() {
    	AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
 
			// set title
			alertDialogBuilder.setTitle("File exists");
 
			// set dialog message
			alertDialogBuilder.setMessage("The " + mLogFileManager.getFileName() + " file already exists. Would you like to overwrite the file?");
			alertDialogBuilder.setCancelable(false);
			alertDialogBuilder.setPositiveButton("Yes",new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,int id) {
						startAInTimer();
					}
				  });
			alertDialogBuilder.setNegativeButton("No",new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,int id) {
						
						
						mStartButton.setChecked(false);
						dialog.cancel();
					}
				});
 
				AlertDialog alertDialog = alertDialogBuilder.create();
 
				alertDialog.show();		
    }
    
    void viewFile() {      
    	String fileName = mFileNameEditText.getText().toString();
    	File file = new File(Environment.getExternalStorageDirectory(), File.separator + LOG_FOLDER + File.separator + fileName);
    	
    	if(!file.exists()) {
    		updateStatus("The " + fileName + " file does not exist", true);
    		return;
    		
    	}
    	
    	if(!LogFileManager.isValidCsvFile(fileName)) {
    		updateStatus("Invalid file extension. The file extension must be .csv", true);
    	}
    	
    	if(!file.canRead()) {
    		updateStatus("Unable to read the " + fileName + " file", true);
    		return;
    	}
    	
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri data = Uri.fromFile(file);

        intent.setDataAndType(data, "text/csv");

        try {
        	startActivity(intent);
        } catch(Exception e) {
        	updateStatus("Unable to find an app to handle this operation. Please install a CSV viewer app.", true);
        }
    	
    }
    
    void emailFile() { 
    	String fileName = mFileNameEditText.getText().toString();
    	File file = new File(Environment.getExternalStorageDirectory(), File.separator + LOG_FOLDER + File.separator + fileName);
    	Intent intent = new Intent(Intent.ACTION_SEND);
    	intent.setType("text/html");
    	intent.putExtra(Intent.EXTRA_SUBJECT, "Logged data");
    	intent.putExtra(Intent.EXTRA_TEXT, "");
    	
    	if(!file.exists()) {
    		updateStatus("The " + fileName + " file does not exist", true);
    		return;
    		
    	}
    	
    	if(!LogFileManager.isValidCsvFile(fileName)) {
    		updateStatus("Invalid file extension. The file extension must be .csv", true);
    	}
    	
    	if(!file.canRead()) {
    		updateStatus("Unable to read the " + fileName + " file", true);
    		return;
    	}
    	
    	Uri uri = Uri.fromFile(file);
    	intent.putExtra(Intent.EXTRA_STREAM, uri);
    	intent.setType("message/rfc822");

    	try {
    		startActivity(Intent.createChooser(intent, "Send email..."));
    	} catch(Exception e) {
        	updateStatus("Unable to find an app to handle this operation", true);
        }
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
        	mTimerPeriodEditText.setEnabled(true);
        	
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
    	stopAInTimer();
    	
    	synchronized(this) {
	    	if(mDaqDevice != null) {
				mDaqDeviceManager.releaseDaqDevice(mDaqDevice);
	    	}
    	
	    	mDaqDevice = null;
    	}
    	
        super.onDestroy();
    }
    
}
