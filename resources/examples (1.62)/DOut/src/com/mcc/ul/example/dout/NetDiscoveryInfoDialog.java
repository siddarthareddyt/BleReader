package com.mcc.ul.example.dout;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

public class NetDiscoveryInfoDialog extends DialogFragment {

	NoticeDialogListener mListener = null;
	String mHost = "192.168.0.1";
	int mPort = 54211;
	 
	EditText mEditText_host;
	EditText mEditText_port;
   
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(getActivity(), android.R.style.Theme_Holo_Light_Dialog_MinWidth));
       
		LayoutInflater inflater = getActivity().getLayoutInflater();

		View view = inflater.inflate(R.layout.discovery_info, new LinearLayout(getActivity()), false);
		builder.setView(view);
       
		mEditText_host = (EditText) view.findViewById(R.id.editText_host);
		mEditText_host.setText(mHost);
		   
		mEditText_port = (EditText) view.findViewById(R.id.editText_port);
		mEditText_port.setText(Integer.toString(mPort));
		   
		builder.setPositiveButton(R.string.net_detect_text, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
     
			if(mListener != null) {
				mHost = mEditText_host.getText().toString();
				mPort = Integer.parseInt(mEditText_port.getText().toString());
				mListener.onDialogPositiveClick(NetDiscoveryInfoDialog.this);
				}
			}
		});
   
		builder.setNegativeButton(R.string.cancel_text, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
          
				if(mListener != null)
					mListener.onDialogNegativeClick(NetDiscoveryInfoDialog.this);
			}
		});
		return builder.create();
	}
	
	public void setNoticeDialogListener(NoticeDialogListener noticeDialogListener) {
		mListener = noticeDialogListener;
	}
	 
	 
	public String getHostAddress() {
		return mHost;
	}
	 
	public int getPort() {
		return mPort;
	}
	
	public interface NoticeDialogListener {
		public void onDialogPositiveClick(DialogFragment dialog);
		public void onDialogNegativeClick(DialogFragment dialog);
	}
}
