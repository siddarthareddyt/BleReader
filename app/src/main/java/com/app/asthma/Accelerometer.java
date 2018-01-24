package com.app.asthma;

/**
 * Created by vishaltipparaju on 5/16/16.
 */
public class Accelerometer {

    double xaxis,yaxis,zaxis;

    public Accelerometer(double xaxis, double yaxis, double zaxis) {

        this.xaxis = xaxis;
        this.yaxis = yaxis;
        this.zaxis = zaxis;
    }

    public double getXaxis() {
        return xaxis;
    }

    public void setXaxis(double xaxis) {
        this.xaxis = xaxis;
    }

    public double getYaxis() {
        return yaxis;
    }

    public void setYaxis(double yaxis) {
        this.yaxis = yaxis;
    }

    public double getZaxis() {
        return zaxis;
    }

    public void setZaxis(double zaxis) {
        this.zaxis = zaxis;
    }
}
