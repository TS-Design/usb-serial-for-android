package com.hoho.android.usbserial.examples;
import android.text.SpannableStringBuilder;

public class DataLayer {
    public String mode = "binit";
    public boolean binit = true;
    public boolean bCL = false;
    public boolean bok = false;
    public boolean balarm = false;
    public boolean bLow = false;
    public boolean bHigh = false;
    public boolean bWM = false;
    public boolean bptest = false;
    public boolean balmrset = false;
    public boolean alarmHistory = false;
    public boolean brtest = false;
    public boolean bfftest = false;
    public boolean bpertest = false;
    public boolean balrmltch = false;
    public boolean bmantest = false;
    public boolean bairalrm = false;
    public boolean RY1 = false;
    public boolean recirculate = false;
    public boolean effpump2 = false;
    public boolean effpump = false;
    public boolean filterFlush = false;
    public boolean peristolic = false;
    public boolean RY2 = false;
    public String rrepeat = "0";
    public String rrun = "0";
    public String airpres = "0";
    public String palmtime = "0";
    public String zone = "0";
    public String effstat = "Off";
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
    public String dosesday = "0";
    public String fdrun = "0";

    public boolean isBptest() {
        return bptest;
    }
    public void setBptest(boolean bptest) {
        this.bptest = bptest;
    }
    public boolean isBalmrset() {
        return balmrset;
    }
    public void setBalmrset(boolean balmrset) {
        this.balmrset = balmrset;
    }
    public boolean isBrtest() {
        return brtest;
    }
    public void setBrtest(boolean brtest) {
        this.brtest = brtest;
    }
    public boolean isBfftest() {
        return bfftest;
    }
    public void setBfftest(boolean bfftest) {
        this.bfftest = bfftest;
    }
    public boolean isBpertest() {
        return bpertest;
    }
    public void setBpertest(boolean bpertest) {
        this.bpertest = bpertest;
    }
    public boolean isBalrmltch() {
        return balrmltch;
    }
    public void setBalrmltch(boolean balrmltch) {
        this.balrmltch = balrmltch;
    }
    public boolean isBmantest() {
        return bmantest;
    }
    public void setBmantest(boolean bmantest) {
        this.bmantest = bmantest;
    }
    public boolean isBairalrm() {
        return bairalrm;
    }
    public void setBairalrm(boolean bairalrm) {
        this.bairalrm = bairalrm;
    }
    public String getDosesday() {
        return dosesday;
    }
    public void setDosesday(String dosesday) {
        this.dosesday = dosesday;
    }
    public String getFdrun() {
        return fdrun;
    }
    public void setFdrun(String fdrun) {
        this.fdrun = fdrun;
    }
    public String getRrepeat() {
        return rrepeat;
    }
    public void setRrepeat(String rrepeat) {
        this.rrepeat = rrepeat;
    }
    public String getRrun() {
        return rrun;
    }
    public void setRrun(String rrun) {
        this.rrun = rrun;
    }
    public String getAirpres() {
        return airpres;
    }
    public void setAirpres(String airpres) {
        this.airpres = airpres;
    }
    public String getPalmtime() {
        return palmtime;
    }
    public void setPalmtime(String palmtime) {
        this.palmtime = palmtime;
    }
    public String getZone() {
        return zone;
    }
    public void setZone(String zone) {
        this.zone = zone;
    }

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
    public boolean isBok() {
        return bok;
    }
    public void setBok(boolean bok) {
        this.bok = bok;
    }
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

}
