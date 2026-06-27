package com.hoho.android.usbserial.examples;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class PanelData {
    private HashMap<String, String> hmap = new HashMap<String, String>();
    // Parser state — instance fields so partial packets that span two onNewData
    // callbacks are handled correctly.
    private String  keyString  = "";
    private String  K          = "";
    private boolean inValue    = false;
    // Tracks the actual data key announced by a {KEY:keyName} packet so that
    // the following {VALUE:data} packet can be stored under hmap[keyName].
    private String  pendingKey = "";

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
        Iterator<Map.Entry<String, String>> iterator = hmap.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getKey().startsWith(k))
                iterator.remove();
        }
        return true;
    }
    public String[][] displayFilterLog(String k) {
        // Collect matching entries sorted by their numeric suffix.
        // Uses startsWith so only keys that genuinely begin with the prefix are matched,
        // and substring(k.length()) so the prefix is stripped exactly once.
        TreeMap<Integer, String> found = new TreeMap<>();
        for (Map.Entry<String, String> entry : hmap.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(k)) {
                String suffix = key.substring(k.length());
                try {
                    int index = Integer.parseInt(suffix);
                    if (index >= 0)
                        found.put(index, entry.getValue());
                } catch (NumberFormatException e) {
                    // ignore keys without a valid integer suffix (e.g. bare "hist" or "log")
                }
            }
        }
        if (found.isEmpty())
            return new String[0][2];
        // Size the array to cover the highest index present (sparse slots stay null)
        String[][] logList = new String[found.lastKey() + 1][2];
        for (Map.Entry<Integer, String> e : found.entrySet())
            logList[e.getKey()][1] = e.getValue();
        return logList;
    }

    /**
     * Parses incoming serial data in {key:value} format.
     * Ignores '"', '\n', '\r' but NOT spaces.
     * Calls onKeyParsed after each complete key-value pair is stored.
     */
    public void parse(byte[] data, Runnable onKeyParsed) {
        String rx = new String(data);
        String V = "";

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
                        boolean suppressNotify = false;
                        if (K != null && !K.isEmpty() && V != null) {
                            // If this is a {KEY:actualKeyName} packet, remember the key
                            // for the {VALUE:data} packet that follows.
                            if ("KEY".equals(K)) {
                                pendingKey = V;
                                suppressNotify = true;  // don't notify until VALUE arrives
                            }
                            setPanel(K, V);
                            setPanel("KEY", K);
                            setPanel("VALUE", V);
                            // {KEY:name}{VALUE:data} two-packet protocol:
                            // also store data directly under the actual key name.
                            if ("VALUE".equals(K) && !pendingKey.isEmpty()) {
                                setPanel(pendingKey, V);
                                setPanel("KEY", pendingKey); // let postDataLayer see the real key
                                pendingKey = "";
                            }
                        }
                        keyString = "";
                        K = "";
                        inValue = false;
                        if (!suppressNotify) {
                            onKeyParsed.run();          // notify fragment to update UI
                        }
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
