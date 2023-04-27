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
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
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
import java.util.Calendar;

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
    private final boolean UiMessageSent = false;
    Handler timerHandler;

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
    private TextView gallonsCount;

    private RadioButton bgrav;
    private RadioButton banr;
    private RadioButton bspy;
    private RadioButton bdrip;
    private RadioButton bdmd;
    private RadioButton bbnr;
    //private TextView remoteTime;
    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    private boolean connected = false;
    private boolean msgAck = false;

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
    final Runnable update = new Runnable() {
        public void run() {
            updateUI();
        }
    };
 /*   final Runnable hidTimeout = new Runnable() {
        public void run() {
            Toast.makeText(getActivity(), "HID Timeout", Toast.LENGTH_SHORT).show();
        }
    }
    mainLooper.postDelayed(hidTimeout, 1500);*/
    public void updateUI() {
        mainLooper.postDelayed(update, 3000);
        if(connected)
            sendJson("bANR","true");
    }
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
        bgrav = view.findViewById(R.id.grav);
        banr = view.findViewById(R.id.banr);
        bbnr = view.findViewById(R.id.bbnr);
        bspy = view.findViewById(R.id.bspy);
        bdmd = view.findViewById(R.id.bdmd);
        bdrip = view.findViewById(R.id.bdrip);
        receiveText = view.findViewById(R.id.receiveText);
        gallonsCount = view.findViewById(R.id.GallonsValue);
        RadioGroup mode = (RadioGroup) view.findViewById(R.id.main_mode);
        bgrav.setOnClickListener(v -> gravCallback());  // something is always true
        banr.setOnClickListener(v -> banrCallback());
        bbnr.setOnClickListener(v -> bbnrCallback());
        bspy.setOnClickListener(v -> bspyCallback());
        bdmd.setOnClickListener(v -> bdmdCallback());
        bdrip.setOnClickListener(v -> bdripCallback());
        if(check5lTime()) {
            Toast.makeText(getActivity(), "Update 5L Time", Toast.LENGTH_SHORT).show();
        }
        /*
        * Start Update timer to sync UI
         */
        updateUI();
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

    private boolean check5lTime() {
        Calendar rightNow = Calendar.getInstance();
        int hour = rightNow.get(Calendar.HOUR_OF_DAY);

//        sendJson("hour", String.valueOf(hour));

        int minute = rightNow.get(Calendar.MINUTE);
//        sendJson("min", String.valueOf(minute));
        int second = rightNow.get(Calendar.SECOND);
//        sendJson("sec", String.valueOf(second));
        int month = rightNow.get(Calendar.DAY_OF_MONTH);
//        sendJson("month", String.valueOf(month));
        int day = rightNow.get(Calendar.DAY_OF_MONTH);
//        sendJson("day", String.valueOf(day));
        int year = rightNow.get(Calendar.YEAR);
//        sendJson("year", String.valueOf(year));
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

    private SpannableStringBuilder updateTime(String remoteHr, String remoteMin, String remoteSec){
        SpannableStringBuilder remoteTime = new SpannableStringBuilder();
        remoteTime.append(remoteHr);
        remoteTime.append(":");
        remoteTime.append(remoteMin);
        remoteTime.append(":");
        remoteTime.append(remoteSec);
        return(remoteTime);
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
    private void gravCallback() {
        send("{\"bGRAV\":true}");
//        status("Gravity");
//        setTextViewFlavor(bgrav, "true");
        ackModeCmd(bgrav);
    }
    private void banrCallback() {
        send("{\"bANR\":true}");
//        status("ANR");
        msgAck = false;
        while(msgAck);
        ackModeCmd(banr);
    }
    private void bbnrCallback() {
        send("{\"bBNR\":true}");
//        status("BNR");
        msgAck = false;
        while(msgAck);
        ackModeCmd(bbnr);
    }
    private void bspyCallback() {
        send("{\"bSPY\":true}");
//        status("SPRAY");
        msgAck = false;
        while(msgAck);
        ackModeCmd(bspy);
    }
    private void bdmdCallback() {
        send("{\"bDMD\":true}");
//        status("Demand");
        msgAck = false;
        while(msgAck);
        ackModeCmd(bdmd);
    }
    private void bdripCallback() {
        send("{\"bDRIP\":true}");
//        status("Drip");
        msgAck = false;
        while(msgAck);
        ackModeCmd(bdrip);
    }

    private boolean sendJson(String cmd, String value) {
        SpannableStringBuilder json = new SpannableStringBuilder();
        json.append("{\"");
        json.append(cmd);
        json.append("\":");
        json.append(value);
        json.append("}");
        json.append("\n");
        try {
            send(String.valueOf(json));
            msgAck = false;
            for(int j=0; j<1000;j++) {

                    if(msgAck)
                        break;
                    else {
                        Toast.makeText(getActivity(), "No ACK", Toast.LENGTH_SHORT).show();
                    }
            }
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
                    case '{':                           // start case start key phase
                        key = true;
                        keyString = "";
                        break;
                    case '}':                           // save value and exit
                        key = false;
                        value = false;
                        VALUE = keyString;
                        keyString = "";
                        receiveText.append(KEY + ":" + VALUE  + "\n");
                        msgAck = true;
                        break;
                    case ':':                           // save key move to value phase
                        key = false;
                        KEY = keyString;
                        value = true;
                        keyString = "";
                        break;
                    case '"':                           // ignore these
                    case '\n':
                    case '\r':
                    case ' ':
                        break;
                    default:
                        //if(key)
                        //    keyString = keyString.concat(String.valueOf(rx.charAt(k)));
                        //else if (value)
                            keyString = keyString.concat(String.valueOf(rx.charAt(k)));  // add char to string
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

}
