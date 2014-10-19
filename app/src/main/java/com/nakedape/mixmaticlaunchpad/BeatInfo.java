package com.nakedape.mixmaticlaunchpad;

/**
 * Created by Nathan on 8/31/2014.
 */
public class BeatInfo {
    private double time;
    private double salience;

    public BeatInfo(double time, double salience){
        this.time = time;
        this.salience = salience;
    }

    public double getTime(){
        return time;
    }

    public double getSalience(){
        return salience;
    }
}
