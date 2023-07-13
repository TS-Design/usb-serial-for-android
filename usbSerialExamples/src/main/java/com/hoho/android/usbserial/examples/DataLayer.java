package com.hoho.android.usbserial.examples;
import android.text.SpannableStringBuilder;

public class DataLayer {
    public String mode = "binit";
    public boolean binit = true;
    public boolean bCL = false;
    public boolean bAlarm = false;
    public boolean bLow = false;
    public boolean bHigh = false;
    public boolean bWM = false;
    public String year = "";
    public String month = "";
    public String dow = "";
    public String day = "";
    public String hrs = "";
    public String min = "";
    public String sec = "";
    public String KEY ="";
    public String VALUE = "";
    public String Tank = "0";
    private SpannableStringBuilder updateTime(String remoteYear, String remoteMonth, String remoteDay, String remoteHr, String remoteMin, String remoteSec) {
        SpannableStringBuilder updateTime = new SpannableStringBuilder();
        updateTime.append(remoteDay);
        updateTime.append("-");
        updateTime.append(remoteDay);
        updateTime.append("-");
        updateTime.append(remoteYear);
        updateTime.append("-");
        updateTime.append(remoteDay);
        updateTime.append("-");
        updateTime.append(remoteHr);
        updateTime.append(":");
        updateTime.append(remoteMin);
        updateTime.append(":");
        updateTime.append(remoteSec);
        return(updateTime);
    }
    public void setValuebyKey() {
        switch (KEY) {
            case ("mode"):
                if (VALUE.equals("binit")) {
                    mode = "binit";
                }
                if (VALUE.equals("bANR")) {
                    mode = "bANR";
                }
                else if (VALUE.equals("bBNR")) {
                    mode = "bBNR";
                }
                else if (VALUE.equals("bDMD")) {
                    mode = "bDMD";
                }
                else if (VALUE.equals("bSPY")) {
                    mode = "bSPY";
                }
                else if (VALUE.equals("bDRIP")) {
                    mode = "bDRIP";
                }
                else if (VALUE.equals("bGRAV")) {
                    mode = "bGRAV";
                }
                break;
            case("year"):
                year = VALUE;
                break;
            case("month"):
                month = VALUE;
                break;
            case("dow"):
                dow = VALUE;
                break;
            case("day"):
                day = VALUE;
                break;
            case("hrs"):
                hrs = VALUE;
                break;
            case("min"):
                min = VALUE;
                break;
            case("sec"):
                sec = VALUE;
                break;
            default: break;
           }
        }


    /* Mode */

    public String getMode() {
        return mode;
    }
    public void setMode(String mode) {
        this.mode = mode;
    }
    public String getTime() {
        String time = year + "-" + month + "-" + day +" " + hrs + ":" + min;
        return time;
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
