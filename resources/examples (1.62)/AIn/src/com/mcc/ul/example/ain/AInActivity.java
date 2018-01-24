package com.mcc.ul.example.ain;

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
import com.mcc.ul.example.ain.NetDiscoveryInfoDialog.NoticeDialogListener;

import android.os.Bundle;
import android.annotation.SuppressLint;
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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

/*
 * Library Call Demonstrated:    AiDevice.aIn()

   Purpose:                      Reads an A/D Input Channel.

   Demonstration:                Displays the analog input on a user-specified
                                 channel.
                                 
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

public class AInActivity extends Activity {

	final static int TIMER_PERIOD = 500; //ms
	final static int GREEN = Color.parseColor("#165B12");
	
	private DaqDeviceManager mDaqDeviceManager;
	private DaqDevice mDaqDevice;
	private AiDevice mAiDevice;
	
	private Timer mAInTimer;
	boolean mUpdateStatus;
	NetDiscoveryInfoDialog mDiscoveryInfoDlg;
	
	// UI objects
	Button mDetectButton;
	Button mConnectButton;
	Button mDisconnectButton;
	Spinner mDaqDevSpinner;
	Spinner mChanSpinner;
	Spinner mChanModeSpinner;
	Spinner mRangeSpinner;
	Spinner mUnitSpinner;
	ToggleButton mStartButton;
	EditText mDataValue;
	
	ArrayAdapter<DaqDeviceDescriptor> mDaqDevInventoryAdapter;
	ArrayAdapter<Integer> mChanAdapter;
	ArrayAdapter<AiChanMode> mChanModeAdapter;
	ArrayAdapter<Range> mRangeAdapter;
	ArrayAdapter<AiUnit> mUnitAdapter;
	TextView mStatusTextView;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initActivity();
        
        mDaqDevice = null;
        mAiDevice = null; 
        mDaqDeviceManager = new DaqDeviceManager(this);
 
        mAInTimer = null;
        
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
    				startAInTimer();
    			else
    				stopAInTimer();
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
        	}
        	else {
        		updateStatus("Selected device does not support analog input", true);
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
    
    private void aInTimer(){
    	
    	final int channel = (Integer) mChanSpinner.getSelectedItem();
    	AiChanMode mode = (AiChanMode) mChanModeSpinner.getSelectedItem();
    	Range range = (Range) mRangeSpinner.getSelectedItem();
    	AiUnit unit = (AiUnit) mUnitSpinner.getSelectedItem();
    	
    	try {
    		synchronized(this) {
    			if(mAiDevice != null) {
		    		// Read Analog data from the specified channel
		    		final double aInValue = mAiDevice.aIn(channel, mode, range, unit);
		    		
		    		runOnUiThread(new Runnable() {
						@SuppressLint("DefaultLocale")
						public void run() {
							updateStatus("Reading channel " + channel, false);
							
							AiUnit unit = (AiUnit) mUnitSpinner.getSelectedItem();
							String strData;
							
							if(unit == AiUnit.COUNTS)
								strData = String.format("%.0f", aInValue);
							else
								strData = String.format("%.10f", aInValue);
							
							mDataValue.setText(strData);
						}
					});
    			}
    		}
    		
		} catch (final ULException e) {
			runOnUiThread(new Runnable() {
				public void run() {
					
					stopAInTimer();
					mStartButton.setChecked(false);
					
					if(e.getErrorInfo() == ErrorInfo.DEADDEV)
						disconnectDaqDevice();
					
					updateStatus(e.getMessage(), true);
				}
			});
			e.printStackTrace();
		}
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
	    		
	    		mChanAdapter.clear();
    		
	    		// Get number of analog input channels for the selected channel mode
	    		int numChannels = aiInfo.getNumChans(mChanModeAdapter.getItem(pos));
	    		
	    		for(int chan = 0; chan < numChannels; chan++ )
	    			mChanAdapter.add(chan);
	    		
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
    	
    	mChanSpinner = (Spinner) findViewById(R.id.spinner_chan);
    	mChanAdapter =  new ArrayAdapter <Integer> (this, android.R.layout.simple_spinner_item );
    	mChanAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mChanSpinner.setAdapter(mChanAdapter);
    	mChanSpinner.setEnabled(false);
    	
    	mChanModeSpinner = (Spinner) findViewById(R.id.spinner_chanMode);
    	mChanModeAdapter =  new ArrayAdapter <AiChanMode> (this, android.R.layout.simple_spinner_item );
    	mChanModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mChanModeSpinner.setAdapter(mChanModeAdapter);
    	mChanModeSpinner.setOnItemSelectedListener(new OnChanModeSelectedListener());
    	mChanModeSpinner.setEnabled(false);
    	
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
    	
    	mDataValue =  (EditText)findViewById(R.id.editText_dataValue);
    	
    	updateStatus("Tap detect button. (If you need to detect a network DAQ device manually, press and hold detect button)", false);
    }
    
    private void startAInTimer() {
    	mStatusTextView.setTextColor(GREEN);
    	
    	mAInTimer = new Timer();
		mAInTimer.schedule(new TimerTask() {          
	        @Override
	        public void run() {
	        	aInTimer();
	        }

	    }, 0, TIMER_PERIOD);
    }
    
    private void stopAInTimer() {
    	if(mAInTimer != null)
    		mAInTimer.cancel();
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
        	mChanSpinner.setEnabled(true);
        	mChanModeSpinner.setEnabled(true);
        	mRangeSpinner.setEnabled(true);
        	mUnitSpinner.setEnabled(true);
        	
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
	    	if(mDaqDevice != null)
				mDaqDeviceManager.releaseDaqDevice(mDaqDevice);
	    	
	    	mDaqDevice = null;
	    	mAiDevice = null;
    	}
    	
        super.onDestroy();
    }
    
}
