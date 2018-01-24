package com.mcc.ul.example.doutscan;

public enum Waveform {
	
    	
    	SQUAREWAVE("Square wave"),
    	TRIANGLE("Triangle"),
    	SAWTOOTH("Sawtooth"),
    	SINEWAVE("Sine Wave");

    	
    	private String mOutputTypeString;
    	
    	private Waveform(String str) {
    		mOutputTypeString = str;
    	}
    	
    	@Override
    	public String toString()
    	{
    		return mOutputTypeString;
    	}
}
