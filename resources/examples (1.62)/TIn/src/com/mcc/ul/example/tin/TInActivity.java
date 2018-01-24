package com.mcc.ul.example.tin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Timer;
import java.util.TimerTask;

import com.mcc.ul.AiChanConfig;
import com.mcc.ul.AiChanInfo;
import com.mcc.ul.AiChanType;
import com.mcc.ul.AiDevice;
import com.mcc.ul.AiInfo;
import com.mcc.ul.DaqDevice;
import com.mcc.ul.DaqDeviceConnectionPermissionListener;
import com.mcc.ul.DaqDeviceDescriptor;
import com.mcc.ul.DaqDeviceInfo;
import com.mcc.ul.DaqDeviceManager;
import com.mcc.ul.ErrorInfo;
import com.mcc.ul.TcConfig;
import com.mcc.ul.TcType;
import com.mcc.ul.TempScale;
import com.mcc.ul.ULException;
import com.mcc.ul.example.tin.NetDiscoveryInfoDialog;
import com.mcc.ul.example.tin.NetDiscoveryInfoDialog.NoticeDialogListener;
import com.mcc.ul.example.tin.R;

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
 * Library Call Demonstrated:    AiDevice.tIn()

   Purpose:                      Reads a temperature channel.

   Demonstration:                Displays the analog input on a user-specified
                                 channel.
                                 
   Steps:
   1. Create a DaqDeviceManager object
   2. Call DaqDeviceManager.getDaqDeviceInventory() to find available DAQ devices.
   3. Call DaqDeviceManager.createDevice() to create a DaqDevice object for the desired device 
   4. Call DaqDevice.requestConnectionPermission() to request permission for connecting to the device
   5. If Permission granted call DaqDevice.connect() to connect to the device
   6. Call DaqDevice.getAiDev() to retrieve the analog input device object
   7. Call AiDevice.tIn() to read an A/D input channel
   
	Note: Declare the following permissions in your application manifest file (AndroidManifest.xml)
	<uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
*/

public class TInActivity extends Activity {

	private DaqDeviceManager mDaqDeviceManager;
	private DaqDevice mDaqDevice;
	private AiDevice mAiDevice;
	
	private Timer mAInTimer;
	private AiChanType mChanType;
	private boolean mUpdateStatus;
	private boolean mUpdateTcType;
	NetDiscoveryInfoDialog mDiscoveryInfoDlg;
	
	// UI objects
	Button mDetectButton;
	Button mConnectButton;
	Button mDisconnectButton;
	Spinner mDaqDevSpinner;
	Spinner mChanSpinner;
	EditText mChanTypeEditText;
	EditText mNAEditText;
	Spinner mTcTypeSpinner;
	Spinner mScaleSpinner;
	ToggleButton mStartButton;
	EditText mDataValue;
	
	ArrayAdapter<DaqDeviceDescriptor> mDaqDevInventoryAdapter;
	ArrayAdapter<Integer> mChanAdapter;
	ArrayAdapter<TcType> mTcTypeAdapter;
	ArrayAdapter<TempScale> mScaleAdapter;
	TextView mStatusTextView;
	
