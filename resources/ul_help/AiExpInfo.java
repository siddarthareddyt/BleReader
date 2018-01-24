
package com.mcc.ul;

/**
 * This class provides methods to retrieve information that describe 
 * the capabilities of the analog input subsystem on an Expansion device. To create 
 * an instance of this class, call the {@link AiInfo#getExpInfo getExpInfo()}
 * method of the {@link AiInfo AiInfo} class.
 *
 */
public class AiExpInfo
{
	private int mFirstChan;
	private AiChanInfo mAiChanInfo[];
	
	AiExpInfo(int firstChan)
	{
		mFirstChan = firstChan;	
	}
	
	/**
	 * Returns the first A/D channel number of the expansion device. 	 
	 * 
	 * @return The first A/D channel number.
	 */
	public int getFirstChan()
	{
		return mFirstChan;
	}
	
	void setTotalNumChans(int numChans)
	{
		mAiChanInfo = new AiChanInfo[numChans];
		
		for(int ch = 0; ch < numChans; ch++)
			mAiChanInfo[ch] = new AiChanInfo(ch);
	}
	
	/**
	 * Returns the maximum number of A/D channels possible on the analog input subsystem 
	 * of a EXP device.&nbsp;This value includes all channel types.
	 * @return Number of A/D channels.
	 */
	public int getTotalNumChans()
	{
		int totalNumChans = 0;
		
		if(mAiChanInfo != null)
			totalNumChans = mAiChanInfo.length;
		
		return totalNumChans;
	}
	
	void setNumChans(AiChanMode mode, int numChans)
	{
		if(mAiChanInfo != null)
		{
			for(int ch = 0; ch < numChans; ch++)
				mAiChanInfo[ch].addChanMode(mode);
		}	
	}
	
	
	/**
	 * Returns the number of A/D channels for a given channel mode. 	 
	 * 
	 * @param mode Channel mode.
	 * @return Number of analog input channels.
	 */
	public int getNumChans(AiChanMode mode)
	{
		int numChans = 0;
		
		if(mAiChanInfo != null)
		{
			for(int ch = 0; ch < mAiChanInfo.length; ch++)
			{
				if(mAiChanInfo[ch].getChanModes().contains(mode))
					numChans++;
			}	
		}	
		
		return numChans;
	}
	
	/**
	 * Returns the number of A/D channels for a given channel type. 	 
	 * 
	 * @param chanType Channel type.
	 * @return Number of analog input channels.
	 */
	
	public int getNumChans(AiChanType chanType)
	{
		int numChans = 0;
		if(mAiChanInfo != null)
		{
			for(int ch = 0; ch < mAiChanInfo.length; ch++)
			{
				if(mAiChanInfo[ch].getChanTypes().contains(chanType))
					numChans++;
			}	
		}	
		
		return numChans;
	}
	
	
	void addChanType(int firtChan, int lastChan, AiChanType chanType)
	{
		if(mAiChanInfo != null)
		{
			for(int ch = firtChan - mFirstChan; ch <= lastChan - mFirstChan; ch++)
				mAiChanInfo[ch].addChanType(chanType);
		}	
	}
	
	/**
	 * Returns {@link AiChanInfo AiChanInfo} for a given A/D channel.
	 * @param chan A/D channel.
	 * @return The AiChanInfo object.
	 */
	public AiChanInfo getChanInfo(int chan)
	{
		AiChanInfo aiChanInfo = null;
		if(mAiChanInfo != null && (chan >= mFirstChan) && (chan - mFirstChan) < mAiChanInfo.length)
			aiChanInfo = mAiChanInfo[chan - mFirstChan];
		
		return aiChanInfo;
	}
	
	void addTcType(int firtChan, int lastChan, TcType tcType)
	{
		if(mAiChanInfo != null)
		{
			for(int ch = firtChan - mFirstChan; ch <= (lastChan - mFirstChan); ch++)
				mAiChanInfo[ch].getTcInfo().addTcType(tcType);
		}	
	}
	
	
}

