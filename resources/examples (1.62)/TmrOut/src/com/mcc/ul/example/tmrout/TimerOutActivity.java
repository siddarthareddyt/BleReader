package com.mcc.ul.example.tmrout;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Timer;
import java.util.TimerTask;

import com.mcc.ul.DaqDevice;
import com.mcc.ul.DaqDeviceConnectionPermissionListener;
import com.mcc.ul.DaqDeviceDescriptor;
import com.mcc.ul.DaqDeviceInfo;
import com.mcc.ul.DaqDeviceManager;
import com.mcc.ul.ErrorInfo;
import com.mcc.ul.TmrChanInfo;
import com.mcc.ul.TmrDevice;
import com.mcc.ul.TmrIdleState;
import com.mcc.ul.TmrInfo;
import com.mcc.ul.TmrOutSettings;
import com.mcc.ul.TmrOutStatus;
import com.mcc.ul.TmrType;
import com.mcc.ul.ULException;
import com.mcc.ul.example.tmrout.NetDiscoveryInfoDialog;
import com.mcc.ul.example.tmrout.NetDiscoveryInfoDialog.NoticeDialogListener;
import com.mcc.ul.example.tmrout.R;
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
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

/*
 * Library Call Demonstrated:    TmrDevice.timerOutStart()
 								 TmrDevice.timerOutStop()

   Purpose:                      Controls an Output Timer Channel.

   Demonstration:                Sends a frequency output to the specified timer.
   
   Special Requirements:         Selected device must have a Timer output.
                                 
   Steps:
   1. Create a DaqDeviceManager object
   2. Call DaqDeviceManager.getDaqDeviceInventory() to find available DAQ devices.
   3. Call DaqDeviceManager.createDevice() to create a DaqDevice object for the desired device 
   4. Call DaqDevice.requestConnectionPermission() to request permission for connecting to the device
   5. If Permission granted call DaqDevice.connect() to connect to the device
   6. Call DaqDevice.getTmrDev() to retrieve the timer device object
   6. Call TmrDevice.timerOutStart() to start the specified timer
   7. Call TmrDevice.timerOutStop() to stop the specified timer
   
	Note: Declare the following permissions in your application manifest file (AndroidManifest.xml)
	<uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
*/

public class TimerOutActivity extends Activity {

	private DaqDeviceManager mDaqDeviceManager;
	private DaqDevice mDaqDevice;
	private TmrDevice mTmrDevice;
	
	private Timer mTmrStatusTimer;
	
	boolean mUpdateStatus;
	NetDiscoveryInfoDialog mDiscoveryInfoDlg;
	
	// UI objects
	Button mDetectButton;
	Button mConnectButton;
	Button mDisconnectButton;
	Spinner mDaqDevSpinner;
	Spinner mTmrChanSpinner;
	ToggleButton mStartButton;
	EditText mFrequencyText;
	EditText mDutyCycleText;
	EditText mPulseCountText;
	EditText mInitialDelayText;
	Spinner mIdleStateSpinner;
	ArrayAdapter<TmrIdleState> mIdleStateAdapter;
	
	ArrayAdapter<DaqDeviceDescriptor> mDaqDevInventoryAdapter;
	ArrayAdapter<Integer> mTmrChanAdapter;
	TextView mStatusTextView;
	
	final static int DEFAULT_FREQUENCY = 100;
	final static double DEFAULT_DUTYCYCLE = 0.5;
	final static int DEFAULT_PULSE_COUNT = 1000;
	final static double DEFAULT_INITIAL_DELAY = 0;
	
	
	final static int TIMER_PERIOD = 500; //ms
	final static int GREEN = Color.parseColor("#165B12");
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initActivity();
        
        mDaqDevice = null; 
        mTmrDevice = null;
        mDaqDeviceManager = new DaqDeviceManager(this);
        
        mTmrStatusTimer = null;
        
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
    				startTimerOut();
    			else
    				stopTimerOut();
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
		
    	mFrequencyText.setFocusableInTouchMode(true);
    	mDutyCycleText.setFocusableInTouchMode(true);
    	mPulseCountText.setFocusableInTouchMode(true);
    	mInitialDelayText.setFocusableInTouchMode(true);
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
    		
