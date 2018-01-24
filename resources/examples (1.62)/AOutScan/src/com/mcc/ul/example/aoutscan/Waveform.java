package com.mcc.ul.example.aoutscan;

public enum Waveform {
	
    	SINEWAVE("Sine Wave"),
    	SQUAREWAVE("Square wave"),
    	TRIANGLE("Triangle"),
    	SAWTOOTH("Sawtooth");

    	
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
