package com.hoho.android.usbserial.examples;
import static java.util.List.of;
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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import android.graphics.Color;

public class TerminalFragment extends Fragment implements SerialInputOutputManager.Listener, AdapterView.OnItemSelectedListener {

    public void setMain_mode(RadioGroup main_mode) {
        this.main_mode = main_mode;
   }
    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;
    private static final int UPDATE_INTERVAL_MILLIS = 350;
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
    private RadioGroup main_mode;
    private RadioButton bgrav;
    private RadioButton banr;
    private RadioButton bbnr;
    private RadioButton bspy;
    private RadioButton bdrip;
    private RadioButton bdmd;
    private RadioButton binit;
    private Spinner tankDropDown;
    //private TextView remoteTime;
    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    public boolean connected = false;
    public DataLayer dataLayer = new DataLayer();
    /* Hoot adds */
    //static boolean cmd_busy = false;
    public String keyString = "";
    //public String KEY = "";
    //public String VALUE = "";
    public String remoteMin = "00";
    public String remoteSec = "00";
    public String remoteHr = "00";
    public boolean popUpDialogPosted = false;
    //Button showPopupBtn, closePopupBtn;
    /*  List of data layer commands to process
*   command index keeps trck of next command to send
*   command lenght is length of commandList
*/
    public List<String> updateCommandList = of(
//        "bCL",
//        "bAlarm",
//        "bHigh",
//        "bLow",
//        "bWM",
        "mode",
        "hour",
        "min",
        "sec",
        "tank"
    );
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
    }
    /*
     * Lifecycle
     */
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
    final Runnable modeSpinner = new Runnable() {
        @Override
        public void run() {

            //Toast.makeText(getActivity(), "modeSpinner  " + dataLayer.getTank(), Toast.LENGTH_SHORT).show();
           // tankDropDown.setSelection(item);
            tankDropDown.setSelection(((ArrayAdapter)tankDropDown.getAdapter()).getPosition(dataLayer.getVALUE()));
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
            updateDataLayer();
        }
    };
   /*final Runnable clearAck = new Runnable() {
        public void run() {
            if(!dataLayer.isMsgAck()){
                Toast.makeText(getActivity(), "ACK FAILED " + dataLayer.getKEY(), Toast.LENGTH_SHORT).show();
                dataLayer.setMsgAck(true);
            }
        }
    };*/
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
        PopUpFragment popUpFragment;
        main_mode = (RadioGroup) view.findViewById(R.id.main_mode);
        bgrav = view.findViewById(R.id.grav);
        binit = view.findViewById(R.id.binit);
        banr = view.findViewById(R.id.banr);
        bbnr = view.findViewById(R.id.bbnr);
        bspy = view.findViewById(R.id.bspy);
        bdmd = view.findViewById(R.id.bdmd);
        bdrip = view.findViewById(R.id.bdrip);
        receiveText = view.findViewById(R.id.receiveText);
        timeRemote = view.findViewById(R.id.timeRemote);
        bgrav.setOnClickListener(v -> gravCallback());  // something is always true
        banr.setOnClickListener(v -> banrCallback());
        bbnr.setOnClickListener(v -> bbnrCallback());
        bspy.setOnClickListener(v -> bspyCallback());
        bdmd.setOnClickListener(v -> bdmdCallback());
        bdrip.setOnClickListener(v -> bdripCallback());
        binit.setOnClickListener(v -> binitCallback());

        /* Spinner Tank Size */
        tankDropDown = view.findViewById(R.id.tankDropDown);
        final ArrayAdapter<CharSequence> tankAdapter = ArrayAdapter.createFromResource(requireActivity(), R.array.tankArray, R.layout.mode_spinner);
        tankAdapter.setDropDownViewResource(R.layout.mode_spinner);
        tankDropDown.setAdapter(tankAdapter);
        tankDropDown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                long tankIndex = parent.getItemIdAtPosition(position);
                dataLayer.setTank(tankDropDown.getSelectedItem().toString());
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
        //modeEnable(main_mode);
        return view;
    }

    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
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
        if ((dataLayer.getKEY()).equals("bCL"))
            dataLayer.setbCL(Boolean.parseBoolean(dataLayer.getVALUE()));
        else if ((dataLayer.getKEY()).equals("bAlarm"))
            dataLayer.setbAlarm(Boolean.parseBoolean(dataLayer.getVALUE()));
        else if ((dataLayer.getKEY()).equals("bLow"))
            dataLayer.setbLow(Boolean.parseBoolean(dataLayer.getVALUE()));
        else if ((dataLayer.getKEY()).equals("bHigh"))
            dataLayer.setbHigh(Boolean.parseBoolean(dataLayer.getVALUE()));
        else if ((dataLayer.getKEY()).equals("bWM"))
            dataLayer.setbWM(Boolean.parseBoolean(dataLayer.getVALUE()));
        else if ((dataLayer.getKEY()).equals("mode")) {
            dataLayer.setMode(dataLayer.getVALUE());
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
                if(!popUpDialogPosted) {
                    if(dataLayer.getTank().equals(0)) {
                        showTankPopUp();
                        popUpDialogPosted = true;
                    }
                }
            }
        }
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
        else if ((dataLayer.getKEY()).equals("bNNR")) {
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
                if (dataLayer.getVALUE().equals("0")) {
                    mainLooper.post(waitOnTank);
                }
                else {
                    dataLayer.setTank(dataLayer.getVALUE());
                    mainLooper.post(modeSpinner);
                }
            }
        }
        else
            Toast.makeText(getActivity(), "CMD not Recognized " + dataLayer.getKEY(), Toast.LENGTH_SHORT).show();
    }
    public void modeEnable(RadioGroup main_mode) {
        if (main_mode.isEnabled()) {
            main_mode.setEnabled(false);
        } else {
            main_mode.setEnabled(true);

        }
    }
    public void updateDataLayer() {
//        if(check5lTime()) {
//            Toast.makeText(getActivity(), "Update 5L Time", Toast.LENGTH_SHORT).show();
//        }

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
    private SpannableStringBuilder updateTime(String remoteHr, String remoteMin, String remoteSec){
        SpannableStringBuilder updateTime = new SpannableStringBuilder();
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

    private void gravCallback() {
        sendJson("bGRAV","true");
    }
    private void banrCallback() {
        sendJson("bANR","true");
    }
    private void bbnrCallback() {
        sendJson("bBNR","true");
    }
    private void bspyCallback() {
        sendJson("bSPY","true");
    }
    private void bdmdCallback() {
        sendJson("bDMD", "true");
    }
    private void bdripCallback() {
        sendJson("bDRIP", "true");
    }
    private void binitCallback() {
        //sendJson("bDRIP", "true");
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
//            spn.append("receive " + data.length + " bytes\n");
//            spn.append(HexDump.dumpHexString(data)).append("\n");
            parse(data);
        }
    }

    public void parse(byte[] data)
    {
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
                        keyString = "";
                        receiveText.append(dataLayer.getKEY() + ":" + dataLayer.getVALUE()  + "\n");
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
        receiveText.append(spn);
    }
}