    		// Check if this DAQ Device supports timer output
        	if(devInfo.hasTmrDev()) {
        		mTmrDevice = mDaqDevice.getTmrDev();
        		TmrInfo tmrInfo = mTmrDevice.getInfo();
        		
        		mTmrChanAdapter.clear();
        		
        		// Get number of timers
        		int numTmrs = tmrInfo.getNumChans();
        		
        		for(int tmr = 0; tmr < numTmrs; tmr++ ) {
        				mTmrChanAdapter.add(tmr);
        		}
        		
        		if(numTmrs == 0) 
        			updateStatus("Selected device does not have timers", true);	
        		else {
        			TmrChanInfo tmrChanInf = tmrInfo.getTimerChanInfo(0);		
        			initTimerUI(tmrChanInf.getType());	
        		}
        	}
        	else
        		updateStatus("Selected device does not support timer output", true);
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
						    	if(mDaqDevice.getInfo().hasTmrDev() && mDaqDevice.getTmrDev().getInfo().getNumChans() > 0) {
						    		mStartButton.setEnabled(true);
						    	}
						    	
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
    
    void startTimerOut() {
    	int channel = (Integer) mTmrChanSpinner.getSelectedItem();
    	
    	TmrOutSettings timerSettings = new TmrOutSettings();
    	double frequency = Double.parseDouble(mFrequencyText.getText().toString());	
    	timerSettings.frequency = frequency;	
    	timerSettings.dutyCycle = Double.parseDouble(mDutyCycleText.getText().toString());	
    	timerSettings.pulseCount =  Long.parseLong(mPulseCountText.getText().toString());	
    	timerSettings.initialDelay =  Double.parseDouble(mInitialDelayText.getText().toString());
    	timerSettings.idleState = (TmrIdleState) mIdleStateSpinner.getSelectedItem();
    	
    	TmrChanInfo tmrChanInfo = mTmrDevice.getInfo().getTimerChanInfo(channel);
    	    	
    	try {
    		if(tmrChanInfo.getType() == TmrType.ADVANCED) {
    			mTmrDevice.tmrOutStart(channel, timerSettings);
    			startTmrStatusTimer();
    		} 
    		else {
    			mTmrDevice.tmrOutStart(channel, frequency);
    		}
    		
		    updateStatus("Timer started", false);
    		
		} catch (final ULException e) {
			runOnUiThread(new Runnable() {
				public void run() {
					mStartButton.setChecked(false);
					updateStatus(e.getMessage(), true);
				}
			});
		}
    }
    
    void stopTimerOut() {
    	int channel = (Integer) mTmrChanSpinner.getSelectedItem();
    	
    	try {
        	
			mTmrDevice.tmrOutStop(channel);
			stopTmrStatusTimer();
	    	updateStatus("Timer stopped", false);
		
		} catch (final ULException e) {
			runOnUiThread(new Runnable() {
				public void run() {
					mStartButton.setChecked(false);
					updateStatus(e.getMessage(), true);
				}
			});
		}
    }
    
    private void startTmrStatusTimer() {
    	mStatusTextView.setTextColor(GREEN);
    	
    	mTmrStatusTimer = new Timer();
    	mTmrStatusTimer.schedule(new TimerTask() {          
	        @Override
	        public void run() {
	        	tmrStatus();
	        }

	    }, 0, TIMER_PERIOD);
    }
    
    private void stopTmrStatusTimer() {
    	if(mTmrStatusTimer != null)
    		mTmrStatusTimer.cancel();
    }
    
