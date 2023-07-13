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
import java.util.Objects;

public class AnrFragment extends Fragment implements SerialInputOutputManager.Listener, AdapterView.OnItemSelectedListener {

    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;
    private static final int UPDATE_INTERVAL_MILLIS = 10;
    private int deviceId, portNum, baudRate;
    private boolean withIoManager;
    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;
    private final boolean UiMessageSent = false;
    //Handler timerHandler;
    //String currentDateTimeString = java.text.DateFormat.getDateTimeInstance().format(new Date());
    private TextView receiveText;
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
    private TextView timeRemote;
    //private TextView remoteTime;
    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    public boolean connected = false;
    public DataLayer dataLayer = new DataLayer();
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
            "dosesday", "fdrun", "rrepeat",
            "rrun", "effstat", "airpres",
            "palmtime", "zone", "balrmltch",
            "bmantest", "balarm", "bLow", "bairalrm"
    );                                                  /* dont need bmantest? */
    public int commandLength = updateCommandList.size();
    public int commandListIndex = 0;

    public AnrFragment() {
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
        timeRemote.setText(dataLayer.getTime());
        mainLooper.postDelayed(timeHandler,1000);
        }
    };
    final Runnable waitOnTank = new Runnable() {
        @Override
        public void run() {
            if(dataLayer.getTank().equals("0")) {
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
            Toast.makeText(getActivity(), "Send Tank " + dataLayer.getTank(), Toast.LENGTH_SHORT).show();
            sendJson("tank", dataLayer.getTank());
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
        View view = inflater.inflate(R.layout.anr_fragment, container, false);
        PopUpFragment popUpFragment;
        systemOk = view.findViewById(R.id.systemOk);
        numberZones = view.findViewById(R.id.numberZones);
        alarmLatch = view.findViewById(R.id.alarmLatch);
        FdRunTime = view.findViewById(R.id.FdRunTime);
        recirRepeatTime = view.findViewById(R.id.recirRepeatTime);
        dosesDay = view.findViewById(R.id.dosesDay);
        recirRunTime = view.findViewById(R.id.recirRunTime);
        receiveText = view.findViewById(R.id.receiveText);
        alarmHistory = view.findViewById(R.id.alarmHistory);
        alarmHistory.setOnClickListener(v -> alarmHistoryCallback());
        recirTest = view.findViewById(R.id.recirTest);
        recirTest.setOnClickListener(v -> recirTestCallback());
        ffTest = view.findViewById(R.id.ffTest);
        ffTest.setOnClickListener(v -> ffTestCallback());
        peristalticTest = view.findViewById(R.id.peristalticTest);
        peristalticTest.setOnClickListener(v -> peristalticTestCallback());
        effPumpTest = view.findViewById(R.id.effPumpTest);
        effPumpTest.setOnClickListener(v -> effPumpTestCallback());
        effPumpAlarmTime = view.findViewById(R.id.effPumpAlarmTime);
        effStatus = view.findViewById(R.id.effStatus);
        manualTest = view.findViewById(R.id.manualTest);
        manualTest.setOnClickListener(v -> manualTestCallback());
        timeRemote = view.findViewById(R.id.timeRemote);
        airAlarm = view.findViewById(R.id.airAlarm );
        alarm = view.findViewById(R.id.alarm);
        alarmReset = view.findViewById(R.id.alarmReset);
        alarmReset.setOnClickListener(v -> alarmResetCallback());
        airPressure = view.findViewById(R.id.airPressure);
        lowProbe = view.findViewById(R.id.lowProbe);

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
    public void postDataLayer() {
        boolean enableMode;

       if ((dataLayer.getKEY()).equals("bok")) {
           dataLayer.bok = Boolean.parseBoolean(dataLayer.getVALUE());
           if (dataLayer.isBok()) {
               systemOk.setTextColor(Color.BLACK);
               systemOk.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
           } else {
               systemOk.setTextColor(Color.WHITE);
               systemOk.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
           }
       }
       else if((dataLayer.getKEY()).equals("bptest")) {
           dataLayer.bptest = Boolean.parseBoolean(dataLayer.getVALUE());
           if (dataLayer.bptest) {
               effPumpTest.setTextColor(Color.BLACK);
               effPumpTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
           } else {
               effPumpTest.setTextColor(Color.BLACK);
               effPumpTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
           }
       }
       else if((dataLayer.getKEY()).equals("balmrset")) {
           dataLayer.balmrset = Boolean.parseBoolean(dataLayer.getVALUE());
           if (dataLayer.balmrset) {
               alarmReset.setTextColor(Color.BLACK);
               alarmReset.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
           } else {
               alarmReset.setTextColor(Color.BLACK);
               alarmReset.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
           }
       }
       else if((dataLayer.getKEY()).equals("brtest")) {
           dataLayer.brtest = Boolean.parseBoolean(dataLayer.getVALUE());
           if (dataLayer.brtest) {
               recirTest.setTextColor(Color.BLACK);
               recirTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
           } else {
               recirTest.setTextColor(Color.BLACK);
               recirTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
           }
       }
       else if((dataLayer.getKEY()).equals("bfftest")) {
           dataLayer.bfftest = Boolean.parseBoolean(dataLayer.getVALUE());
           if (dataLayer.bfftest) {
               ffTest.setTextColor(Color.BLACK);
               ffTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
           } else {
               ffTest.setTextColor(Color.BLACK);
               ffTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
           }
       }
       else if((dataLayer.getKEY()).equals("bpertest")) {
           dataLayer.bpertest = Boolean.parseBoolean(dataLayer.getVALUE());
           if (dataLayer.bpertest) {
               peristalticTest.setTextColor(Color.WHITE);
               peristalticTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
           } else {
               peristalticTest.setTextColor(Color.BLACK);
               peristalticTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
           }
       }
       else if((dataLayer.getKEY()).equals("balrmltch")) {
           dataLayer.balrmltch = Boolean.parseBoolean(dataLayer.getVALUE());
           if (dataLayer.balrmltch) {
               alarmLatch.setTextColor(Color.BLACK);
               alarmLatch.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
           } else {
               alarmLatch.setTextColor(Color.BLACK);
               alarmLatch.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
           }
       }
       else if((dataLayer.getKEY()).equals("bmantest")) {
           dataLayer.bmantest = Boolean.parseBoolean(dataLayer.getVALUE());
           if (dataLayer.bmantest) {
               manualTest.setTextColor(Color.BLACK);
               manualTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
           } else {
               manualTest.setTextColor(Color.BLACK);
               manualTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
           }
       }
       else if((dataLayer.getKEY()).equals("balarm")) {
           dataLayer.balarm = Boolean.parseBoolean(dataLayer.getVALUE());
           if (!dataLayer.balarm) {
               alarm.setTextColor(Color.BLACK);
               alarm.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
           } else {
               alarm.setTextColor(Color.BLACK);
               alarm.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
           }
       }
       else if((dataLayer.getKEY()).equals("bLow")) {
           dataLayer.bLow = Boolean.parseBoolean(dataLayer.getVALUE());
           if (dataLayer.bLow) {
               lowProbe.setTextColor(Color.BLACK);
               lowProbe.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
           } else {
               lowProbe.setTextColor(Color.BLACK);
               lowProbe.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
           }
       }
       else if((dataLayer.getKEY()).equals("bairalrm")) {
           dataLayer.bairalrm = Boolean.parseBoolean(dataLayer.getVALUE());
           if (!dataLayer.bairalrm) {
               airAlarm.setTextColor(Color.BLACK);
               airAlarm.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
           } else {
               airAlarm.setTextColor(Color.BLACK);
               airAlarm.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
           }
       }
       else if((dataLayer.getKEY()).equals("dosesday")) {
           dataLayer.dosesday = dataLayer.getVALUE();
           dosesDay.setText("Dose Setting per Day (Field Dose):" + dataLayer.dosesday);
       }
       else if((dataLayer.getKEY()).equals("fdrun")) {
           dataLayer.fdrun = dataLayer.getVALUE();
           FdRunTime.setText("Pump Run Time (Field Dose):" + dataLayer.fdrun);
       }
       else if((dataLayer.getKEY()).equals("rrepeat")) {
           dataLayer.rrepeat = dataLayer.getVALUE();
           recirRepeatTime.setText("Water Pump Recir Repeat Cycle Timer: " + dataLayer.rrepeat);
       }
       else if((dataLayer.getKEY()).equals("rrun")) {
           dataLayer.rrun = dataLayer.getVALUE();
           recirRunTime.setText("Water Pump Recirc Run Timer: " + dataLayer.rrun);
       }
       else if((dataLayer.getKEY()).equals("dosesday")) {
           dataLayer.dosesday = dataLayer.getVALUE();
           dosesDay.setText("Dose Setting per Day (Field Dose): " + dataLayer.dosesday);
       }
       else if((dataLayer.getKEY()).equals("effstat")) {
           dataLayer.effstat = dataLayer.getVALUE();
           effStatus.setText("Effuent Pump Status " + dataLayer.effstat);
       }
       else if((dataLayer.getKEY()).equals("airpres")) {
           dataLayer.airpres = dataLayer.getVALUE();
           airPressure.setText("Air Compressor Pressure WCI: " + dataLayer.airpres);
       }
       else if((dataLayer.getKEY()).equals("palmtime")) {
           dataLayer.palmtime = dataLayer.getVALUE();
           effPumpAlarmTime.setText("Effluent Pump Runtime Alarm Timer " + dataLayer.palmtime);
       }
       else if((dataLayer.getKEY()).equals("zone")) {
           dataLayer.zone = dataLayer.getVALUE();
           numberZones.setText("Number of Zones " + dataLayer.zone);
       }
/*        else if ((dataLayer.getKEY()).equals("bAlarm"))
            dataLayer.setbAlarm(Boolean.parseBoolean(dataLayer.getVALUE()));
        else if ((dataLayer.getKEY()).equals("bLow"))
            dataLayer.setbLow(Boolean.parseBoolean(dataLayer.getVALUE()));
        else if ((dataLayer.getKEY()).equals("bHigh"))
            dataLayer.setbHigh(Boolean.parseBoolean(dataLayer.getVALUE()));
        else if ((dataLayer.getKEY()).equals("bWM"))
            dataLayer.setbWM(Boolean.parseBoolean(dataLayer.getVALUE()));
        if ((dataLayer.getKEY()).equals("mode")) {
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
        }*/
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
            //timeRemote.setText(updateTime(remoteHr, remoteMin, remoteSec));
        }
/*        else if ((dataLayer.getKEY()).equals("Tank")) {
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
       if ((dataLayer.getKEY()).equals("bALARM")) {
            // kill cmd not found msg until view is enabled
        }/*       else if ((dataLayer.getKEY()).equals("tank")) {
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
 /*   private void ackModeCmd(RadioButton activeButton){
        bgrav.setTextColor(Color.WHITE);
        banr.setTextColor(Color.WHITE);
        bbnr.setTextColor(Color.WHITE);
        bspy.setTextColor(Color.WHITE);
        bdrip.setTextColor(Color.WHITE);
        bdmd.setTextColor(Color.WHITE);
       if(activeButton == bgrav)
           bgrav.setTextColor(ContextCompat.getColor(getContext(), R.color.textOn));
       else if (activeButton == banr)
           banr.setTextColor(ContextCompat.getColor(getContext(), R.color.textOn));
       else if (activeButton == bbnr)
           bbnr.setTextColor(ContextCompat.getColor(getContext(), R.color.textOn));
       else if (activeButton == bspy)
           bspy.setTextColor(ContextCompat.getColor(getContext(), R.color.textOn));
       else if (activeButton == bdrip)
           bdrip.setTextColor(ContextCompat.getColor(getContext(), R.color.textOn));
       else if (activeButton == bdmd)
           bdmd.setTextColor(ContextCompat.getColor(getContext(), R.color.textOn));
    }*/
    private void effPumpTestCallback() {
        if(dataLayer.bptest) {
            effPumpTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("bptest", "false");
        }
        else {
            effPumpTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
            sendJson("bptest", "true");
        }
    }

    private void alarmResetCallback() {
            if(dataLayer.balmrset) {
                alarmReset.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
                sendJson("balmrset", "false");
            }
            else {
                alarmReset.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
                sendJson("balmrset", "true");
            }
    }
    private void alarmHistoryCallback() {
        if(dataLayer.alarmHistory) {
            alarmHistory.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("ahist", "false");
        }
        else {
            alarmHistory.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
            sendJson("ahist", "true");
        }
    }
    private void ffTestCallback() {
        if(dataLayer.bfftest) {
            ffTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("bfftest", "false");
        }
        else {
            ffTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
            sendJson("bfftest", "true");
        }
    }
    private void manualTestCallback() {
        if(dataLayer.bmantest) {
            manualTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("bmantest", "false");
        }
        else {
            manualTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
            sendJson("bmantest", "true");
        }
    }
    private void peristalticTestCallback() {
        if(dataLayer.bpertest) {
            peristalticTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendJson("bpertest", "false");
        }
        else {
            peristalticTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOn));
            sendJson("bpertest", "true");
        }
    }
    private void recirTestCallback() {
        if(dataLayer.brtest) {
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
/*        for (int k = 0; k < 30000; k++) {
            if (dataLayer.isMsgAck())
                break;
        }*/
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
        //String valueString = null;
        boolean key = false;
        boolean value = false;

        if(rx.length() > 0){
            for(int k = 0; k < rx.length(); k++){
                switch(rx.charAt(k)) {
                    case '{':                           // start case start key phase
                        key = true;
                        keyString = "";
                        break;
                    case '}':                           // save value and exit
                        key = false;
                        value = false;
                        dataLayer.setVALUE(keyString);
                        dataLayer.setValuebyKey();
                        keyString = "";
                        //receiveText.append(dataLayer.getKEY() + ":" + dataLayer.getVALUE()  + "\n");
                        //Toast.makeText(getActivity(), "ACK", Toast.LENGTH_SHORT).show();
                        //dataLayer.setMsgAck(true);
                        mainLooper.post(postMsg);
                        //mainLooper.post(() -> { postMsg; });
                        break;
                    case ':':                           // save key move to value phase
                        key = false;
                        dataLayer.setKEY(keyString);
                        value = true;
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
