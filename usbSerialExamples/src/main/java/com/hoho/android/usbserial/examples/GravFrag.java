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
import android.widget.PopupWindow;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class GravFrag extends Fragment implements SerialInputOutputManager.Listener, AdapterView.OnItemSelectedListener {

    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;
    private static final int UPDATE_INTERVAL_MILLIS = 50;
    private int deviceId, portNum, baudRate;
    private boolean withIoManager;
    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;
    private final boolean UiMessageSent = false;
    //Handler timerHandler;
    //String currentDateTimeString = java.text.DateFormat.getDateTimeInstance().format(new Date());
    private TextView receiveText;
    public PanelData panelData;

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
    //public TextView alarmLatchStatus;

    public Button systemOk;
    public Button effPumpTest;
    public Button alarm;
    public Button alarmLatch;
    public Button alarmHistory;
    public Button ffTest;
    public Button recirTest;
    public Button alarmReset;
    public Button manualTest;
    public Button lowProbe;
    public Button airAlarm;
    public Button peristalticTest;
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
    PopupWindow popupManualTest;
    //Button showPopupBtn, closePopupBtn;
    /*  List of data layer commands to process
     *   command index keeps trck of next command to send
     *   command lenght is length of commandList
     */
    public List<String> updateCommandList = of(
            "mode", "time",
            "tank", "bok", "bptest", "balmrset",
            "so1", "so0", "so2",
            "dosesday", "fdrun", "rrepeat",
            "rrun", "effstat", "airpres",
            "palmtime", "zone", "balrmltch",
            "bmantest", "bAlarm", "bLow", "bHigh", "bwater", "bairalrm"
    );                                                  /* dont need bmantest? */
    public int commandLength = updateCommandList.size();
    public int commandListIndex = 0;
    public String priorityCommandValue;
    public String priorityCommand;
    public boolean priorityCommandEnabled;

    public GravFrag() {
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
            if (panelData != null && panelData.containsKey("time"))
                timeRemote.setText(panelData.getPanelString("time"));
            mainLooper.postDelayed(timeHandler, 1000);
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
    @SuppressWarnings("deprecation")
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
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requireActivity().registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB), Context.RECEIVER_NOT_EXPORTED);
        } else {
            getActivity().registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));
        }

        if(usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted)
            mainLooper.post(this::connect);
    }
    @Override
    public void onPause() {
        mainLooper.removeCallbacks(postMsg);
        if (popupManualTest != null && popupManualTest.isShowing()) {
            sendJson("bENA", "false");
            popupManualTest.dismiss();
        }
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
        View view = inflater.inflate(R.layout.grav_frag, container, false);
        PopUpFragment popUpFragment;
        // Banner TextViews
        timeRemote = view.findViewById(R.id.timeRemote);
        systemOk = view.findViewById(R.id.systemOk);
        alarm = view.findViewById(R.id.alarm);
        alarmReset = view.findViewById(R.id.alarmReset);
        alarmReset.setOnClickListener(v -> alarmResetCallback());
        alarmLatch = view.findViewById(R.id.alarmLatch);
        alarmLatch.setOnClickListener(v -> alarmLatchCallback());
        // Body objects
        // alarmLatchStatus = view.findViewById(R.id.alarmLatchStatus);
        alarmHistory = view.findViewById(R.id.alarmHistory);
        alarmHistory.setOnClickListener(v ->
            AlarmHistoryPopup.show(getContext(), view, panelData,
                () -> sendJson("log", "query"),
                () -> sendJson("clrlog", "query")));
        manualTest = view.findViewById(R.id.manualTest);
        manualTest.setOnClickListener(v -> manualTestCallback());
        airAlarm = view.findViewById(R.id.airAlarm );
        lowProbe = view.findViewById(R.id.lowProbe);
        airPressure = view.findViewById(R.id.airPressure);
        // Start Update timer to sync UI
        mainLooper.postDelayed(update, UPDATE_INTERVAL_MILLIS);
        panelData = new ViewModelProvider(requireActivity()).get(PanelViewModel.class).panelData;
        return view;
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @SuppressWarnings("deprecation")
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
                    spn.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
        } else {
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
        }
    }
    private void flashRedAlarmTextColor(TextView tv, boolean value) {
        if (value) {
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.RedAlarmBackground));
        } else {
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
        }
    }
    private void putRedAlarmTextColor(TextView tv, boolean value) {
        if (value) {
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
        } else {
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.RedAlarmBackground));
        }
    }
    private void putWaterLevelText(TextView tv, boolean value) {
        if (value) {
            lowProbe.setText("Water Level Alarm");
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.WaterLevelBackground));
        } else {
            lowProbe.setText("Water Level Good");
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
        }
    }
    private void putLowWaterText(TextView tv, boolean value) {
        if (value) {
            lowProbe.setText("Water Level Low");
            tv.setTextColor(Color.YELLOW);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.RedAlarmBackground));
        } else {
            lowProbe.setText("Water Level Good");
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
        }
    }
    private void putHighWaterText(TextView tv, boolean value) {
        if (value) {
            lowProbe.setText("Water Level High");
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.RedAlarmBackground));
            tv.setTextColor(Color.BLUE);
        } else {
            lowProbe.setText("Water Level Good");
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
        }
    }
    public void postDataLayer() {                           // Convert string to bool and update UI with command
        if (!isAdded() || getContext() == null) return;
        // boolean enableMode;
        /* Status Banner */
        if (panelData.containsKey("bok")) {
            putRedAlarmTextColor(systemOk, panelData.getPanelBool("bok"));
            flashRedAlarmTextColor(alarm, !panelData.getPanelBool("bok"));
        }
        if(panelData.containsKey("balmrset"))
            putTextColor(alarmReset, panelData.getPanelBool("balmrset"));
        if(panelData.containsKey("balrmltch"))
            putTextColor(alarmLatch, panelData.getPanelBool("balrmltch"));
        if (panelData.containsKey("bwater"))
            if (panelData.getPanelBool("bAlarm"))
                putWaterLevelText(lowProbe, true);
            else if (panelData.getPanelBool("bHigh"))
                putHighWaterText(lowProbe, true);
            else if (panelData.getPanelBool("bLow"))
                putLowWaterText(lowProbe, false);
            else
                putLowWaterText(lowProbe, true);
        if(panelData.containsKey("bairalrm"))
            putRedAlarmTextColor(airAlarm, !panelData.getPanelBool("bairalrm"));
        /* Variables */
        if(panelData.containsKey("airpres"))
            airPressure.setText(String.format("Air Compressor Pressure WCI: %s", panelData.getPanelString ("airpres")));
        if (panelData.containsKey("time"))
            timeRemote.setText(panelData.getPanelString("time"));
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
            if (priorityCommandEnabled) {
                sendJson(priorityCommand, priorityCommandValue);
                priorityCommandEnabled = false;
            } else
                sendJson(updateCommandList.get(commandListIndex++), "Query");
            if (commandListIndex == commandLength)
                commandListIndex = 0;
        }
    }
    public void showTankPopUp() {
        DialogFragment newFragment = new PopUpFragment();
        assert getParentFragmentManager() != null;
        newFragment.show(getParentFragmentManager(), "tank");
    }
    private void setTextViewFlavor(TextView textview, String value) {
        if (value.equalsIgnoreCase("true")) {
            textview.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
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
            effPumpTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            sendJson("bptest", "true");
        }
    }
    private void alarmResetCallback() {
        if(panelData.getPanelBool("balmrset")) {
            alarmReset.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("balmrset", "false");
        }
        else {
            alarmReset.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            sendJson("balmrset", "true");
        }
    }
    private void alarmLatchCallback() {
        if (panelData.getPanelBool("balrmltch")) {
            alarmLatch.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendPriorityCommand("balrmltch", "false");
        } else {
            alarmLatch.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            sendPriorityCommand("balrmltch", "true");
        }
    }

    private void ffTestCallback() {
        if(panelData.getPanelBool("so0")) {
            ffTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("so0", "false");
        }
        else {
            ffTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            sendJson("so0", "true");
        }
    }
    private void manualTestCallback() {
        popupManualTest = ManualInputTestHelper.show(this, requireView(), manualTest, panelData, this::sendJson);
    }
    private void peristalticTestCallback() {
        if(panelData.getPanelBool("so2")) {
            peristalticTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("so2", "false");
        }
        else {
            peristalticTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            sendJson("so2", "true");
        }
    }
    private void recirTestCallback() {
        if(panelData.getPanelBool("so1")) {
            recirTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("so1", "false");
        }
        else {
            recirTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            sendJson("so1", "true");
        }
    }
    public void sendPriorityCommand(String pCmd, String pValue) {
        priorityCommandEnabled = true;
        priorityCommand = pCmd;
        priorityCommandValue = pValue;
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
            spn.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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
        if(data.length > 0) {
            panelData.parse(data, () -> mainLooper.post(postMsg));
        }
    }
    void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
        spn.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.yellow)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        //receiveText.append(spn);
    }
}
