package com.hoho.android.usbserial.examples;

import java.util.HashMap;

public class PanelData {
    private HashMap<String, String> hmap = new HashMap<String, String>();
    public HashMap<String, String> gethmap() {
        return hmap;
    }

    public void sethmap(HashMap<String, String> hmapin) {
        hmap = hmapin;
    }

    public boolean getPanel(String k) {
        return Boolean.parseBoolean(hmap.get(k));
    }

    public void setPanel(String k, String v) {
        hmap.put(k, v);
    }

    public boolean containsKey(String k) {
        return hmap.containsKey(k);
    }

    public boolean getPanelBool(String k) {
        return Boolean.parseBoolean(hmap.get(k));
    }

    public String getPanelString(String k) {
        return hmap.get(k);
    }
}

