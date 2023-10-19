package com.hoho.android.usbserial.examples;
import static java.util.List.of;

import java.util.HashMap;
import java.util.*;
import java.util.List;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.Map;

import android.graphics.Color;

public class manualClass extends Fragment implements SerialInputOutputManager.Listener, AdapterView.OnItemSelectedListener {

    //public HashMap<String, String> panelData;

    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;
    private static final int UPDATE_INTERVAL_MILLIS = 100;
    private int deviceId, portNum, baudRate;
    private boolean withIoManager;
    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;
    private final boolean UiMessageSent = false;
    //Handler timerHandler;
    //String currentDateTimeString = java.text.DateFormat.getDateTimeInstance().format(new Date());
    //private TextView receiveText;
    /*    private TextView fieldFlush;
        private TextView recirculate;
        private TextView pumpRuntime;
        private TextView mainEfficencyPump;
        private TextView altEfficencyPump;
        private TextView _peristalicPump;
        private TextView airpressure;
        private TextView highProbe;
        private TextView lowProbe;
        private TextView alarmExt;
        private TextView chlorineIn;
        private TextView waterMeterIn;*/
    //private TextView timeRemote;
    private TextView maintLowProbe;
    private TextView maintHiProbe;
    private TextView maintAlarmProbe;
    private CheckBox recirculate;
    private CheckBox effpump2;
    private CheckBox effpump;
    private CheckBox filterFlush;
    private CheckBox peristolic;
    private CheckBox RY1;
    private CheckBox RY2;
    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    public boolean connected = false;
   // public DataLayer dataLayer = new DataLayer();
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
    //public List<String, String> panelData;
    public static List<String> updateCommandList = of(
        "balarm",
        "bHigh",
        "bLow",
        "bry1",
        "bry2",
        "bptest",
        "bptest2",
        "bpertest",
        "bfftest",
        "brtest",
        "airpres"
        //"bmantest"                  // UI manual mode
    );
    public int commandLength = updateCommandList.size();
    public int commandListIndex = 0;
   // public HashMap panelData;
            // Creating an empty HashMap

