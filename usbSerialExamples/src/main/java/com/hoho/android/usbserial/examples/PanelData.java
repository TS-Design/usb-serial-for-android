package com.hoho.android.usbserial.examples;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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

    public String deletePanelKey(String k) {
        return hmap.remove(k);
    }

    public boolean deletePanelLogs(String k) {
        boolean b = false;
        Iterator<Map.Entry<String, String>> iterator = hmap.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getKey().contains("log"))
                iterator.remove();
        }
        return b;
    }
    public String[][] displayFilterLog(String k) {
        String keyIndex;
        String[][] logList = new String[17][2];
        for (Map.Entry<String, String> entry : hmap.entrySet()) {
            if (entry.getKey().contains(k)) {       // check for substring
                keyIndex = entry.getKey().replace("log","");
                logList[Integer.parseInt(keyIndex)][1] = entry.getValue();
            }
        }
        //logList.sort(String::compareToIgnoreCase);
        return logList;
    }

}

