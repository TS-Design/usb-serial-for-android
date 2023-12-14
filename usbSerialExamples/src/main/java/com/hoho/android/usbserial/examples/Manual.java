package com.hoho.android.usbserial.examples;

import static java.util.List.of;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;

public class Manual extends Fragment implements SerialInputOutputManager.Listener, AdapterView.OnItemSelectedListener {

    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;
    private static final int UPDATE_INTERVAL_MILLIS = 200;
    private int deviceId, portNum, baudRate;
    private boolean withIoManager;
    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;
    private TextView maintLowProbe;
    private TextView maintHiProbe;
    private TextView maintAlarmProbe;
    private CheckBox recirculate;
    private CheckBox effpump2;
    private CheckBox effpump;
    private CheckBox filterFlush;
    private CheckBox peristolic;
    private CheckBox RY1;
    private CheckBox RY4;
    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    public boolean connected = false;
    public PanelData panelData = new PanelData();
    /* Hoot adds */
    public String keyString = "";
    public String remoteMin = "00";
    public String remoteSec = "00";
    public String remoteHr = "00";
    public String remoteYear = "00";
    public String remoteDow = "00";
    public String remoteDay = "00";
    public String remoteMonth = "00";
    /*  List of data layer commands to process
     *   command index keeps trck of next command to send
     *   command lenght is length of commandList
     */
    public static List<String> updateCommandList = of(
        "balarm",   // Alarm Input Probe
        "bHigh",                // High Input Probe
        "bLow",                 // Low Input Probe
        "bry1",                 // RY1 Unassigned
        "bry4",                 // RY4 Unassigned
        "bptest",               // RY2
        "bptest2",              // RY3
        "bpertest",             // Peristolic SO3
        "bfftest",              // Filter Flush SO2
        "brtest",               // Recirculate SO1
        "airpres"               // Air Pressure
    );
    public int commandLength = updateCommandList.size();
    public int commandListIndex = 0;
    public Manual() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            ? UsbPermission.Granted : UsbPermission.Denied;
                    connect();
                }
            }
        };
        mainLooper = new Handler(Looper.getMainLooper());
    }
    /*
     * Lifecycle
     */
    final Runnable timeHandler = new Runnable() {  // Post Time not used
        @Override
        public void run() {
            //timeRemote.setText(dataLayer.getTime());
            mainLooper.postDelayed(timeHandler,1000);
        }
    };
    final Runnable update = new Runnable() { // Read Panel Status
        public void run() {
            //Toast.makeText(getActivity(), "Update ", Toast.LENGTH_SHORT).show();
            getPanelStatus();
        }
    };
    final Runnable postMsg = new Runnable() { // Post Data to UI
        public void run() {
            postDataLayer();

            //Toast.makeText(getActivity(), "HID Timeout", Toast.LENGTH_SHORT).show();
        }
    };
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        assert getArguments() != null;
        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");
        withIoManager = getArguments().getBoolean("withIoManager");
        mainLooper.postDelayed(timeHandler,1000);
    }
    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));

        if(usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted)
            mainLooper.post(this::connect);
    }
    @Override
    public void onPause() {
        if(connected) {
            status("disconnected");
            disconnect();
        }
        getActivity().unregisterReceiver(broadcastReceiver);
        super.onPause();
    }
    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.manual_main, container, false);
        maintLowProbe = view.findViewById(R.id.maintLowProbe);
        maintAlarmProbe = view.findViewById(R.id.maintAlarmProbe);
        maintHiProbe = view.findViewById(R.id.maintHiProbe);
        RY1 = view.findViewById(R.id.RY1);
        RY4 = view.findViewById(R.id.RY4);
        recirculate = view.findViewById(R.id.recirculate);
        effpump = view.findViewById(R.id.effpump);
        effpump2 = view.findViewById(R.id.effpump2);
        filterFlush = view.findViewById(R.id.filterFlush);
        peristolic = view.findViewById(R.id.peristolic);
        /* CallBacks */
        RY1.setOnClickListener(v -> RY1Callback());  // something is always true
        RY4.setOnClickListener(v -> RY4Callback());
        recirculate.setOnClickListener(v -> recirculateCallback());
        effpump.setOnClickListener(v -> effpumpCallback());
        effpump2.setOnClickListener(v -> effpump2Callback());
        filterFlush.setOnClickListener(v -> filterFlushCallback());
        peristolic.setOnClickListener(v -> peristolicCallback());
        /* Start Update timer to sync UI   */
        mainLooper.postDelayed(update, UPDATE_INTERVAL_MILLIS);
        return view;
    }
    private void RY1Callback() {
        if(RY1.isChecked())
            sendJson("bry1","true");
        else
            sendJson("bry1","false");
    }
    private void RY4Callback() {
        if(RY4.isChecked())
            sendJson("bry4","true");
        else
            sendJson("bry4","false");
    }
    private void peristolicCallback() {
        if(peristolic.isChecked())
            sendJson("bpertest","true");
        else
            sendJson("bpertest","false");
    }
    private void filterFlushCallback() {
        if(filterFlush.isChecked())
            sendJson("bfftest","true");
        else
            sendJson("bfftest","false");
    }
    private void effpump2Callback() {
        if(effpump2.isChecked())
            sendJson("bptest2","true");
        else
            sendJson("bptest2","false");
    }
    private void effpumpCallback() {
        if(effpump.isChecked())
            sendJson("bptest","true");
        else
            sendJson("bptest","false");
    }
    private void recirculateCallback()  {
        if(recirculate.isChecked())
            sendJson("brtest","true");
        else
            sendJson("brtest","false");
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            //receiveText.setText("");
            return true;
        } else if( id == R.id.send_break) {
            if(!connected) {
                Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            } else {
                try {
                    usbSerialPort.setBreak(true);
                    Thread.sleep(100); // should show progress bar instead of blocking UI thread
                    usbSerialPort.setBreak(false);
                    SpannableStringBuilder spn = new SpannableStringBuilder();
                    spn.append("send <break>\n");
                    spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    //receiveText.append(spn);
                } catch(UnsupportedOperationException ignored) {
                    Toast.makeText(getActivity(), "BREAK not supported", Toast.LENGTH_SHORT).show();
                } catch(Exception e) {
                    Toast.makeText(getActivity(), "BREAK failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial
     */
    @Override
    public void onNewData(byte[] data) {
        mainLooper.post(() -> {
            receive(data); });
    }

    @Override
    public void onRunError(Exception e) {
        mainLooper.post(() -> {
            status("connection lost: " + e.getMessage());
            disconnect();
        });
    }
    /*
     * Serial + UI
     */
    private void connect() {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        for(UsbDevice v : usbManager.getDeviceList().values())
            if(v.getDeviceId() == deviceId)
                device = v;
        if(device == null) {
            status("connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if(driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if(driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        if(driver.getPorts().size() < portNum) {
            status("connection failed: not enough ports at device");
            return;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if(usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(INTENT_ACTION_GRANT_USB), flags);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if(usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE);
            if(withIoManager) {
                usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
                usbIoManager.start();
            }
            status("connected");
            connected = true;
        } catch (Exception e) {
            status("connection failed: " + e.getMessage());
            disconnect();
        }
    }
    private void disconnect() {
        connected = false;
        if(usbIoManager != null) {
            usbIoManager.setListener(null);
            usbIoManager.stop();
        }
        usbIoManager = null;
        try {
            usbSerialPort.close();
        } catch (IOException ignored) {}
        usbSerialPort = null;
    }
    private void putTextColor(TextView tv, boolean value) {
       if (value) {
          tv.setTextColor(Color.BLACK);
          tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
       } else {
          tv.setTextColor(Color.BLACK);
          tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
       }
    }
    public void postDataLayer() {  // Update UI inputs and outputs
        /* Post these everytime */
        if(panelData.containsKey("bLow"))
            putTextColor(maintLowProbe, panelData.getPanelBool("bLow"));
        if(panelData.containsKey("bHigh"))
            putTextColor(maintHiProbe, panelData.getPanelBool("bHigh"));
        if(panelData.containsKey("balarm"))
            putTextColor(maintAlarmProbe, panelData.getPanelBool("balarm"));
        if(panelData.containsKey("bptest"))
            putTextColor(effpump, panelData.getPanelBool("bptest"));  //RY3
        if(panelData.containsKey("bptest2"))
            putTextColor(effpump2, panelData.getPanelBool("bptest2"));
        if(panelData.containsKey("brtest"))
            putTextColor(recirculate, panelData.getPanelBool("brtest"));
        if(panelData.containsKey("bfftest"))
            putTextColor(filterFlush, panelData.getPanelBool("bfftest"));
        if(panelData.containsKey("bpertest"))
            putTextColor(peristolic, panelData.getPanelBool("bpertest"));
        if(panelData.containsKey("bry1"))
            putTextColor(RY1, panelData.getPanelBool("bry1"));
        if(panelData.containsKey("bry4"))
            putTextColor(RY4, panelData.getPanelBool("bry4"));

       }
   public void getPanelStatus() {
 /*       if(check5lTime()) {
            Toast.makeText(getActivity(), "Update 5L Time", Toast.LENGTH_SHORT).show();
        }*/

           mainLooper.postDelayed(update, UPDATE_INTERVAL_MILLIS);
           //mainLooper.postDelayed(clearAck, 200);
           if (connected) {
               sendJson(updateCommandList.get(commandListIndex++), "Query");
               if (commandListIndex == commandLength)
                   commandListIndex = 0;
           }
       }
   private boolean sendJson (String cmd, String value){
       int j = 0;
       SpannableStringBuilder json = new SpannableStringBuilder();
       json.append("{\"");
       json.append(cmd);
       json.append("\":");
       json.append(value);
       json.append("}");
       json.append("\n");
       try {
           send(String.valueOf(json));
       } catch (Exception e) {
           onRunError(e);
       }
       return true;
   }
   private void send (String str) {
           if (!connected) {
               Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
               return;
           }
           try {
               //dataLayer.setMsgAck(false);
               byte[] data = (str + '\n').getBytes();
               SpannableStringBuilder spn = new SpannableStringBuilder();
            /* spn.append("send " + data.length + " bytes\n");
            spn.append(HexDump.dumpHexString(data)).append("\n");
            spn.append(data + "\n");*/
               spn.append(str);
               spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
               //receiveText.append(spn);
               usbSerialPort.write(data, WRITE_WAIT_MILLIS);
           } catch (Exception e) {
               onRunError(e);
           }
       }
   public void receive(byte[] data) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        if(data.length > 0)
        {
//            spn.append("receive " + data.length + " bytes\n");
//            spn.append(HexDump.dumpHexString(data)).append("\n");
            parse(data);
        }
    }
   public void parse(byte[] data) {
        String rx = new String(data);
        String K = null;
        String V = null;
        boolean key = false;
        boolean value = false;

        if(rx.length() > 0){
            for(int k = 0; k < rx.length(); k++){
                switch(rx.charAt(k)) {
                    case '{':                           // start case start key phase
                        keyString = "";
                        break;
                    case '}':                           // End Parse Save [key,Value] and exit
                        V = keyString;
                        if(V != null && K != null ) {
                            panelData.setPanel(K, V);
                            panelData.setPanel("KEY", K);
                            panelData.setPanel("VALUE", V);
                            //receiveText.append( K + ":" + V  + "\n");
                        }
                        else
                            Toast.makeText(getActivity(), "PARSE -- CMD is Null", Toast.LENGTH_SHORT).show();
                        keyString = "";
                        mainLooper.post(postMsg);       // Post Message to UI
                        break;
                    case ':':                           // save key move to value phase
                        K = keyString;
                        keyString = "";
                        break;
                    case '"':                           // ignore these
                    case '\n':
                    case '\r':
                    case ' ':
                        break;
                    default:
                        keyString = keyString.concat(String.valueOf(rx.charAt(k)));  // add char to string
                        break;
                }
            }
        }
    }
   void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        //receiveText.append(spn);
    }
}
