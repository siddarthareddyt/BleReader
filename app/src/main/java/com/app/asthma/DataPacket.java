package com.app.asthma;

/**
 * Created by vishaltipparaju on 2/9/16.
 */
public class DataPacket {
    long index;
    String timeStamp;//vochex, vocstring;
    Accelerometer axisreading;
    double misc;



    public DataPacket() {
    }

    public long getIndex() {
        return index;
    }

    public void setIndex(long index) {
        this.index = index;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(String timeStamp) {
        this.timeStamp = timeStamp;
    }

    /*public DataPacket(double resolution, double voc, double no2, double acetone, double temperature, double humidity, double battery) {
        this.resolution = resolution;
        this.voc = voc;
        this.no2 = no2;
        this.acetone = acetone;
        this.temperature = temperature;
        this.humidity = humidity;
        this.battery = battery;

    }*/

    /*public DataPacket(long index, String timeStamp, double resolution, double voc, double no2, double acetone, double temperature, double humidity, double battery) {
        this.index = index;
        this.timeStamp = timeStamp;
        this.resolution = resolution;
        this.voc = voc;
        this.no2 = no2;
        this.acetone = acetone;
        this.temperature = temperature;
        this.humidity = humidity;
        this.battery = battery;
    }*/

    public DataPacket(long index, String timeStamp, Accelerometer axisreading, int misc) {
        this.index = index;
        this.timeStamp = timeStamp;
        this.axisreading = axisreading;
        this.misc = misc;
    }


    /*public double getResolution() {
        return resolution;
    }

    public void setResolution(double resolution) {
        this.resolution = resolution;
    }
*/



    public Accelerometer getAxisreading() {
        return axisreading;
    }

    public void setAxisreading(Accelerometer axisreading) {
        this.axisreading = axisreading;
    }
    public double getMisc() {
        return misc;
    }

    public void setMisc(double misc) {
        this.misc = misc;
    }

}