	final static int TIMER_PERIOD = 500; //ms
	final static int GREEN = Color.parseColor("#165B12");
	
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
    			else {
    				stopAInTimer();
    				mTcTypeSpinner.setEnabled(true);
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
        		
        		if(aiInfo.hasTempChan()) {
	        	
	        		// Get number of TC channels
	        		int numChans = aiInfo.getNumChans(AiChanType.TC);
	        		mChanAdapter.clear();
	        		
	        		for(int chan = 0; chan < numChans; chan++ )
	        			mChanAdapter.add(chan);
	        		
	        		
	        		mScaleAdapter.clear();
	        		mScaleAdapter.addAll(EnumSet.allOf(TempScale.class));
        		}
        		else {
            		updateStatus("Selected device does not have temperature channel", true);
            	}
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
					    	
					    	if( mChanSpinner.getSelectedItem() != null) {
						    	int chanNum = (Integer) mChanSpinner.getSelectedItem();
						    	updateChanType(chanNum);
					    	}
					    	
					    	try {
					    		if(mDaqDevice.getAiDev().getInfo().hasTempChan())
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
    
    private void tInTimer(){
    	
    	final int channel = (Integer) mChanSpinner.getSelectedItem();
    	TempScale scale = (TempScale) mScaleSpinner.getSelectedItem();

    	try {
    		synchronized(this) {
    			if(mAiDevice != null) {
    				
    				if(mChanType == AiChanType.TC && mUpdateTcType) {
    					
	    	    		AiChanConfig aiChanConfig = mAiDevice.getConfig().getAiChanConfig(channel);
	    	    		TcConfig tcConfig = aiChanConfig.getTcConfig();
	    	    		
	    	    		TcType tcType = (TcType) mTcTypeSpinner.getSelectedItem();
	    	    		
	    	    		// Set the TC type
	    				tcConfig.setTcType(tcType);
	    				
	    				mUpdateTcType = false;
    				}
    				
		    		// Read Analog data from the specified channel
		    		final double tInValue = mAiDevice.tIn(channel, scale);
		    		
		    		runOnUiThread(new Runnable() {
						@SuppressLint("DefaultLocale")
						public void run() {
							updateStatus("Reading channel " + channel, false);
						
							String strData;
							
							strData = String.format("%.10f", tInValue);
							
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
					mTcTypeSpinner.setEnabled(true);
					
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
    
    public class OnChanSelectedListener implements OnItemSelectedListener {    
    	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) { 
    		
    		int chanNum = mChanAdapter.getItem(pos);
    		updateChanType(chanNum);
    		
    	}
    	
    	public void onNothingSelected(AdapterView<?> parent){// Do nothing.
    	}
    }
    
    private void updateChanType(int chanNum) {
    	
    	if(mDaqDevice.isConnected()) {
    		
    		AiChanConfig aiChanConfig = mAiDevice.getConfig().getAiChanConfig(chanNum);
  
    		// Get the type of the selected channel
    		try {
    			
    			mChanType = aiChanConfig.getChanType();
				mChanTypeEditText.setText(mChanType.toString());
				
				mTcTypeAdapter.clear();
				
				if(mChanType == AiChanType.TC) {		
	        	
					mNAEditText.setVisibility(View.INVISIBLE);
					mTcTypeSpinner.setVisibility(View.VISIBLE);
					mTcTypeSpinner.setEnabled(true);
	        		
	        		// Get supported tcTypes
	        		AiChanInfo chanInfo = mAiDevice.getInfo().getChanInfo(chanNum);
        			EnumSet<TcType> tcTypes = chanInfo.getTcInfo().getTcTypes();
	        		mTcTypeAdapter.addAll(tcTypes);
	        		
	        		//AiChanConfig aiChanConfig = mAiDevice.getConfig().getAiChanConfig(channel);
    	    		TcConfig tcConfig = aiChanConfig.getTcConfig();
    	    		
    	    		// Get the TC type
    				TcType tcType = tcConfig.getTcType();
    	    		
    	    		mTcTypeSpinner.setSelection(mTcTypeAdapter.getPosition(tcType));
    	    		    		
				}
				else
				{
					mTcTypeSpinner.setEnabled(false);
					mNAEditText.setVisibility(View.VISIBLE);
					mTcTypeSpinner.setVisibility(View.INVISIBLE);
				}
				
			} catch (ULException e) {
				e.printStackTrace();
			}	
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
    	mChanSpinner.setOnItemSelectedListener(new OnChanSelectedListener());
    	mChanSpinner.setEnabled(false);
    	
    	mChanTypeEditText =  (EditText)findViewById(R.id.editText_chanType);
    	
    	mNAEditText =  (EditText)findViewById(R.id.editText_na);
    	mNAEditText.setVisibility(View.INVISIBLE);
    	mNAEditText.setText("N/A");
    	
    	mTcTypeSpinner = (Spinner) findViewById(R.id.spinner_tcType);
    	mTcTypeAdapter =  new ArrayAdapter <TcType> (this, android.R.layout.simple_spinner_item );
    	mTcTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mTcTypeSpinner.setAdapter(mTcTypeAdapter);
    	mTcTypeSpinner.setEnabled(false);
    	
    	mScaleSpinner = (Spinner) findViewById(R.id.spinner_scale);
    	mScaleAdapter =  new ArrayAdapter <TempScale> (this, android.R.layout.simple_spinner_item );
    	mScaleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mScaleSpinner.setAdapter(mScaleAdapter);
    	mScaleSpinner.setEnabled(false);
    	
    	mStatusTextView = (TextView) findViewById(R.id.textView_errInfo);
    	
    	mStartButton = (ToggleButton) findViewById(R.id.toggleButton_start);
    	mStartButton.setOnClickListener(mClickListener);
    	mStartButton.setEnabled(false);
    	
    	mDataValue =  (EditText)findViewById(R.id.editText_dataValue);
    	
    	updateStatus("Tap detect button. (If you need to detect a network DAQ device manually, press and hold detect button)", false);
    }
    
    private void startAInTimer() {
    	mStatusTextView.setTextColor(GREEN);
    	mTcTypeSpinner.setEnabled(false);
    	
    	mUpdateTcType = true;
    	
    	mAInTimer = new Timer();
		mAInTimer.schedule(new TimerTask() {          
	        @Override
	        public void run() {
	        	tInTimer();
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
        	mTcTypeSpinner.setEnabled(true);
        	mScaleSpinner.setEnabled(true);
        	
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
