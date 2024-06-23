package com.hoho.android.usbserial.examples;

import static android.os.Build.VERSION.SDK_INT;

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
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TerminalFragment extends Fragment implements SerialInputOutputManager.Listener, AdapterView.OnItemSelectedListener {

    public void setMain_mode(RadioGroup main_mode) {
        this.main_mode = main_mode;
    }
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
    public String priorityCommandValue;
    public String priorityCommand;
    public boolean priorityCommandEnabled;
    //Handler timerHandler;
    //String currentDateTimeString = java.text.DateFormat.getDateTimeInstance().format(new Date());
    private TextView receiveText;
    public PanelData panelData = new PanelData();

    private TextView timeRemote;
    private RadioGroup main_mode;
    private RadioButton binit;
    private RadioButton bgrav;
    private RadioButton banr;
    private RadioButton bbnr;
    private RadioButton bspy;
    private RadioButton bmicro;
    private RadioButton bdrip;
    private RadioButton bdmd;
    private RadioButton manual;
    private RadioButton cancelManual;
    private Spinner tankDropDown;
    //private TextView remoteTime;
    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    public boolean connected = false;
    /* Hoot adds */
    public String tankSizeString = "";
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
    public List<String> updateCommandList;
    {
        updateCommandList = new ArrayList<>();
        updateCommandList.add("mode");
        updateCommandList.add("time");
        updateCommandList.add("tank");
        updateCommandList.add("bmantest");
    }

    public int commandLength = updateCommandList.size();
    public int commandListIndex = 0;

    public TerminalFragment() {
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
        priorityCommandEnabled = false;
        priorityCommandValue = null;
        priorityCommandValue = null;
    }
    /*
     * Lifecycle
     */
    final Runnable timeHandler = new Runnable() {
        @Override
        public void run() {
            //String time = panelData.getPanelString("year") + "-" + panelData.getPanelString("month") + "-" + panelData.getPanelString("day") +" " + panelData.getPanelString("hrs") + ":" + panelData.getPanelString("min");
            timeRemote.setText(panelData.getPanelString("time"));
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
    final Runnable modeSpinner = new Runnable() {
        @Override
        public void run() {

            //Toast.makeText(getActivity(), "modeSpinner  " + dataLayer.getTank(), Toast.LENGTH_SHORT).show();
            // tankDropDown.setSelection(item);
            tankDropDown.setSelection(((ArrayAdapter)tankDropDown.getAdapter()).getPosition(panelData.getPanelString("tank")));
        }
    };
    final Runnable updateTank = new Runnable() {
        @Override
        public void run() {
            Toast.makeText(getActivity(), "Send Tank " + panelData.getPanelString("tank"), Toast.LENGTH_SHORT).show();
            sendJson("tank", panelData.getPanelString("tank"));
        }
    };
    final Runnable update = new Runnable() { // Send next command to Panel
        public void run() {
            mainLooper.postDelayed(update, UPDATE_INTERVAL_MILLIS);
            if (connected) {
                if(panelData.containsKey("bmantest")) {
                    if (panelData.getPanelString("bmantest").equals("true")) {                    // inset manual in this timeslot {
                        sendJson("bmantest", "false");                // resume current panel mode
                        //break;
                    }
                }

                if(priorityCommandEnabled == true) {
                    sendJson(priorityCommand, priorityCommandValue);
                    priorityCommandEnabled = false;
                }
                else
                    sendJson(updateCommandList.get(commandListIndex++), "Query");
                if (commandListIndex == commandLength)
                    commandListIndex = 0;
            }
        }
    };

    final Runnable postMsg = new Runnable() {
        public void run() {
            postDataLayer();

            //Toast.makeText(getActivity(), "HID Timeout", Toast.LENGTH_SHORT).show();
        }
    };
    final Runnable setPanelTime = new Runnable() {
        public void run() {
            String currentDate = new SimpleDateFormat("dd MM yyyy", Locale.getDefault()).format(new Date());
            String currentTime = new SimpleDateFormat("HH mm ss", Locale.getDefault()).format(new Date());
            if (connected) {
                String uiTime = currentTime + " " + currentDate;
                priorityCommandEnabled = true;
                priorityCommand = "time";
                priorityCommandValue = uiTime;
                //sendJson("time", uiTime);
            }
            else
                mainLooper.postDelayed(setPanelTime,1000);


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
        Toast.makeText(getActivity(), "onResume Term", Toast.LENGTH_SHORT).show();
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
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        PopUpFragment popUpFragment;
        main_mode = (RadioGroup) view.findViewById(R.id.main_mode);
        bgrav = view.findViewById(R.id.grav);
        binit = view.findViewById(R.id.binit);
        banr = view.findViewById(R.id.banr);
        bbnr = view.findViewById(R.id.bbnr);
        bmicro = view.findViewById(R.id.bmicro);
        bspy = view.findViewById(R.id.bspy);
        bdmd = view.findViewById(R.id.bdmd);
        bdrip = view.findViewById(R.id.bdrip);
        manual = view.findViewById(R.id.manual);
        receiveText = view.findViewById(R.id.receiveText);
        timeRemote = view.findViewById(R.id.timeRemote);
        bgrav.setOnClickListener(v -> gravCallback());  // something is always true
        banr.setOnClickListener(v -> anrCallback());
        bbnr.setOnClickListener(v -> bbnrCallback());
        bspy.setOnClickListener(v -> bspyCallback());
        bmicro.setOnClickListener(v -> bmicroCallback());
        bdmd.setOnClickListener(v -> bdmdCallback());
        bdrip.setOnClickListener(v -> bdripCallback());
        binit.setOnClickListener(v -> binitCallback());
        manual.setOnClickListener(v -> manualCallback());
        //cancelManual.setOnClickListener(v -> cancelManualCallback());

        /* Spinner Tank Size */
        tankDropDown = view.findViewById(R.id.tankDropDown);
        final ArrayAdapter<CharSequence> tankAdapter = ArrayAdapter.createFromResource(requireActivity(), R.array.tankArray, R.layout.mode_spinner);
        tankAdapter.setDropDownViewResource(R.layout.mode_spinner);
        tankDropDown.setAdapter(tankAdapter);
        tankDropDown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                long tankIndex = parent.getItemIdAtPosition(position);
                if(tankIndex == 3)
                    tankSizeString = "1000";
                else if(tankIndex == 2)
                    tankSizeString = "750";
                else if(tankIndex == 1)
                    tankSizeString = "500";
                else
                    tankSizeString = "0";
                panelData.setPanel("tank", tankSizeString);
                if(tankIndex != 0)                                              // prevent from reseting Panel tank size on default
                    mainLooper.post(updateTank);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Toast.makeText(getActivity(), "Spinner Nothing", Toast.LENGTH_SHORT).show();
            }
        });
        /* Start Update timer to sync UI   */
        mainLooper.postDelayed(update, UPDATE_INTERVAL_MILLIS);
        mainLooper.postDelayed(setPanelTime, 1);
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
                    receiveText.append(spn);
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
            int flags = SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
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
        boolean enableMode = false;
        String tankItemIndex = "";
        if (panelData.containsKey("mode")) {          // Set Mode Radio Button
            if (panelData.getPanelString("mode").equals("bANR"))
                main_mode.check(R.id.banr);
            else if (panelData.getPanelString("mode").equals("bBNR"))
                main_mode.check(R.id.bbnr);
            else if (panelData.getPanelString("mode").equals("bDMD"))
                main_mode.check(R.id.bdmd);
            else if (panelData.getPanelString("mode").equals("bSPY"))
                main_mode.check(R.id.bspy);
            else if (panelData.getPanelString("mode").equals("bDRIP"))
                main_mode.check(R.id.bdrip);
            else if (panelData.getPanelString("mode").equals("bMICRO"))
                main_mode.check(R.id.bmicro);
            else if (panelData.getPanelString("mode").equals("bGRAV"))
                main_mode.check(R.id.grav);
            else if (panelData.getPanelString("mode").equals("bmantest"))
                main_mode.check(R.id.manual);
            else if (panelData.getPanelString("mode").equals("binit"))
                main_mode.check(R.id.binit);
        }
        if (panelData.containsKey("tank")) {
            tankItemIndex = "0";
            if (panelData.getPanelString("tank").equals( "500"))
                tankItemIndex = "500/600";
            if (panelData.getPanelString("tank").equals( "750"))
                tankItemIndex = "750/900";
            if (panelData.getPanelString("tank").equals("1000"))
                tankItemIndex = "1000/1200";

            tankDropDown.setSelection(((ArrayAdapter)tankDropDown.getAdapter()).getPosition(tankItemIndex));
            enableMode = !panelData.getPanelString("tank").equals("0");
        }
        if(panelData.containsKey("time"))

        for(int i = 0; i < main_mode.getChildCount(); i++){
            main_mode.getChildAt(i).setEnabled(enableMode);
        }
        //main_mode.check(R.id.binit);
        if(!popUpDialogPosted) {
            if(panelData.getPanelString("tank").equals("0")) {
                //showTankPopUp();
                popUpDialogPosted = true;
            }
        }
//        if (panelData.containsKey("tank")) {     // Tank pull down
        //   if (panelData.getPanelString("tank") != "0") {
        //          tankDropDown.setSelection(((ArrayAdapter)tankDropDown.getAdapter()).getPosition(panelData.getPanelString("tank")));
        // mainLooper.post(waitOnTank);
        //           }
//            else {
        //              panelDatak(dataLayer.getVALUE());
        //            mainLooper.post(modeSpinner);
        //          }


        //       else
        //Toast.makeText(getActivity(), "CMD not Recognized " + dataLayer.getKEY(), Toast.LENGTH_SHORT).show();
    }
    public void modeEnable(RadioGroup main_mode) {
    }
    public void getPanelStatus() {  // depriciate TOOD delete me
 /*       if(check5lTime()) {
            Toast.makeText(getActivity(), "Update 5L Time", Toast.LENGTH_SHORT).show();
        }*/

        mainLooper.postDelayed(update, UPDATE_INTERVAL_MILLIS);
        //mainLooper.postDelayed(clearAck, 200);
        if (connected) {
            if (panelData.getPanelString("bmantest").equals("true"))                     // inset manual in this timeslot
                sendJson("bmantest", "false");                // resume current panel mode
            else {
                sendJson(updateCommandList.get(commandListIndex++), "Query");
                if (commandListIndex == commandLength)
                    commandListIndex = 0;
            }
        }
    }
    public void showTankPopUp() {
        DialogFragment newFragment = new PopUpFragment();
        assert getParentFragmentManager() != null;
        newFragment.show(getParentFragmentManager(), "tank");
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
    private void ackModeCmd(RadioButton activeButton){
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
    }
    private void cancelManualCallback(){ // TODO need this?
        View TerminalLayout = getActivity().findViewById(R.id.TerminalLayout);
        ViewGroup parent = (ViewGroup) TerminalLayout.getParent();
        int index = parent.indexOfChild(TerminalLayout);
        parent.removeView(TerminalLayout);
        TerminalLayout = getLayoutInflater().inflate(R.layout.fragment_terminal, parent, false);
        parent.addView(TerminalLayout, index);

    }
    private void gravCallback() {
        sendJson("bGRAV","true");
        Bundle args = new Bundle();
        args.putInt("device", deviceId);
        args.putInt("port", portNum);
        args.putInt("baud", baudRate);
        args.putBoolean("withIoManager", withIoManager);
        Fragment TerminalFragment = new GravFrag();
        TerminalFragment.setArguments(args);
        getParentFragmentManager().beginTransaction().replace(R.id.fragment, TerminalFragment, "gravity").addToBackStack(null).commit();
    }
    private void anrCallback() {
        sendJson("bANR","true");
        Bundle args = new Bundle();
        args.putInt("device", deviceId);
        args.putInt("port", portNum);
        args.putInt("baud", baudRate);
        args.putBoolean("withIoManager", withIoManager);
        Fragment TerminalFragment = new AnrFragment();
        TerminalFragment.setArguments(args);
        getParentFragmentManager().beginTransaction().replace(R.id.fragment, TerminalFragment, "anr").addToBackStack(null).commit();
    }
    private void manualCallback() {
        /*View TerminalLayout = getActivity().findViewById(R.id.TerminalLayout);
        ViewGroup parent = (ViewGroup) TerminalLayout.getParent();
        int index = parent.indexOfChild(TerminalLayout);
        parent.removeView(TerminalLayout);
        TerminalLayout = getLayoutInflater().inflate(R.layout.manual_main, parent, false);
        parent.addView(TerminalLayout, index);*/
        //sendJson("bANR","true");
        sendJson("bmantest","true");                // suspend current panel mode
        Bundle args = new Bundle();
        args.putInt("device", deviceId);
        args.putInt("port", portNum);
        args.putInt("baud", baudRate);
        args.putBoolean("withIoManager", withIoManager);
        Fragment TerminalFragment = new manualClass();
        TerminalFragment.setArguments(args);
        getParentFragmentManager().beginTransaction().replace(R.id.fragment, TerminalFragment, "manual").addToBackStack(null).commit();
    }
    private void bbnrCallback() {
        sendJson("bBNR","true");
    }
    private void bspyCallback() {
        sendJson("bSPY","true");
        Bundle args = new Bundle();
        args.putInt("device", deviceId);
        args.putInt("port", portNum);
        args.putInt("baud", baudRate);
        args.putBoolean("withIoManager", withIoManager);
        Fragment TerminalFragment = new NightSpray();
        TerminalFragment.setArguments(args);
        getParentFragmentManager().beginTransaction().replace(R.id.fragment, TerminalFragment, "nightspray").addToBackStack(null).commit();
    }
    private void bmicroCallback() {
        sendJson("bMICRO","true");
        Bundle args = new Bundle();
        args.putInt("device", deviceId);
        args.putInt("port", portNum);
        args.putInt("baud", baudRate);
        args.putBoolean("withIoManager", withIoManager);
        Fragment TerminalFragment = new MicroDose();
        TerminalFragment.setArguments(args);
        getParentFragmentManager().beginTransaction().replace(R.id.fragment, TerminalFragment, "microdose").addToBackStack(null).commit();
    }
    private void bdmdCallback() {
        sendJson("bDMD", "true");
        Bundle args = new Bundle();
        args.putInt("device", deviceId);
        args.putInt("port", portNum);
        args.putInt("baud", baudRate);
        args.putBoolean("withIoManager", withIoManager);
        Fragment TerminalFragment = new Demand();
        TerminalFragment.setArguments(args);
        getParentFragmentManager().beginTransaction().replace(R.id.fragment, TerminalFragment, "demand").addToBackStack(null).commit();
    }
    private void bdripCallback() {
        sendJson("bDRIP", "true");
        Bundle args = new Bundle();
        args.putInt("device", deviceId);
        args.putInt("port", portNum);
        args.putInt("baud", baudRate);
        args.putBoolean("withIoManager", withIoManager);
        Fragment TerminalFragment = new Drip();
        TerminalFragment.setArguments(args);
        getParentFragmentManager().beginTransaction().replace(R.id.fragment, TerminalFragment, "drip").addToBackStack(null).commit();
    }
    private void binitCallback() {
//        if(dataLayer.getTank().equals("0") && main_mode.isEnabled())
//            main_mode.setEnabled(false);
//        else
//            main_mode.setEnabled(true);
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
            receiveText.append(spn);
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
            //    spn.append("receive " + data.length + " bytes\n");
            //    spn.append(HexDump.dumpHexString(data)).append("\n");
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
                        key = true;
                        keyString = "";
                        break;
                    case '}':                           // End Parse Save [key,Value] and exit
                        V = keyString;
                        if(V != null && K != null ) {
                            panelData.setPanel(K, V);
                            panelData.setPanel("KEY", K);
                            panelData.setPanel("VALUE", V);
                            receiveText.append( K + ":" + V  + "\n");
                        }
                        else
                            //Toast.makeText(getActivity(), "PARSE -- CMD is Null", Toast.LENGTH_SHORT).show();
                            keyString = "";
                        mainLooper.post(postMsg);       // Post Message to UI
                        break;
                    case  ':':                           // save key move to value phase
                        K = keyString;
                        keyString = "";
                        break;
                    case '"':                           // ignore these
                    case '\n':
                    case '\r':
                    //case ' ':
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
        receiveText.append(spn);
    }
}
