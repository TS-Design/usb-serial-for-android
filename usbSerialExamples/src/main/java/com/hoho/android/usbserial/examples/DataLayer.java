package com.hoho.android.usbserial.examples;

public class DataLayer {
    private boolean bCL = false;
    private boolean bAlarm = false;
    private boolean bLow = false;
    private boolean bHigh = false;
    private boolean bWM = false;
    private String mode = "";
    private String KEY ="";
    private String VALUE = "";
    private String Tank = "0";


    /* Mode */
    public String getMode() {
        return mode;
    }
    public void setMode(String mode) {
        this.mode = mode;
    }

    /* Boolean */
    public String getTank() {
        return Tank;
    }

    public void setTank(String tank) {
        Tank = tank;
    }
    public String getKEY() {
        return KEY;
    }

    public String getVALUE() {
        return VALUE;
    }

    public void setVALUE(String VALUE) {
        this.VALUE = VALUE;
    }

    public void setKEY(String KEY) {
        this.KEY = KEY;
    }

    public boolean isbWM() {
        return bWM;
    }

    public void setbWM(boolean bWM) {
        this.bWM = bWM;
    }

    public boolean isbHigh() {
        return bHigh;
    }

    public void setbHigh(boolean bHigh) {
        this.bHigh = bHigh;
    }

    public boolean isbCL() {
        return bCL;
    }

    public void setbCL(boolean bCL) { this.bCL = bCL; }

    public boolean isbAlarm() {
        return bAlarm;
    }

    public void setbAlarm(boolean bAlarm) {
        this.bAlarm = bAlarm;
    }

    public boolean isbLow() {
        return bLow;
    }

    public void setbLow(boolean bLow) {
        this.bLow = bLow;
    }
}
