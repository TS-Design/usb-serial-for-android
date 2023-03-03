package com.hoho.android.usbserial.examples;


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
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

//import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;

import android.graphics.Color;

public class TerminalFragment extends Fragment implements SerialInputOutputManager.Listener {


    private enum UsbPermission { Unknown, Requested, Granted, Denied }

    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;

    private int deviceId, portNum, baudRate;
    private boolean withIoManager;

    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;
    private TextView receiveText;
    private TextView fieldFlush;
    private TextView recirculate;
    private TextView pumpRuntime;
    private TextView mainEfficencyPump;
    private TextView altEfficencyPump;
    private TextView peristalicPump;
    private TextView airpressure;
    private TextView highProbe;
    private TextView lowProbe;
    private TextView alarmExt;
    private TextView chlorineIn;
    private TextView waterMeterIn;
    private TextView test;
    private TextView testHigh;
    private TextView testLow;
    private TextView testAlarmExt;
    private TextView testWaterMeterIn;
    private TextView testChlorineIn;
    private TextView effluentCount;
    private TextView gallonsCount;
    private RadioButton boff;
    private RadioButton bANR;
    private RadioButton bSPY;
    private RadioButton bdrip;
    private RadioButton bdmd;
    private TextView remoteTime;
    private ControlLines controlLines;
    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    private boolean connected = false;

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
    }
    /* Hoot adds */
    //static boolean cmd_busy = false;
    static String keyString = "";
    static String KEY = "";
    static String VALUE = "";
    public String remoteMin = "00";
    public String remoteSec = "00";
    public String remoteHr = "00";
    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");
        withIoManager = getArguments().getBoolean("withIoManager");
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
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());
        fieldFlush = view.findViewById(R.id._fieldFlush);
        recirculate = view.findViewById(R.id._recirculate);
        mainEfficencyPump = view.findViewById(R.id._effpump1);
        altEfficencyPump = view.findViewById(R.id._effpump2);
        peristalicPump = view.findViewById(R.id._peristalic);
        airpressure = view.findViewById(R.id._airpressure);
        boff = view.findViewById(R.id.bOFF);
        bANR = view.findViewById(R.id.bANR);
        bSPY = view.findViewById(R.id.bSPY);
        bdmd = view.findViewById(R.id.bdemand);
        bdrip = view.findViewById(R.id.bdrip);
        highProbe = view.findViewById(R.id._bhigh);
        lowProbe = view.findViewById(R.id._blow);
        alarmExt = view.findViewById(R.id._balarm);
        chlorineIn = view.findViewById(R.id._bcl);
        waterMeterIn = view.findViewById(R.id._bwm);
        gallonsCount = view.findViewById(R.id._gallonsCount);

        test = view.findViewById(R.id.controlLineTest);
        testHigh = view.findViewById(R.id.controlLineHigh);
        testLow = view.findViewById(R.id.controlLineLow);
        testAlarmExt = view.findViewById(R.id.controlLineAlarm);
        testChlorineIn = view.findViewById(R.id.controlLineCl);
        testWaterMeterIn = view.findViewById(R.id.controlLineWm);
        effluentCount = view.findViewById(R.id._effluentcount);
        pumpRuntime = view.findViewById(R.id._pumprun);
        remoteTime = view.findViewById(R.id.remoteTime);
        View receiveBtn = view.findViewById(R.id.receive_btn);
        //View bANR = view.findViewById(R.id.bANR);

        TextView sendText = view.findViewById(R.id.send_text);
        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));
        controlLines = new ControlLines(view);
        if(withIoManager) {
            receiveBtn.setVisibility(View.GONE);
        } else {
            receiveBtn.setOnClickListener(v -> read());
        }
        RadioGroup mode = (RadioGroup) view.findViewById(R.id._mode);
        boff.setOnClickListener(v -> send("{\"bOFF\":True}"));
        bANR.setOnClickListener(v -> send("{\"bANR\":True}"));
        bSPY.setOnClickListener(v -> send("{\"bSPY\":True}"));
        bdmd.setOnClickListener(v -> send("{\"bDMD\":True}"));
        bdrip.setOnClickListener(v -> send("{\"bdrip_sel\":True}"));


        //boff.setClickable(false);
        //boff.setEnabled(false);

        //boff.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans

        //View _effpump1 = view.findViewById(R.id._effpump1); // mlp

        return view;
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
            controlLines.start();
        } catch (Exception e) {
            status("connection failed: " + e.getMessage());
            disconnect();
        }
    }

    private void disconnect() {
        connected = false;
        controlLines.stop();
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

    private void setTextViewFlavor(TextView textview, String value) {
        if (value.equalsIgnoreCase("true")) {
            textview.setBackgroundColor(Color.GREEN);
            textview.setTextColor(Color.BLACK);
        }
        else {
            textview.setBackgroundColor(Color.BLACK);
            textview.setTextColor(Color.WHITE);
        }
    }

    public void upDateUi(String cmd,  String value) {
        if (cmd.equalsIgnoreCase("bS2")) {
            setTextViewFlavor(fieldFlush, value);
        }
        else if(cmd.equalsIgnoreCase("blow")) {
            setTextViewFlavor(lowProbe, value);
        }
        else if(cmd.equalsIgnoreCase("bhigh")) {
            setTextViewFlavor(highProbe, value);
        }
        else if(cmd.equalsIgnoreCase("balarm")) {
            setTextViewFlavor(alarmExt, value);
        }
        else if(cmd.equalsIgnoreCase("bcl")) {
            setTextViewFlavor(chlorineIn, value);
        }
        else if(cmd.equalsIgnoreCase("bwm")) {
            setTextViewFlavor(waterMeterIn, value);
        }
        else if(cmd.equalsIgnoreCase("bS1")) {
            setTextViewFlavor(recirculate, value);
        }
        else if(cmd.equalsIgnoreCase("bRY3")) {
                setTextViewFlavor(mainEfficencyPump, value);
        }
        else if(cmd.equalsIgnoreCase("bRY4")) {
                setTextViewFlavor(altEfficencyPump, value);
        }
        else if(cmd.equalsIgnoreCase("bENA")) {
            setTextViewFlavor(test, value);
        }
        else if(cmd.equalsIgnoreCase("bDPump")) {
            setTextViewFlavor(peristalicPump, value);
        }
        else if(cmd.equalsIgnoreCase("PTime")) {
            pumpRuntime.setText(value);
        }
        else if(cmd.equalsIgnoreCase("FFper")) {
            effluentCount.setText(value);
        }
        else if(cmd.equalsIgnoreCase("AirTime")) {
            airpressure.setText(value);
        }
        else if(cmd.equalsIgnoreCase("GAL")) {
            gallonsCount.setText(value);
        }
        else if(cmd.equalsIgnoreCase("bOFF")) {
            if (value.equalsIgnoreCase("true"))
                boff.setChecked(true);
        }
        else if(cmd.equalsIgnoreCase("bdrip_sel")) {
            if (value.equalsIgnoreCase("true"))
                bdrip.setChecked(true);
        }
        else if(cmd.equalsIgnoreCase("bANR")) {
            if (value.equalsIgnoreCase("true"))
                bANR.setChecked(true);
        }
        else if(cmd.equalsIgnoreCase("bSPY")) {
            if (value.equalsIgnoreCase("true"))
                bSPY.setChecked(true);
        }
        else if(cmd.equalsIgnoreCase("bDMD")) {
            if (value.equalsIgnoreCase("true"))
                bdmd.setChecked(true);
        }
        else if(cmd.equalsIgnoreCase("HRS")) {
            remoteHr = value;
            remoteTime.setText(updateTime(remoteHr, remoteMin, remoteSec));
        }
        else if(cmd.equalsIgnoreCase("MIN")) {
            remoteMin = value;
            remoteTime.setText(updateTime(remoteHr, remoteMin, remoteSec));
        }
        else if(cmd.equalsIgnoreCase("SEC")) {
            remoteSec = value;
            remoteTime.setText(updateTime(remoteHr, remoteMin, remoteSec));
        }
    }

    private SpannableStringBuilder updateTime(String remoteHr, String remoteMin, String remoteSec){
        SpannableStringBuilder remoteTime = new SpannableStringBuilder();
        remoteTime.append(remoteHr);
        remoteTime.append(":");
        remoteTime.append(remoteMin);
        remoteTime.append(":");
        remoteTime.append(remoteSec);
        return(remoteTime);
    }

    private void sendJson(String cmd, String value) {
        SpannableStringBuilder json = new SpannableStringBuilder();
        json.append("{\"");
        json.append(cmd);
        json.append("\":");
        json.append(value);
        json.append("}");
        json.append("\n");
        send(String.valueOf(json));
    }

    private void send(String str) {
        if(!connected) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] data = (str + '\n').getBytes();
            SpannableStringBuilder spn = new SpannableStringBuilder();
            spn.append("send " + data.length + " bytes\n");
            spn.append(HexDump.dumpHexString(data)).append("\n");
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
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
            receive(Arrays.copyOf(buffer, len));
        } catch (IOException e) {
            // when using read with timeout, USB bulkTransfer returns -1 on timeout _and_ errors
            // like connection loss, so there is typically no exception thrown here on error
            status("connection lost: " + e.getMessage());
            disconnect();
        }
    }

    private void receive(byte[] data) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        if(data.length > 0)
        {
            spn.append("receive " + data.length + " bytes\n");
            spn.append(HexDump.dumpHexString(data)).append("\n");
            parse(data);
        }
        //receiveText.append(spn);
    }

    private void parse(byte[] data)
    {

        String rx = new String(data);
        //String valueString = null;
        boolean key = false;
        boolean value = false;

        if(rx.length() > 0){
            //receiveText.append(rx + "\n");

            for(int k = 0; k < rx.length(); k++){
                switch(rx.charAt(k)) {
                    case '{':
                        key = true;
                        keyString = "";
                        break;
                    case '}':
                        key = false;
                        value = false;
                        VALUE = keyString;
                        keyString = "";
                        if(!KEY.equalsIgnoreCase("AirTime") && !KEY.equalsIgnoreCase("PTime"))
                            receiveText.append(KEY + ":" + VALUE  + "\n");
                        upDateUi(KEY, VALUE);
                        break;
                    case ':':
                        key = false;
                        KEY = keyString;
                        value = true;
                        keyString = "";
                        break;
                    case '"':
                    case '\n':
                    case '\r':
                    case ' ':
                        break;
                    default:
                        //if(key)
                        //    keyString = keyString.concat(String.valueOf(rx.charAt(k)));
                        //else if (value)
                            keyString = keyString.concat(String.valueOf(rx.charAt(k)));
                        break;
                }
            }
        }
    }

    void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    class ControlLines {
        private static final int refreshInterval = 200; // msec

        private final Runnable runnable;
        private final ToggleButton test, high, low, alarm, CL, WM;

        ControlLines(View view) {
            runnable = this::run; // w/o explicit Runnable, a new lambda would be created on each postDelayed, which would not be found again by removeCallbacks

            test = view.findViewById(R.id.controlLineTest);
            high = view.findViewById(R.id.controlLineHigh);
            low = view.findViewById(R.id.controlLineLow);
            alarm = view.findViewById(R.id.controlLineAlarm);
            CL = view.findViewById(R.id.controlLineCl);
            WM = view.findViewById(R.id.controlLineWm);
            test.setOnClickListener(this::test);
            high.setOnClickListener(this::toggle);
            low.setOnClickListener(this::toggle);
            alarm.setOnClickListener(this::toggle);
            CL.setOnClickListener(this::toggle);
            WM.setOnClickListener(this::toggle);
        }

        private  void test(View v) {
            sendJson("bENA", "True");
        }

        private void toggle(View v) {
            ToggleButton btn = (ToggleButton) v;
            if (!connected) {
                btn.setChecked(!btn.isChecked());
                Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
                return;
            }
            String ctrl = "";
            try {
                if (btn.equals(test)) {
                    ctrl = "Test";
                    if(btn.isChecked()) {
                        sendJson("bENA", "false");
                    }
                    else {
                        sendJson("bENA","true");
                    }
                    //usbSerialPort.setRTS(btn.isChecked());
                }
                if (btn.equals(low)) { ctrl = "Low"; usbSerialPort.setDTR(btn.isChecked()); }
            } catch (IOException e) {
                status("set" + ctrl + "() failed: " + e.getMessage());
            }
        }

        private void run() {
            if (!connected)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getControlLines();
                test.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RTS));
                high.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CTS));
                low.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DTR));
                alarm.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DSR));
                CL.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CD));
                WM.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RI));
                mainLooper.postDelayed(runnable, refreshInterval);
            } catch (IOException e) {
                status("getControlLines() failed: " + e.getMessage() + " -> stopped control line refresh");
            }
        }

        void start() {
            if (!connected)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getSupportedControlLines();
                if (!controlLines.contains(UsbSerialPort.ControlLine.RTS)) test.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.CTS)) high.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.DTR)) low.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.DSR)) alarm.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.CD))   CL.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.RI))   WM.setVisibility(View.INVISIBLE);
                run();
            } catch (IOException e) {
                Toast.makeText(getActivity(), "getSupportedControlLines() failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        void stop() {
            mainLooper.removeCallbacks(runnable);
            test.setChecked(false);
            high.setChecked(false);
            low.setChecked(false);
            alarm.setChecked(false);
            CL.setChecked(false);
            WM.setChecked(false);
        }
    }
}
