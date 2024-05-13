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
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class MicroDose extends Fragment implements SerialInputOutputManager.Listener, AdapterView.OnItemSelectedListener {

    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;
    private static final int UPDATE_INTERVAL_MILLIS = 200;
    private int deviceId, portNum, baudRate;
    private boolean withIoManager;
    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;
    private final boolean UiMessageSent = false;
    //Handler timerHandler;
    //String currentDateTimeString = java.text.DateFormat.getDateTimeInstance().format(new Date());
    private TextView receiveText;
    public PanelData panelData = new PanelData();

    private TextView timeRemote;
    //private TextView remoteTime;
    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    public boolean connected = false;
    //public DataLayer dataLayer = new DataLayer();
    /* Hoot Fragment adds */
    //static boolean cmd_busy = false;
    private TextView dosesDay;
    private TextView effStatus;
    private TextView FdRunTime;
    private TextView recirRepeatTime;
    private TextView recirRunTime;
    private TextView airPressure;
    private TextView effPumpAlarmTime;
    private TextView numberZones;
    private TextView waterFlowData;
    private TextView effStartTime;

    public Button systemOk;
    public Button effPumpTest;
    public Button alarmLatch;
    public Button alarmHistory;
    public Button ffTest;
    public Button recirTest;
    public Button alarmReset;
    public Button manualTest;
    public Button alarm;
    public Button lowProbe;
    public Button airAlarm;
    public Button peristalticTest;
    public String keyString = "";
    //public String KEY = "";
    //public String VALUE = "";
    public String remoteMin = "00";
    public String remoteSec = "00";
    public String remoteHr = "00";
    public String remoteYear = "00";
    public String remoteDow = "00";
    public String remoteDay = "00";
    public String remoteMonth = "00";
    public boolean popUpDialogPosted = false;
    //Button showPopupBtn, closePopupBtn;
    /*  List of data layer commands to process
     *   command index keeps trck of next command to send
     *   command lenght is length of commandList
     */
    public List<String> updateCommandList = of(
            "mode", "year", "month","day",
            "hour", "min", "sec",
            "tank", "bok", "bptest","balmrset",
            "brtest", "bfftest", "bpertest",
            "effstat", "airpres",
            "palmtime", "balrmltch",
            "bmantest", "balarm", "bLow", "bairalrm"
    );                                                  /* dont need bmantest? */
    public int commandLength = updateCommandList.size();
    public int commandListIndex = 0;

    public MicroDose() {
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
    final Runnable timeHandler = new Runnable() {
        @Override
        public void run() {
            String time = panelData.getPanelString("year") + "-" + panelData.getPanelString("month") + "-" + panelData.getPanelString("day") +" " + panelData.getPanelString("hrs") + ":" + panelData.getPanelString("min");
            timeRemote.setText(time);
            mainLooper.postDelayed(timeHandler,1000);
        }
    };
    final Runnable waitOnTank = new Runnable() {
        @Override
        public void run() {
            if(panelData.getPanelString("tank").equals("0")) {
                if(!popUpDialogPosted) {
                    showTankPopUp();
                    popUpDialogPosted = true;
                }
                mainLooper.postDelayed(waitOnTank, UPDATE_INTERVAL_MILLIS);
            }
            else {
                // mainLooper.postDelayed(update, UPDATE_INTERVAL_MILLIS);
                popUpDialogPosted = false;
            }
        }
    };
    final Runnable updateTank = new Runnable() {
        @Override
        public void run() {
            Toast.makeText(getActivity(), "Send Tank " + panelData.getPanelString("tank"), Toast.LENGTH_SHORT).show();
            sendJson("tank", panelData.getPanelString("tank"));
        }
    };
    final Runnable update = new Runnable() {
        public void run() {
            //Toast.makeText(getActivity(), "Update ", Toast.LENGTH_SHORT).show();
            getPanelStatus();
        }
    };
    final Runnable postMsg = new Runnable() {
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
        View view = inflater.inflate(R.layout.micro_dose, container, false);
        PopUpFragment popUpFragment;
        alarmHistory = view.findViewById(R.id.alarmHistory);
        timeRemote = view.findViewById(R.id.timeRemote);
        systemOk = view.findViewById(R.id.systemOk);
        effPumpTest = view.findViewById(R.id.effPumpTest);
        ffTest = view.findViewById(R.id.ffTest);
        peristalticTest = view.findViewById(R.id.peristalticTest);
        alarmReset = view.findViewById(R.id.alarmReset);
        recirTest = view.findViewById((R.id.recirTest));
        waterFlowData = view.findViewById(R.id.waterFlowData);
        recirRepeatTime = view.findViewById(R.id.recirRepeatTime);
        effStartTime = view.findViewById(R.id.effStartTime);
        effStatus = view.findViewById(R.id.effStatus);
        airPressure = view.findViewById(R.id.airPressure);
        effPumpAlarmTime = view.findViewById(R.id.effPumpAlarmTime);
        alarmLatch = view.findViewById(R.id.alarmLatch);
        alarm = view.findViewById(R.id.alarm);
        lowProbe = view.findViewById(R.id.lowProbe);
        airAlarm = view.findViewById(R.id.airAlarm );
        dosesDay = view.findViewById(R.id.dosesDay);


        alarmHistory.setOnClickListener(v -> alarmHistoryCallback());
        alarmReset.setOnClickListener(v -> alarmResetCallback());

        /* Start Update timer to sync UI   */
        mainLooper.postDelayed(update, UPDATE_INTERVAL_MILLIS);
        return view;
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
            receiveText.setText("");
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
    public void postDataLayer() {                           // Convert string to bool and update UI with command
        boolean enableMode;
        /* Status Banner */
        if(panelData.containsKey("bok"))
            putTextColor(systemOk, panelData.getPanelBool("bok"));
        if(panelData.containsKey("bptest"))
            putTextColor(effPumpTest, panelData.getPanelBool("bptest"));
        if(panelData.containsKey("balmrset"))
            putTextColor(alarmReset, panelData.getPanelBool("balmrset"));
        if(panelData.containsKey("brtest"))
            putTextColor(recirTest, panelData.getPanelBool("brtest"));
        if(panelData.containsKey("bfftest"))
            putTextColor(ffTest, panelData.getPanelBool("bfftest"));
        if(panelData.containsKey("bpertest"))
            putTextColor(peristalticTest, panelData.getPanelBool("bpertest"));
        if(panelData.containsKey("balrmltch"))
            putTextColor(alarmLatch, panelData.getPanelBool("balrmltch"));
        if(panelData.containsKey("balarm"))
            putTextColor(alarm, !panelData.getPanelBool("balarm"));
        if(panelData.containsKey("bLow"))
            putTextColor(lowProbe, panelData.getPanelBool("bLow"));
        if(panelData.containsKey("bairalrm"))
            putTextColor(airAlarm, !panelData.getPanelBool("bairalrm"));
        /* Variables */
        if(panelData.containsKey("dosesday"))
            dosesDay.setText(String.format("Dose Setting per Day (Field Dose):%s", panelData.getPanelString("dosesday")));
        if(panelData.containsKey("fdrun"))
            FdRunTime.setText(String.format("Pump Run Time (Field Dose):%s", panelData.getPanelString("fdrun")));
        if(panelData.containsKey("rrepeat"))
            recirRepeatTime.setText(String.format("Water Pump Recir Repeat Cycle Timer: %s", panelData.getPanelString("rrepeat")));
        if(panelData.containsKey("rrun"))
            recirRunTime.setText(String.format("Water Pump Recirc Run Timer: %s", panelData.getPanelString("rrun")));
        if(panelData.containsKey("effstat"))
            effStatus.setText(String.format("Effuent Pump Status :%s", panelData.getPanelString("effstat")));
        if(panelData.containsKey("airpres"))
            airPressure.setText(String.format("Air Compressor Pressure WCI: %s", panelData.getPanelString ("airpres")));
        if(panelData.containsKey("palmtime"))
            effPumpAlarmTime.setText(String.format("Effluent Pump Runtime Alarm Timer %s", panelData.getPanelString("palmtime")));
        if(panelData.containsKey("zone"))
            numberZones.setText(String.format("Number of Zones %s", panelData.getPanelString("zone")));
        if(panelData.containsKey("dow"))
            dosesDay.setText(String.format("%s", panelData.getPanelString("panelData.getPanel(\"dow\")")));
        if(panelData.containsKey(""))
            dosesDay.setText(String.format("%s", panelData.getPanelString("panelData.getPanel(\"\")")));
        if (panelData.containsKey("dow"))
            remoteDow = panelData.getPanelString("dow");
        if (panelData.containsKey("day"))
            remoteDay = panelData.getPanelString("day");
        if (panelData.containsKey("month"))
            remoteMonth = panelData.getPanelString("month");
        if (panelData.containsKey("year"))
            remoteYear = panelData.getPanelString("year");
        if (panelData.containsKey("hrs"))
            remoteHr = panelData.getPanelString("hrs");
        if (panelData.containsKey("min"))
            remoteMin = panelData.getPanelString("min");
        if (panelData.containsKey("sec"))
            remoteSec = panelData.getPanelString("sec");
        //timeRemote.setText(updateTime(remoteHr, remoteMin, remoteSec));
    }
    public void modeEnable(RadioGroup main_mode) {
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
    public void showTankPopUp() {
        DialogFragment newFragment = new PopUpFragment();
        assert getFragmentManager() != null;
        newFragment.show(getFragmentManager(), "tank");
    }
    private void setTextViewFlavor(TextView textview, String value) {
        if (value.equalsIgnoreCase("true")) {
            textview.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
            textview.setTextColor(Color.BLACK);
        }
        else {
            textview.setBackgroundColor(Color.BLACK);
            textview.setTextColor(Color.WHITE);
        }
    }
    private boolean check5lTime() {
        Calendar rightNow = Calendar.getInstance();
        int hour = rightNow.get(Calendar.HOUR_OF_DAY);

        sendJson("hour", String.valueOf(hour));

        int minute = rightNow.get(Calendar.MINUTE);
        sendJson("min", String.valueOf(minute));
        int second = rightNow.get(Calendar.SECOND);
        sendJson("sec", String.valueOf(second));
        int month = rightNow.get(Calendar.DAY_OF_MONTH);
        sendJson("month", String.valueOf(month));
        int day = rightNow.get(Calendar.DAY_OF_MONTH);
        sendJson("day", String.valueOf(day));
        int year = rightNow.get(Calendar.YEAR);
        sendJson("year", String.valueOf(year));
        return true;
    }
    private SpannableStringBuilder localTime(int remoteHr, int remoteMin, int remoteSec){
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
    }
    private void effPumpTestCallback() {
        if(panelData.getPanelBool("bptest")) {
            effPumpTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("bptest", "false");
        }
        else {
            effPumpTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
            sendJson("bptest", "true");
        }
    }
    private void alarmResetCallback() {
        if(panelData.getPanelBool("balmrset")) {
            alarmReset.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("balmrset", "false");
        }
        else {
            alarmReset.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
            sendJson("balmrset", "true");
        }
    }
    private void alarmHistoryCallback() {
        if(panelData.getPanelBool("alarmHistory")) {
            alarmHistory.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("ahist", "false");
        }
        else {
            alarmHistory.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
            sendJson("ahist", "true");
        }
    }
    private void ffTestCallback() {
        if(panelData.getPanelBool("bfftest")) {
            ffTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("bfftest", "false");
        }
        else {
            ffTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
            sendJson("bfftest", "true");
        }
    }
    private void manualTestCallback() {
        if(panelData.getPanelBool("bmantest")) {
            manualTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("bmantest", "false");
        }
        else {
            manualTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
            sendJson("bmantest", "true");
        }
    }
    private void peristalticTestCallback() {
        if(panelData.getPanelBool("bpertest")) {
            peristalticTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("bpertest", "false");
        }
        else {
            peristalticTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
            sendJson("bpertest", "true");
        }
    }
    private void recirTestCallback() {
        if(panelData.getPanelBool("brtest")) {
            recirTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("brtest", "false");
        }
        else {
            recirTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
            sendJson("brtest", "true");
        }
    }
    private boolean sendJson(String cmd, String value) {
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
    private void send(String str) {
        if(!connected) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
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
    private void read() {
        if(!connected) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] buffer = new byte[8192];
            int len = usbSerialPort.read(buffer, READ_WAIT_MILLIS);
/*            if(len == -1)
                msgAck = false;
            else
                msgAck = true;*/
            receive(Arrays.copyOf(buffer, len));
        } catch (IOException e) {
            // when using read with timeout, USB bulkTransfer returns -1 on timeout _and_ errors
            // like connection loss, so there is typically no exception thrown here on error
            status("connection lost: " + e.getMessage());
            disconnect();
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
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.yellow)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        //receiveText.append(spn);
    }
}