    private void tmrStatus() {
    	int channel = (Integer) mTmrChanSpinner.getSelectedItem();
    	
    	try {
    		synchronized(this) {
    			if(mTmrDevice != null) {
		    		// Read the timer status
    				TmrOutStatus tmrStatus = mTmrDevice.getTmrOutStatus(channel);
    				if(tmrStatus == TmrOutStatus.RUNNING)
    					updateStatus("Timer " + channel + " is running", false);
    				else {
    					updateStatus("Timer " + channel + " stopped", false);
    					stopTmrStatusTimer();
    					
    					runOnUiThread(new Runnable() {
    						public void run() {
    							mStartButton.setChecked(false);
    						}
    					});
    					
    				}
    			}
    		}
    		
		} catch (final ULException e) {
			runOnUiThread(new Runnable() {
				public void run() {
					
					stopTmrStatusTimer();
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
    	
    	mTmrChanSpinner = (Spinner) findViewById(R.id.spinner_tmr);
    	mTmrChanAdapter =  new ArrayAdapter <Integer> (this, android.R.layout.simple_spinner_item );
    	mTmrChanAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mTmrChanSpinner.setAdapter(mTmrChanAdapter);
    	mTmrChanSpinner.setEnabled(false);
    	
    	mStatusTextView = (TextView) findViewById(R.id.textView_errInfo);
    		
    	mFrequencyText =  (EditText)findViewById(R.id.editText_frequency);
    	mFrequencyText.setEnabled(false);
    	mFrequencyText.setFocusableInTouchMode(false);
    	mFrequencyText.setText(String.valueOf(DEFAULT_FREQUENCY));
    	
    	mDutyCycleText =  (EditText)findViewById(R.id.editText_dutyCycle);
    	mDutyCycleText.setEnabled(false);
    	mDutyCycleText.setFocusableInTouchMode(false);
    	
    	mPulseCountText =  (EditText)findViewById(R.id.editText_pulseCount);
    	mPulseCountText.setEnabled(false);
    	mPulseCountText.setFocusableInTouchMode(false);
    	
    	
    	mInitialDelayText =  (EditText)findViewById(R.id.editText_initialDelay);
    	mInitialDelayText.setEnabled(false);
    	mInitialDelayText.setFocusableInTouchMode(false);
    	
    	
    	mIdleStateSpinner = (Spinner) findViewById(R.id.spinner_idleState);
    	mIdleStateAdapter =  new ArrayAdapter <TmrIdleState> (this, android.R.layout.simple_spinner_item );
    	mIdleStateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mIdleStateSpinner.setAdapter(mIdleStateAdapter);
    	mIdleStateSpinner.setEnabled(false);
    	
    	mIdleStateAdapter.addAll(EnumSet.allOf(TmrIdleState.class));
    	
    	
    	mStartButton = (ToggleButton) findViewById(R.id.toggleButton_start);
    	mStartButton.setOnClickListener(mClickListener);
    	mStartButton.setEnabled(false);
    	
    	updateStatus("Tap detect button. (If you need to detect a network DAQ device manually, press and hold detect button)", false);
    }
    
    void initTimerUI(TmrType type) {
    	if(type == TmrType.ADVANCED) {
    		mDutyCycleText.setText(String.valueOf(DEFAULT_DUTYCYCLE));
    		mPulseCountText.setText(String.valueOf(DEFAULT_PULSE_COUNT));
    		mInitialDelayText.setText(String.valueOf(DEFAULT_INITIAL_DELAY));
    		mDutyCycleText.setEnabled(true);
        	mPulseCountText.setEnabled(true);
        	mInitialDelayText.setEnabled(true);
        	mIdleStateSpinner.setEnabled(true);	
    	}
    	else {
    		mDutyCycleText.setText("0.5");
    		mPulseCountText.setText("0");
    		mInitialDelayText.setText("0");
    		mDutyCycleText.setEnabled(false);
        	mPulseCountText.setEnabled(false);
        	mInitialDelayText.setEnabled(false);
        	mIdleStateSpinner.setEnabled(false);
    	}
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
        	mTmrChanSpinner.setEnabled(true);
        	mFrequencyText.setEnabled(true);
        	
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
    	stopTmrStatusTimer();
    	
    	synchronized(this) {
	    	if(mDaqDevice != null)
				mDaqDeviceManager.releaseDaqDevice(mDaqDevice);
	    	
	    	mDaqDevice = null;
	    	mTmrDevice = null;
	    	
    	}
    	
        super.onDestroy();
    }
    
}
