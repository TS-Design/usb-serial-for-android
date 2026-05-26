package com.hoho.android.usbserial.examples;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PanelData {
    private HashMap<String, String> hmap = new HashMap<String, String>();
    private String keyString = "";

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
                try {
                    int index = Integer.parseInt(keyIndex);
                    if (index >= 0 && index < logList.length) {
                        logList[index][1] = entry.getValue();
                    }
                } catch (NumberFormatException e) {
                    // Ignore entries that don't have a valid integer index after "log"
                }
            }
        }
        //logList.sort(String::compareToIgnoreCase);
        return logList;
    }

    /**
     * Parses incoming serial data in {key:value} format.
     * Ignores '"', '\n', '\r' but NOT spaces.
     * Calls onKeyParsed after each complete key-value pair is stored.
     */
    public void parse(byte[] data, Runnable onKeyParsed) {
        String rx = new String(data);
        String K = "";
        String V = "";
        boolean inValue = false;  // true after the first ':' separator; colons in values are kept

        if (!rx.isEmpty()) {
            for (int i = 0; i < rx.length(); i++) {
                switch (rx.charAt(i)) {
                    case '{':                           // start key phase
                        keyString = "";
                        K = "";
                        inValue = false;
                        break;
                    case '}':                           // end: save [key, value]
                        V = keyString;
                        if (K != null && !K.isEmpty() && V != null) {
                            setPanel(K, V);
                            setPanel("KEY", K);
                            setPanel("VALUE", V);
                        }
                        keyString = "";
                        inValue = false;
                        onKeyParsed.run();              // notify fragment to update UI
                        break;
                    case ':':
                        if (!inValue) {                 // first ':' separates key from value
                            K = keyString;
                            keyString = "";
                            inValue = true;
                        } else {                        // subsequent ':' are part of the value (e.g. time "23:59:30")
                            keyString = keyString.concat(":");
                        }
                        break;
                    case '"':                           // ignore these characters
                    case '\n':
                    case '\r':
                        break;
                    default:
                        keyString = keyString.concat(String.valueOf(rx.charAt(i)));
                        break;
                }
            }
        }
    }
}
