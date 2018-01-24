package com.mcc.ul.example.aout;

import java.util.ArrayList;
import java.util.EnumSet;

import com.mcc.ul.AoDevice;
import com.mcc.ul.AoInfo;
import com.mcc.ul.AoUnit;
import com.mcc.ul.DaqDevice;
import com.mcc.ul.DaqDeviceConnectionPermissionListener;
import com.mcc.ul.DaqDeviceDescriptor;
import com.mcc.ul.DaqDeviceInfo;
import com.mcc.ul.DaqDeviceManager;
import com.mcc.ul.ErrorInfo;
import com.mcc.ul.Range;
import com.mcc.ul.ULException;
import com.mcc.ul.example.aout.NetDiscoveryInfoDialog;
import com.mcc.ul.example.aout.NetDiscoveryInfoDialog.NoticeDialogListener;
import com.mcc.ul.example.aout.R;
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

/*
 * Library Call Demonstrated:    AoDevice.aOut()

   Purpose:                      Writes a value to the specified D/A channel.

   Demonstration:                Sets the value of D/A output.
                                 
                                 
   Steps:
   1. Create a DaqDeviceManager object
   2. Call DaqDeviceManager.getDaqDeviceInventory() to find available DAQ devices.
   3. Call DaqDeviceManager.createDevice() to create a DaqDevice object for the desired device 
   4. Call DaqDevice.requestConnectionPermission() to request permission for connecting to the device
   5. If Permission granted call DaqDevice.connect() to connect to the device
   6. Call DaqDevice.getAoDev() to retrieve the analog output device object
   6. Call AoDevice.aOut() to write the output value to the D/A channel
   
	Note: Declare the following permissions in your application manifest file (AndroidManifest.xml)
	<uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
*/

public class AOutActivity extends Activity {

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
	Spinner mUnitSpinner;
	Button mWriteButton;
	EditText mOutputValue;
	
	ArrayAdapter<DaqDeviceDescriptor> mDaqDevInventoryAdapter;
	ArrayAdapter<Integer> mChanAdapter;
	ArrayAdapter<Range> mRangeAdapter;
	ArrayAdapter<AoUnit> mUnitAdapter;
	TextView mStatusTextView;
	
	final static int TIMER_PERIOD = 500; //ms
	final static int GREEN = Color.parseColor("#165B12");
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initActivity();
        
        mAoDevice = null;
        mDaqDevice = null; 
        mDaqDeviceManager = new DaqDeviceManager(this);
        
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
    		case R.id.button_write:
    			writeOutputValue();
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
		
		mOutputValue.setFocusableInTouchMode(true);
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
        		
        		mChanAdapter.clear();
        		
        		// Get number of analog output channels
        		int numChannels = aoInfo.getTotalNumChans();
        		
        		for(int chan = 0; chan < numChannels; chan++ )
        			mChanAdapter.add(chan);
        		
        		mRangeAdapter.clear();
        		
        		// Get supported ranges for the specified channel mode
        		EnumSet<Range> ranges = aoInfo.getRanges(); 
        		
        		mRangeAdapter.addAll(ranges);	
        		
        		mUnitAdapter.clear();
        		
        		// Get supported units
        		EnumSet<AoUnit> units = aoInfo.getUnits();
        		mUnitAdapter.addAll(units);
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
						    		mWriteButton.setEnabled(true);
						    	
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
    
    
    private void writeOutputValue(){
    	
    	int channel = (Integer) mChanSpinner.getSelectedItem();
    	Range range = (Range) mRangeSpinner.getSelectedItem();
    	AoUnit unit = (AoUnit) mUnitSpinner.getSelectedItem();
    	
    	double outputVal = Double.parseDouble(mOutputValue.getText().toString());
    	
    	try {
    		// Write to the specified channel
    		mAoDevice.aOut(channel, range, unit, outputVal);
    		
    		updateStatus(outputVal +" written to D/A channel" + channel, false);
    		
		} catch (final ULException e) {
			if(e.getErrorInfo() == ErrorInfo.DEADDEV)
				disconnectDaqDevice();
			
			updateStatus(e.getMessage(), true);
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
		    	mWriteButton.setEnabled(false);
	    	
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
    	
    	mUnitSpinner = (Spinner) findViewById(R.id.spinner_unit);
    	mUnitAdapter =  new ArrayAdapter <AoUnit> (this, android.R.layout.simple_spinner_item );
    	mUnitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	mUnitSpinner.setAdapter(mUnitAdapter);
    	mUnitSpinner.setEnabled(false);
    	
    	mStatusTextView = (TextView) findViewById(R.id.textView_errInfo);
    	
    	mWriteButton = (Button) findViewById(R.id.button_write);
    	mWriteButton.setOnClickListener(mClickListener);
    	mWriteButton.setEnabled(false);
    	
    	mOutputValue =  (EditText)findViewById(R.id.editText_outputValue);
    	mOutputValue.setFocusableInTouchMode(false);
		mOutputValue.setText("0");
		
		updateStatus("Tap detect button. (If you need to detect a network DAQ device manually, press and hold detect button)", false);
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
			
			mOutputValue.setFocusableInTouchMode(true);
    		
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
    	if(mDaqDevice != null)
			mDaqDeviceManager.releaseDaqDevice(mDaqDevice);
    	
    	mDaqDevice = null;
    	mAoDevice = null;
    	
        super.onDestroy();
    }
    
}
