package com.mcc.ul.example.ain.log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;


import com.mcc.ul.AiUnit;
import com.mcc.ul.DaqDevice;

import android.annotation.SuppressLint;
import android.os.Environment;

@SuppressLint("DefaultLocale")
public class LogFileManager {
	
	FileWriter mFileWriter;
	String mFileName;
	File mFolder;
	File mFile;
	String mFilePath;
	SimpleDateFormat mDateFormat;
	SimpleDateFormat mTimeFormat;
	
	LogFileManager(String folder, String fileName) {
		mFolder = new File (Environment.getExternalStorageDirectory(), File.separator + folder);
		mFileName = fileName;
		mFilePath = mFolder + File.separator + fileName;
		
		mFile = new File(mFilePath);
		
		mDateFormat = new SimpleDateFormat("dd-MMM-yyyy", Locale.US);
		mTimeFormat = new SimpleDateFormat("hh:mm:ss.SSS aaa", Locale.US);
	}
	
	public void setFileName(String fileName) {
		mFileName = fileName;
		mFilePath = mFolder + File.separator + fileName;
		mFile = new File(mFilePath);
	}
	
	public String getFileName() {
		return mFileName;
	}
	
	 public boolean fileExists() {
	    	if(mFile.exists())
	    		return true;
	    	else
	    		return false;
	 }
	 
	 public boolean createHeader(DaqDevice daqDev, int lowChan, int highChan, long period) {
	    	
		boolean created = true;
    	if (!mFolder.exists())
    		mFolder.mkdir();
    		
        String date = mDateFormat.format(Calendar.getInstance().getTime()); 
        String time = mTimeFormat.format(Calendar.getInstance().getTime()); 

    	try {
			mFileWriter = new FileWriter(mFilePath);
			mFileWriter.append("Date,");
			mFileWriter.append(date);
			mFileWriter.append('\n');
			mFileWriter.append("Time,");
			mFileWriter.append(time);
			mFileWriter.append('\n');
			mFileWriter.append("Device,");
			mFileWriter.append(daqDev.getDescriptor().productName);
			mFileWriter.append('\n');
			mFileWriter.append("Serial Number,");
			mFileWriter.append(daqDev.getConfig().getSerialNumber());
			mFileWriter.append('\n');
			mFileWriter.append("Timer Period(ms),");
			mFileWriter.append(Long.toString(period));
			mFileWriter.append('\n');
			mFileWriter.append('\n');
			mFileWriter.append("Time,");
			for(int ch = lowChan; ch <= highChan; ch++) {
				mFileWriter.append("channel " + ch);
				if(ch != highChan)
					mFileWriter.append(",");
			}
				
			mFileWriter.append('\n');
			mFileWriter.flush();
			
		} catch (IOException e) {
			created = false;
			e.printStackTrace();
		}
    	
    	return created;
    }
	 
	
	boolean writeData(double[] data, AiUnit unit) {
		boolean written = false;
		
		if(data == null || mFileWriter == null || !mFile.exists())
			return written;
		
		String dataStr;
		
		for(int i = 0; i < data.length; i++) {
    		if(unit == AiUnit.COUNTS)
    			dataStr = String.format(Locale.US, "%.0f", data[i]);
    		else
    			dataStr = String.format(Locale.US, "%.6f", data[i]);
    		
    		try {
    			
    			if (i == 0) {
			        String time = mTimeFormat.format(Calendar.getInstance().getTime()); 
			        mFileWriter.append(time);
			        mFileWriter.append(',');
    			}
    			
				mFileWriter.append(dataStr);
				if( i != (data.length - 1))
					mFileWriter.append(',');
				else { 
					mFileWriter.append('\n');
					mFileWriter.flush();
					written = true;
				}
						
			} catch (IOException e) {
				written = false;
			}	
    	}
		
		return written;
	}
	
	public static boolean isValidCsvFile(String fileName) {
    	boolean valid = false;
    	String filenameArray[] = fileName.split("\\.");
        String extension = filenameArray[filenameArray.length-1];
        
        if(extension.equalsIgnoreCase("csv"))
        	valid = true;
        
        return valid;
    }

}
