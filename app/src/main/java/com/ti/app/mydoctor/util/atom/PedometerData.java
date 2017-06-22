package com.ti.app.mydoctor.util.atom;

/**
 * Created by giovanni on 06/04/2016.
 */
public class PedometerData {
    public int mSteps;
    public int mCalorie;
    public int mDistance;
    public int mSpeed;
    public int mPosition;
    public int mBattery = -1;
    public String mTimeStamp;
    public int mDuration;

    @Override
    public String toString() {
        return "PedometerData{" +
                "mSteps=" + mSteps +
                ", mCalorie=" + mCalorie +
                ", mDistance=" + mDistance +
                ", mSpeed=" + mSpeed +
                ", mPosition=" + mPosition +
                ", mBattery=" + mBattery +
                ", mTimeStamp='" + mTimeStamp + '\'' +
                ", mDuration=" + mDuration +
                '}';
    }
}
