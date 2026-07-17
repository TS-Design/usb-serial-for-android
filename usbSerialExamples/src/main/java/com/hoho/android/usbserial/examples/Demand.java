package com.hoho.android.usbserial.examples;

import static java.lang.Integer.parseInt;
import static java.util.List.of;

import android.app.Activity;
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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
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

import com.hoho.android.usbserial.BuildConfig;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class Demand extends Fragment implements SerialInputOutputManager.Listener, AdapterView.OnItemSelectedListener {

    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.LIBRARY_PACKAGE_NAME  + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;
    private static final int UPDATE_INTERVAL_MILLIS = 100;
    private int deviceId, portNum, baudRate;
    private boolean withIoManager;
    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;
    private TextView flashAlarmView = null;
    private boolean flashAlarmPhase = false;
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
    public String priorityCommandValue;
    public String priorityCommand;
    public boolean priorityCommandEnabled;
    public boolean connected = false;
    //public DataLayer dataLayer = new DataLayer();
    /* Hoot Fragment adds */
    //static boolean cmd_busy = false;
    private EditText demandAlarmTime;
    private TextView dosesDay;
    private TextView effStatus;
    private TextView FdRunTime;
    private TextView recirRepeatTime;
    private TextView recirRunTime;
    private TextView airPressure;
    private TextView effPumpAlarmTime;
    private TextView numberZones;
    private TextView flowData;
    private TextView alarmLatchStatus;
    private TextView hourTotalValue;
    private TextView hourlyAverageValue;
    private TextView dailyTotalValue;
    private TextView thirtyDayTotalValue;
    private TextView thirtyDayTotalAverageValue;
    private TextView lifetimeDaysValue;
    private TextView lifetimeValue;
    private TextView lifetimeDaysAverageValue;

    public Button systemOk;
    public Button effPumpTest;
    public Button alarmLatch;
    public Button alarmHistory;
    public Button alarmProbe;
    public Button ffTest;
    public Button recirTest;
    public Button alarmReset;
    public Button manualTest;
    public Button alarm;
    public Button waterAlarm;
    public Button airAlarm;
    public Button peristalticTest;
    private Button effstat;
    private Button manual;
    private Button demandTimer;
    private Button closePopupBtn;
    private Button closeAlarmBtn;
    private Button closeGallonsBtn;
    private TextView gallontextwindow;
    private TextView alarmTextWindow;
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
    PopupWindow popupWindow;
    PopupWindow popupManualTest;
    //LinearLayout linearLayout1;


    //Button showPopupBtn, closePopupBtn;
    /*  List of data layer commands to process
     *   command index keeps trck of next command to send
     *   command lenght is length of commandList
     */
    public List<String> updateCommandList = of(
            "mode",
            "time",
            "bok","bwater", "bAlarm", "bLow", "bHigh", "bairalrm", "balrmltch",
            "tank",
            "airpres",
            "palmtime",             // pump runtime alarm
            "dalrmtime",            // demand runtime befor alarm
            "bptest",
            "balmrset",
            "bmantest",
            "so0",
            "so1",
            "so2",
            "flow",                 // metering command get all
            "dosesday",
            "fdrun",
            "rrepeat",
            "rrun",
            "effstat"
    );                                                  /* dont need bmantest? */
    public int commandLength = updateCommandList.size();
    public int commandListIndex = 0;

    public Demand() {
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
    final Runnable modeSpinner = new Runnable() {
        @Override
        public void run() {

            //Toast.makeText(getActivity(), "modeSpinner  " + dataLayer.getTank(), Toast.LENGTH_SHORT).show();
            demandAlarmTime.setText(panelData.getPanelString("dalrmtime"));
        }
    };
    final Runnable update5L = () -> {
        Toast.makeText(getActivity(), "Send Panel Demand Alarm " + panelData.getPanelString("balrmtime"), Toast.LENGTH_SHORT).show();
        sendPriorityCommand("dalrmtime", panelData.getPanelString("dalrmtime"));
    };
    final Runnable update = () -> {
        //Toast.makeText(getActivity(), "Update ", Toast.LENGTH_SHORT).show();
        getPanelStatus();
    };
    final Runnable postMsg = () -> {
        postDataLayer();

        //Toast.makeText(getActivity(), "HID Timeout", Toast.LENGTH_SHORT).show();
    };
    private final Runnable flashAlarmRunnable = new Runnable() {
        @Override
        public void run() {
            if (flashAlarmView == null) return;
            flashAlarmPhase = !flashAlarmPhase;
            int color = flashAlarmPhase
                    ? ContextCompat.getColor(requireContext(), R.color.RedAlarmBackground)
                    : ContextCompat.getColor(requireContext(), R.color.textGoodBackground);
            flashAlarmView.setBackgroundColor(color);
            mainLooper.postDelayed(flashAlarmRunnable, 1000);
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
    @Override
    public void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requireActivity().registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB), Context.RECEIVER_NOT_EXPORTED);
        } else {
            ContextCompat.registerReceiver(requireActivity(), broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB), ContextCompat.RECEIVER_NOT_EXPORTED);
        }
        Toast.makeText(getActivity(), "onResume Term", Toast.LENGTH_SHORT).show();
        if(usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted)
            mainLooper.post(this::connect);
    }
    @Override
    public void onPause() {
        mainLooper.removeCallbacks(postMsg);
        mainLooper.removeCallbacks(flashAlarmRunnable);
        flashAlarmView = null;
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
        View view = inflater.inflate(R.layout.demandfrag, container, false);
        PopUpFragment popUpFragment;
        systemOk = view.findViewById(R.id.systemOk);
        systemOk.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
        systemOk.setTextColor(Color.BLACK);
        alarmLatch = view.findViewById(R.id.alarmLatch);
        alarmLatch.setOnClickListener(v -> alarmLatchCallback());
        alarm = view.findViewById(R.id.alarm);
        alarm.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
        alarm.setTextColor(Color.BLACK);
        alarmHistory = view.findViewById(R.id.alarmHistory);
        timeRemote = view.findViewById(R.id.timeRemote);
        airAlarm = view.findViewById(R.id.airAlarm );
        //alarmProbe = view.findViewById(R.id.alarmProbe);
        alarmReset = view.findViewById(R.id.alarmReset);
        alarmReset.setOnClickListener(v -> alarmResetCallback());
        airPressure = view.findViewById(R.id.airPressure);
        waterAlarm = view.findViewById(R.id.waterAlarm);
        waterAlarm.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
        waterAlarm.setTextColor(Color.BLACK);
        manual = view.findViewById(R.id.manual);
        //alarmLatchStatus = view.findViewById(R.id.alarmLatchStatus);
        demandTimer = view.findViewById(R.id.demandTimer);
        flowData = view.findViewById(R.id.flowData);
        //flowData.setOnClickListener(v-> flowDataCallback());
        //flowData.setOnClickListener(new R.id(flowData));
        effstat = view.findViewById(R.id.effstat);  // button
        effStatus = view.findViewById(R.id.effStatus);  // TextView
        effPumpAlarmTime = view.findViewById(R.id.effPumpAlarmTime);
        // dropdowns
        demandAlarmTime = view.findViewById(R.id.demandAlarmTime);
        demandAlarmTime.setOnEditorActionListener((v, actionId, event) -> {
            boolean handled = false;
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                try {
                    int dalrmtime = parseInt(demandAlarmTime.getText().toString());
                    sendPriorityCommand("dalrmtime", Integer.toString(dalrmtime));
                } catch (NumberFormatException e) {
                    return false;
                }
                ((InputMethodManager) getContext().getSystemService(Activity.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(requireView().getWindowToken(), 0);
                demandAlarmTime.clearFocus();
                handled = true;
            }
            return handled;
        });
        //linearLayout1 = (LinearLayout) findViewById(R.id.linearLayout1);

        flowData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //instantiate the popup.xml layout file
                LayoutInflater layoutInflater = (LayoutInflater) Demand.this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View customView = layoutInflater.inflate(R.layout.gallons_popup, null);

                closeGallonsBtn = customView.findViewById(R.id.closeGallonsBtn);
                hourTotalValue = customView.findViewById(R.id.hourTotalValue);
                hourlyAverageValue = customView.findViewById(R.id.hourlyAverageValue);
                dailyTotalValue = customView.findViewById(R.id.dailyTotalValue);
                thirtyDayTotalValue = customView.findViewById(R.id.thirtyDayTotalValue);
                thirtyDayTotalAverageValue = customView.findViewById(R.id.thirtyDayTotalAverageValue);
                lifetimeValue = customView.findViewById(R.id.lifetimeValue);
                lifetimeDaysAverageValue = customView.findViewById(R.id.lifetimeDaysAverageValue);
                lifetimeDaysValue = customView.findViewById(R.id.lifetimeDaysValue);
                //instantiate popup window
                popupWindow = new PopupWindow(customView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                //display the popup window
                popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);
                hourTotalValue.setText(panelData.getPanelString("hours1"));
                hourlyAverageValue.setText(panelData.getPanelString("hours24"));
                dailyTotalValue.setText(panelData.getPanelString("day30"));
                thirtyDayTotalValue.setText(panelData.getPanelString("day30Avg"));
                thirtyDayTotalAverageValue.setText(panelData.getPanelString("hours1"));
                lifetimeDaysValue.setText(panelData.getPanelString("lifetimeValue"));
                lifetimeValue.setText(panelData.getPanelString("lifeTime"));
                lifetimeDaysAverageValue.setText(panelData.getPanelString("lifetimeAvg"));
                //close the popup window on button click
                closeGallonsBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        popupWindow.dismiss();
                    }
                });
            }
        });
        alarmHistory.setOnClickListener(v ->
            AlarmHistoryPopup.show(getContext(), view, panelData,
                () -> sendPriorityCommand("log", "query"),
                () -> sendPriorityCommand("clrlog", "query")));
        manualTest = view.findViewById(R.id.manualTest);
        manualTest.setOnClickListener(v -> manualTestCallback());
        /* Start Update timer to sync UI   */
        mainLooper.postDelayed(update, UPDATE_INTERVAL_MILLIS);
        panelData = new ViewModelProvider(requireActivity()).get(PanelViewModel.class).panelData;
        return view;
    }
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
    }
    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
        Toast.makeText(getActivity(), "Spinner Outside Nothing", Toast.LENGTH_SHORT).show();
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
    private void putYellowAlarmTextColor(TextView tv, boolean value) {
        if (value) {
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
        } else {
            tv.setTextColor(Color.YELLOW);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.light_blue_A400));
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
    private void putRedAlarmTextColorFlash(TextView tv, boolean value) {
        if (value) {
            if (flashAlarmView != tv) {
                mainLooper.removeCallbacks(flashAlarmRunnable);
                flashAlarmView = tv;
                flashAlarmPhase = true;
                tv.setTextColor(Color.BLACK);
                tv.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.RedAlarmBackground));
                mainLooper.postDelayed(flashAlarmRunnable, 1000);
            }
        } else {
            mainLooper.removeCallbacks(flashAlarmRunnable);
            flashAlarmView = null;
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.textGoodBackground));
        }
    }
    private void putWaterLevelTextColor(TextView tv, boolean value) {
        if (value) {
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
        } else {
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.WaterLevelBackground));
        }
    }
    private void putWaterLevelText(TextView tv, boolean value) {
        if (value) {
            waterAlarm.setText("Water Level Alarm");
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.WaterLevelBackground));
        } else {
            waterAlarm.setText("Water Level Good");
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
        }
    }
    private void putLowWaterText(TextView tv, boolean value) {
        if (value) {
            waterAlarm.setText("Water Level Low");
            tv.setTextColor(Color.YELLOW);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.RedAlarmBackground));
        } else {
            waterAlarm.setText("Water Level Good");
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
        }
    }
    private void putHighWaterText(TextView tv, boolean value) {
        if (value) {
            waterAlarm.setText("Water Level High");
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.RedAlarmBackground));
            tv.setTextColor(Color.BLUE);
        } else {
            waterAlarm.setText("Water Level Good");
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
        }
    }
    public void postDataLayer() {                           // Convert string to bool and update UI with command
        if (!isAdded() || getContext() == null) return;
        boolean enableMode;
        /* Status Banner */
        if(panelData.containsKey("bok")) {
            putRedAlarmTextColor(systemOk, panelData.getPanelBool("bok"));
            }
        if (panelData.containsKey("bAlarm")) {
            putRedAlarmTextColorFlash(alarm, panelData.getPanelBool("bAlarm"));
        }
        if(panelData.containsKey("balmrset"))
            putTextColor(alarmReset, panelData.getPanelBool("balmrset"));
        if(panelData.containsKey("balrmltch"))
            putTextColor(alarmLatch, panelData.getPanelBool("balrmltch"));
//        if (panelData.containsKey("bLow") || panelData.containsKey("bHigh")) {
        if (panelData.containsKey("bwater"))   //  Water Alarm Button
            if (panelData.getPanelBool("bAlarm"))
                putWaterLevelText(waterAlarm, true);
            else if (panelData.getPanelBool("bHigh"))
                putHighWaterText(waterAlarm, true);
            else if (panelData.getPanelBool("bLow"))
                putLowWaterText(waterAlarm, false);
            else
                putLowWaterText(waterAlarm, true);
        if(panelData.containsKey("bairalrm"))
            putRedAlarmTextColor(airAlarm, !panelData.getPanelBool("bairalrm"));
        /* Variables */
        if(panelData.containsKey("airpres"))
            airPressure.setText(String.format("Air Compressor Pressure WCI: %s", panelData.getPanelString ("airpres")));
        if(panelData.containsKey("palmtime"))
            effPumpAlarmTime.setText("Effluent Pump Runtime Alarm Timer");
        if(panelData.containsKey("effstat")) {
            effStatus.setText(String.format("Effuent Pump Status :%s", panelData.getPanelString("effstat")));
            putTextColor(effstat, panelData.getPanelBool("effstat"));
        }
        if (panelData.containsKey("airpres"))
            airPressure.setText(String.format("Air Compressor Pressure WCI:               %s", panelData.getPanelString("airpres")));
        if (panelData.containsKey("dalrmtime") && !demandAlarmTime.hasFocus()) {
            if (panelData.getPanelString("dalrmtime").contentEquals(""))
                demandAlarmTime.setText(String.format("%d", 0));
            else
                demandAlarmTime.setText(panelData.getPanelString("dalrmtime"));
        }
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
            if (priorityCommandEnabled == true) {
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

        sendPriorityCommand("hour", String.valueOf(hour));

        int minute = rightNow.get(Calendar.MINUTE);
        sendPriorityCommand("min", String.valueOf(minute));
        int second = rightNow.get(Calendar.SECOND);
        sendPriorityCommand("sec", String.valueOf(second));
        int month = rightNow.get(Calendar.DAY_OF_MONTH);
        sendPriorityCommand("month", String.valueOf(month));
        int day = rightNow.get(Calendar.DAY_OF_MONTH);
        sendPriorityCommand("day", String.valueOf(day));
        int year = rightNow.get(Calendar.YEAR);
        sendPriorityCommand("year", String.valueOf(year));
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
    private void flowDataCallback() {

    }
    private void effPumpTestCallback() {
        if(panelData.getPanelBool("bptest")) {
            effPumpTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendPriorityCommand("bptest", "false");
        }
        else {
            effPumpTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            sendPriorityCommand("bptest", "true");
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

    private void alarmResetCallback() {
        if(panelData.getPanelBool("balmrset")) {
            alarmReset.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendPriorityCommand("balmrset", "false");
        }
        else {
            alarmReset.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            sendPriorityCommand("balmrset", "true");
        }
    }
    private void alarmHistoryCallback() {
        if(panelData.getPanelBool("alarmHistory")) {
            alarmHistory.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendPriorityCommand("ahist", "false");
        }
        else {
            alarmHistory.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            sendPriorityCommand("ahist", "true");
        }
    }
    private void ffTestCallback() {
        if(panelData.getPanelBool("so0")) {
            ffTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendPriorityCommand("so0", "false");
        }
        else {
            ffTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            sendPriorityCommand("so0", "true");
        }
    }
    private void manualTestCallback() {
        popupManualTest = ManualInputTestHelper.show(this, requireView(), manualTest, panelData, this::sendPriorityCommand);
    }
    private void peristalticTestCallback() {
        if(panelData.getPanelBool("so2")) {
            peristalticTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendPriorityCommand("so2", "false");
        }
        else {
            peristalticTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            sendPriorityCommand("so2", "true");
        }
    }
    private void recirTestCallback() {
        if(panelData.getPanelBool("so1")) {
            recirTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendPriorityCommand("so1", "false");
        }
        else {
            recirTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            sendPriorityCommand("so1", "true");
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