    public manualClass() {
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
        RY2 = view.findViewById(R.id.RY2);
        recirculate = view.findViewById(R.id.recirculate);
        effpump = view.findViewById(R.id.effpump);
        effpump2 = view.findViewById(R.id.effpump2);
        filterFlush = view.findViewById(R.id.filterFlush);
        peristolic = view.findViewById(R.id.peristolic);
        /* CallBacks */
        RY1.setOnClickListener(v -> RY1Callback());  // something is always true
        RY2.setOnClickListener(v -> RY2Callback());
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
    private void RY2Callback() {
        if(RY2.isChecked())
            sendJson("bry2","true");
        else
            sendJson("bry2","false");
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
            putTextColor(maintLowProbe, panelData.getPanel("bLow"));
        if(panelData.containsKey("bHigh"))
            putTextColor(maintHiProbe, panelData.getPanel("bHigh"));
        if(panelData.containsKey("balarm"))
            putTextColor(maintAlarmProbe, panelData.getPanel("balarm"));
        if(panelData.containsKey("bptest"))
            putTextColor(effpump, panelData.getPanel("bptest"));
        if(panelData.containsKey("bLow"))
            if(panelData.containsKey("bptest2"))
            putTextColor(effpump2, panelData.getPanel("bptest2"));
        if(panelData.containsKey("brtest"))
            putTextColor(recirculate, panelData.getPanel("brtest"));
        if(panelData.containsKey("bfftest"))
            putTextColor(filterFlush, panelData.getPanel("bfftest"));
        if(panelData.containsKey("bpertest"))
            putTextColor(peristolic, panelData.getPanel("bpertest"));
        if(panelData.containsKey("bry1"))
            putTextColor(RY1, panelData.getPanel("bry1"));
        if(panelData.containsKey("bry2"))
            putTextColor(RY2, panelData.getPanel("bry2"));

       }
    /*        if ((dataLayer.getKEY()).equals("mode")) {
            //dataLayer.setMode(dataLayer.getVALUE());
            if(dataLayer.getMode().equals("bANR"))
                main_mode.check(R.id.banr);
            else if (dataLayer.getMode().equals("bBNR"))
                main_mode.check(R.id.bbnr);
            else if (dataLayer.getMode().equals("bDMD"))
                main_mode.check(R.id.bdmd);
            else if (dataLayer.getMode().equals("bSPY"))
                main_mode.check(R.id.bspy);
            else if (dataLayer.getMode().equals("bDRIP"))
                main_mode.check(R.id.bdrip);
            else if (dataLayer.getMode().equals("bGRAV"))
                main_mode.check(R.id.grav);
            else if (dataLayer.getMode().equals("binit")) {
                main_mode.check(R.id.binit);
                if (dataLayer.getTank().equals("0"))
                    enableMode = false;
                else
                    enableMode = true;
                for(int i = 0; i < main_mode.getChildCount(); i++){
                    ((RadioButton)main_mode.getChildAt(i)).setEnabled(enableMode);
                }
                if(!popUpDialogPosted) {
                    if(dataLayer.getTank().equals(0)) {
                        //showTankPopUp();
                        popUpDialogPosted = true;
                    }
                }
            }
        }
        else if ((dataLayer.getKEY()).equals("dow"))
            remoteDow = dataLayer.getVALUE();
        else if ((dataLayer.getKEY()).equals("day"))
            remoteDay = dataLayer.getVALUE();
        else if ((dataLayer.getKEY()).equals("month"))
            remoteMonth = dataLayer.getVALUE();
        else if ((dataLayer.getKEY()).equals("year"))
            remoteYear = dataLayer.getVALUE();
        else if ((dataLayer.getKEY()).equals("hrs"))
            remoteHr = dataLayer.getVALUE();
        else if ((dataLayer.getKEY()).equals("min"))
            remoteMin = dataLayer.getVALUE();
        else if ((dataLayer.getKEY()).equals("sec")) {
            remoteSec = dataLayer.getVALUE();
            timeRemote.setText(updateTime(remoteHr, remoteMin, remoteSec));
        }
        else if ((dataLayer.getKEY()).equals("Tank")) {
            Toast.makeText(getActivity(), "Tank Size " + dataLayer.getKEY(), Toast.LENGTH_SHORT).show();
        }
        else if ((dataLayer.getKEY()).equals("bANR")) {
            // kill cmd not found msg until   is enabled
        }
        else if ((dataLayer.getKEY()).equals("bBNR")) {
            // kill cmd not found msg until view is enabled
        }
        else if ((dataLayer.getKEY()).equals("bDMD")) {
            // kill cmd not found msg until view is enabled
        }
        else if ((dataLayer.getKEY()).equals("bSPY")) {
            // kill cmd not found msg until view is enabled
        }
        else if ((dataLayer.getKEY()).equals("bALARM")) {
            // kill cmd not found msg until view is enabled
        }
        else if ((dataLayer.getKEY()).equals("tank")) {
            if (dataLayer.getVALUE() != null) {
                tankDropDown.setSelection(((ArrayAdapter)tankDropDown.getAdapter()).getPosition(dataLayer.getVALUE()));
                // mainLooper.post(waitOnTank);
            }
            else {
                dataLayer.setTank(dataLayer.getVALUE());
                mainLooper.post(modeSpinner);
            }
        }*/
       //       else
       //Toast.makeText(getActivity(), "CMD not Recognized " + dataLayer.getKEY(), Toast.LENGTH_SHORT).show();
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
    /*private SpannableStringBuilder localTime(int remoteHr, int remoteMin, int remoteSec){
        SpannableStringBuilder remoteTime = new SpannableStringBuilder();
        remoteTime.append(String.valueOf(remoteHr));
        remoteTime.append(":");
        remoteTime.append(String.valueOf(remoteMin));
        remoteTime.append(":");
        remoteTime.append(String.valueOf(remoteSec));
        return(remoteTime);
    }
    private SpannableStringBuilder updateTime(String remoteYear, String remoteMonth, String remoteDay, String remoteHr, String remoteMin, String remoteSec){
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
    }*/
    /*  private void ackModeCmd(RadioButton activeButton){
        bgrav.setTextColor(Color.WHITE);
        banr.setTextColor(Color.WHITE);
        bbnr.setTextColor(Color.WHITE);
        bspy.setTextColor(Color.WHITE);
        bdrip.setTextColor(Color.WHITE);
        bdmd.setTextColor(Color.WHITE);
        if(activeButton == bgrav)
            bgrav.setTextColor(Color.GREEN);
        else if (activeButton == banr)
            banr.setTextColor(Color.GREEN);
        else if (activeButton == bbnr)
            bbnr.setTextColor(Color.GREEN);
        else if (activeButton == bspy)
            bspy.setTextColor(Color.GREEN);
        else if (activeButton == bdrip)
            bdrip.setTextColor(Color.GREEN);
        else if (activeButton == bdmd)
            bdmd.setTextColor(Color.GREEN);
    }*/
       private void gravCallback () {
           sendJson("bGRAV", "true");
       }
    /*  private void banrCallback() {
        sendJson("bANR","true");
        Bundle args = new Bundle();
        args.putInt("device", deviceId);
        args.putInt("port", portNum);
        args.putInt("baud", baudRate);
        args.putBoolean("withIoManager", withIoManager);
        Fragment TerminalFragment = new AnrFragment();
        TerminalFragment.setArguments(args);
        getParentFragmentManager().beginTransaction().replace(R.id.fragment, TerminalFragment, "anr").addToBackStack(null).commit();
    }*/
       private void bbnrCallback() {
           sendJson("bBNR", "true");
       }
       private void bspyCallback () {
           sendJson("bSPY", "true");
       }
       private void bdmdCallback () {
           sendJson("bDMD", "true");
       }
       private void bdripCallback () {
           sendJson("bDRIP", "true");
       }
       private void binitCallback () {
//        if(dataLayer.getTank().equals("0") && main_mode.isEnabled())
//            main_mode.setEnabled(false);
//        else
//            main_mode.setEnabled(true);
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
    /*private void read() {
        if(!connected) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] buffer = new byte[8192];
            int len = usbSerialPort.read(buffer, READ_WAIT_MILLIS);
    /*      if(len == -1)
                msgAck = false;
            else
                msgAck = true;*/
    /*    receive(Arrays.copyOf(buffer, len));
        } catch (IOException e) {
            // when using read with timeout, USB bulkTransfer returns -1 on timeout _and_ errors
            // like connection loss, so there is typically no exception thrown here on error
            status("connection lost: " + e.getMessage());
            disconnect();
        }
    }*/
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
                        panelData.setPanel(K, V);
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
