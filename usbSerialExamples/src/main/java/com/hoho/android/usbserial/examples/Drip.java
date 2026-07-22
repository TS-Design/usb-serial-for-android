package com.hoho.android.usbserial.examples;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static java.lang.Integer.parseInt;

import android.annotation.SuppressLint;
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
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
public class Drip extends Fragment implements SerialInputOutputManager.Listener, AdapterView.OnItemSelectedListener {
    // private Fragment anrFragment;
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    private void onClick(View v1) {
        popupWindow.dismiss();
    }

    private enum UsbPermission {Unknown, Requested, Granted, Denied}

    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 1000;
    //private static final int READ_WAIT_MILLIS = 1000;
    private static final int UPDATE_INTERVAL_MILLIS = 100;
    private int deviceId, portNum, baudRate, microdose;
    private boolean withIoManager;
    // private boolean keypadOn = false;
    // private boolean bALT = false;
    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;
    private TextView flashAlarmView = null;
    private boolean flashAlarmPhase = false;
    //private final boolean UiMessageSent = false;
    public String priorityCommandValue;
    public String priorityCommand;
    public boolean priorityCommandEnabled;
    //Handler timerHandler;
    //String currentDateTimeString = java.text.DateFormat.getDateTimeInstance().format(new Date());
   //  private TextView receiveText;
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
    //private Spinner zoneCount;
    private EditText zoneCount;
    private EditText doseDayCount;
    // private EditText recirRepeatCount;
    // private EditText dripRunCount;
    // private EditText dripRunCountSec;
    private EditText effPumpAlarmTimeCount;
    private EditText FdRunTimeCount;
    private EditText FdRunTimeCountSec;
   // private EditText peristolticCount;
   // private EditText peristolticCountSec;
    //private TextView FdRunTime;
    //private TextView dosesDay;
    // private TextView effStatus;
    private TextView airPressure;
    //private TextView effPumpAlarmTime;
    // private Button closePopupBtn;
    private Button closeAlarmBtn;
    private Button clearAlarm;
    private TextView textAlarmTime;
    private TextView alarmTextWindow;
    private Button closeGallonsBtn;
    private Button manualInputTest;
    private Button zone1;
    private Button zone2;
    private TextView hourTotalValue;
    private TextView hourlyAverageValue;
    private TextView dailyTotalValue;
    private TextView thirtyDayTotalValue;
    private TextView thirtyDayTotalAverageValue;
    private TextView lifetimeDaysValue;
    private TextView lifetimeValue;
    private TextView lifetimeDaysAverageValue;
    private Button systemOk;
    public Button effPumpTest;
    public Button alarmLatch;
    public Button alarmHistory;
    public Button closePopupBtn;
    public Button flowData;
    public Button ffTest;
    public Button recirTest;
    public Button alarmReset;
    public Button alarm;
    public Button waterAlarm;
    public Button airAlarm;
    // public Button peristalticTest;
    //public String KEY = "";
    //public String VALUE = "";
    //public String remoteMin = "00";
    public String remoteSec = "00";
    // public String remoteHr = "00";
    // public String remoteYear = "00";
    // public String remoteDow = "00";
    // public String remoteDay = "00";
    // public String remoteMonth = "00";
    public String[][] alarmList = new String[30][20];
    public boolean popUpDialogPosted = false;
    PopupWindow popupWindow;
    PopupWindow popupManualTest;
    //Button showPopupBtn, closePopupBtn;
    /*  List of data layer commands to process
     *   command index keeps trck of next command to send
     *   command lenght is length of commandList
     */
    public List<String> updateCommandList = Arrays.asList(
            "tank",
            "bok",
            "bwater",
            "bptest",
            "balmrset",
            "so0",
            "so1",
            "so2",
            "dosesday",
            "fdrun",
            "effstat",
            "airpres",
            "palmtime",
            "zone",
            "balrmltch",
            "bAlarm",
            "bLow",
            "bHigh",
            "bairalrm",
            "flow",
            "time",
            "perdur"
    );
    // public int commandLength = updateCommandList.size();
    public int commandListIndex = 0;

    public Drip() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            ? UsbPermission.Granted : UsbPermission.Denied;
                    connect();
                }
            }
        };
        mainLooper = new Handler(Looper.getMainLooper());
    }
    /* Runnable */

    final Runnable setPanelTime = new Runnable() {
        @Override
        public void run() {
            String currentDate = new SimpleDateFormat("dd MM yyyy", Locale.getDefault()).format(new Date());
            String currentTime = new SimpleDateFormat("HH mm ss", Locale.getDefault()).format(new Date());
            if (connected) {
                String uiTime = currentTime + " " + currentDate;
                priorityCommandEnabled = true;
                priorityCommand = "time";
                priorityCommandValue = uiTime;
                Toast.makeText(getActivity(), "Panel Time Updated", Toast.LENGTH_SHORT).show();
            } else
                mainLooper.postDelayed(setPanelTime, 1000); // Keep Trying if disconnected
            //Toast.makeText(getActivity(), "HID Timeout", Toast.LENGTH_SHORT).show();
        }
    };
    final Runnable timeHandler = new Runnable() { // TODO no longer used?
        @Override
        public void run() {
            //String time = panelData.getPanelString("year") + "-" + panelData.getPanelString("month") + "-" + panelData.getPanelString("day") +" " + panelData.getPanelString("hrs") + ":" + panelData.getPanelString("min");
            timeRemote.setText(panelData.getPanelString("time"));
            mainLooper.postDelayed(timeHandler, 1000);
        }
    };
    final Runnable waitOnTank = new Runnable() {
        @Override
        public void run() {
            if (panelData.getPanelString("tank").equals("0")) {
                if (!popUpDialogPosted) {
                    showTankPopUp();
                    popUpDialogPosted = true;
                }
                mainLooper.postDelayed(waitOnTank, UPDATE_INTERVAL_MILLIS);
            } else {
                // mainLooper.postDelayed(update, UPDATE_INTERVAL_MILLIS);
                popUpDialogPosted = false;
            }
        }
    };
    /*final Runnable updateTank = () -> {
        Toast.makeText(getActivity(), "Send Tank " + panelData.getPanelString("tank"), Toast.LENGTH_SHORT).show();
        sendPriorityCommand("tank", panelData.getPanelString("tank"));
    };*/
    final Runnable update = this::getPanelStatus;
    final Runnable postMsg = this::postDataLayer;
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

    /*final Runnable update5L = () -> {
        Toast.makeText(getActivity(), "Send Panel Demand Alarm " + panelData.getPanelString("balrmtime"), Toast.LENGTH_SHORT).show();
        sendPriorityCommand("zone", panelData.getPanelString("zone"));
    };*/
    /*    final Runnable modeSpinner = new Runnable() {
        @Override
        public void run() {

            //Toast.makeText(getActivity(), "modeSpinner  " + dataLayer.getTank(), Toast.LENGTH_SHORT).show();
            zoneCount.setSelection(((ArrayAdapter)zoneCount.getAdapter()).getPosition(panelData.getPanelString("zone")));
        }
    }; */
    /* OS Callbacks */
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
        microdose = getArguments().getInt("microdose");
        withIoManager = getArguments().getBoolean("withIoManager");
        //  mainLooper.postDelayed(timeHandler,1000);

    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requireActivity().registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB), Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireActivity().registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));
        }
        Toast.makeText(getActivity(), "onResume Term", Toast.LENGTH_SHORT).show();
        if (usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted)
            mainLooper.post(this::connect);
    }

    @Override
    public void onPause() {
        mainLooper.removeCallbacks(postMsg);
        mainLooper.removeCallbacks(flashAlarmRunnable);
        flashAlarmView = null;
        if (popupManualTest != null && popupManualTest.isShowing()) {
            popupManualTest.dismiss();
        }
        if (connected) {
            sendJson("bENA", "false");
            status("disconnected");
            disconnect();
        }
        getActivity().unregisterReceiver(broadcastReceiver);
        super.onPause();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)  {
        View view = inflater.inflate(R.layout.drip_frag, container, false);
        //anrFragment = getParentFragment();
        // PopUpFragment popUpFragment;
        //focusSink = view.findViewById(R.id.focusSink);
        systemOk = view.findViewById(R.id.systemOk);
        flowData = view.findViewById((R.id.flowData));
        alarmLatch = view.findViewById(R.id.alarmLatch);
        alarmLatch.setOnClickListener(v -> alarmLatchCallback());
        alarmHistory = view.findViewById(R.id.alarmHistory);
        // recirTest = view.findViewById(R.id.recirTest);
        // recirTest.setOnClickListener(v -> recirTestCallback());
        ffTest = view.findViewById(R.id.ffTest);
        ffTest.setOnClickListener(v -> ffTestCallback());
        // peristalticTest = view.findViewById(R.id.peristalticTest);
        //peristalticTest.setOnClickListener(v -> peristalticTestCallback());
        effPumpTest = view.findViewById(R.id.effPumpTest);
        effPumpTest.setOnClickListener(v -> effPumpTestCallback());
        //effPumpAlarmTime = view.findViewById(R.id.effPumpAlarmTime);
        // effStatus = view.findViewById(R.id.effStatus);
        timeRemote = view.findViewById(R.id.timeRemote);
        airAlarm = view.findViewById(R.id.airAlarm);
        alarm = view.findViewById(R.id.alarm);
        alarmReset = view.findViewById(R.id.alarmReset);
        alarmReset.setOnClickListener(v -> alarmResetCallback());
        airPressure = view.findViewById(R.id.airPressure);
        waterAlarm = view.findViewById(R.id.waterAlarm);
        manualInputTest = view.findViewById(R.id.manualTest);
        // Single EditText handlers
        zoneCount = view.findViewById(R.id.zoneCount);
        zone1 =  view.findViewById(R.id.zone1);
        zone2 =  view.findViewById(R.id.zone2);
        zoneCount.setOnEditorActionListener((v, actionId, event) -> {
            boolean handled = false;
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                int zone = parseInt(zoneCount.getText().toString());
                sendPriorityCommand("zone", Integer.toString(zone));
                ((InputMethodManager) getContext().getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(requireView().getWindowToken(), 0);
                zoneCount.clearFocus();
                handled = true;
            }
            return handled;
        });
        /* recirRepeatCount = view.findViewById(R.id.recirRepeatCount);
        recirRepeatCount.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int rrepeat = parseInt(recirRepeatCount.getText().toString())*60;
                    sendPriorityCommand("rrepeat", Integer.toString(rrepeat));
                    ((InputMethodManager) getContext().getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(requireView().getWindowToken(), 0);
                    recirRepeatCount.clearFocus();
                    return true;
                }
                return false;
            }
        });*/
        effPumpAlarmTimeCount = view.findViewById(R.id.effPumpAlarmTimeCount);
        effPumpAlarmTimeCount.setOnEditorActionListener((v, actionId, event) -> {
            boolean handled = false;
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                int palmtime = parseInt(effPumpAlarmTimeCount.getText().toString()) * 60;
                sendPriorityCommand("palmtime", Integer.toString(palmtime));
                ((InputMethodManager) getContext().getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(requireView().getWindowToken(), 0);
                effPumpAlarmTimeCount.clearFocus();
                handled = true;
            }
            return handled;
        });
        doseDayCount = view.findViewById(R.id.doseDayCount);
        doseDayCount.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                int dosesday = parseInt(doseDayCount.getText().toString());
                sendPriorityCommand("dosesday", Integer.toString(dosesday));
                ((InputMethodManager) getContext().getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(requireView().getWindowToken(), 0);
                doseDayCount.clearFocus();
                return true;
            }
            return false;
        });       // Field Dose Minutes and Seconds
        FdRunTimeCount = view.findViewById(R.id.FdRunTimeCount);
        FdRunTimeCount.setOnEditorActionListener((v, actionId, event) -> {
            boolean handled = false;
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                // int fdrun = parseInt(FdRunTimeCount.getText().toString());
                int minutes; // Declare here so it's visible after try-catch
                int seconds; // Declare here so it's visible after try-catch
                int totalSeconds; // Declare here so it's visible after try-catch
                try {
                    minutes = Integer.parseInt(FdRunTimeCount.getText().toString());
                    seconds = Integer.parseInt(FdRunTimeCountSec.getText().toString());
                    totalSeconds = minutes * 60 + seconds; // Or just use value if that's what you want
                } catch (NumberFormatException e) {
                    return false;
                }
                Log.d("Drip", "Field Dose Update from Minutes:" + totalSeconds);
                sendPriorityCommand("fdrun", Integer.toString(totalSeconds));
                ((InputMethodManager) getContext().getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(requireView().getWindowToken(), 0);
                FdRunTimeCount.clearFocus();
                handled = true;
            }
            return handled;
        });
        FdRunTimeCountSec = view.findViewById(R.id.FdRunTimeCountSec);
        FdRunTimeCountSec.setOnEditorActionListener((v, actionId, event) -> {
            boolean handled = false;
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                // int fdrun = parseInt(FdRunTimeCountSec.getText().toString());
                int minutes; // Declare here so it's visible after try-catch
                int seconds; // Declare here so it's visible after try-catch
                int totalSeconds; // Declare here so it's visible after try-catch
                try {
                    minutes = Integer.parseInt(FdRunTimeCount.getText().toString());
                    seconds = Integer.parseInt(FdRunTimeCountSec.getText().toString());
                    totalSeconds = minutes * 60 + seconds; // Or just use value if that's what you want
                } catch (NumberFormatException e) {
                    return false;
                }
                sendPriorityCommand("fdrun", Integer.toString(totalSeconds));
                Log.d("Drio", "Field Dose Update from Seconds:" + totalSeconds);
                // Hide keyboard using EditText's window token
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(FdRunTimeCountSec.getWindowToken(), 0);
                // Clear focus AFTER hiding keyboard
                FdRunTimeCountSec.clearFocus();
                handled = true;
            }
            return handled;
        });

        // Drip Run Time Minutes and Seconds
        /*dripRunCount = view.findViewById(R.id.dripRunCount);
        dripRunCount.setOnEditorActionListener((v, actionId, event) -> {
            boolean handled = false;
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                // int rrun = parseInt(recirRunCount.getText().toString());
                int minutes; // Declare here so it's visible after try-catch
                int seconds; // Declare here so it's visible after try-catch
                int totalSeconds; // Declare here so it's visible after try-catch
                try {
                    minutes = Integer.parseInt(dripRunCount.getText().toString());
                    seconds = Integer.parseInt(dripRunCountSec.getText().toString());
                    totalSeconds = minutes * 60 + seconds; // Or just use value if that's what you want
                } catch (NumberFormatException e) {
                    return false;
                }
                sendPriorityCommand("fdrun", Integer.toString(totalSeconds));
                Log.d("Drip", "Drip Run Time Sec = " + totalSeconds);
                ((InputMethodManager) getContext().getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(requireView().getWindowToken(), 0);
                dripRunCount.clearFocus();
                handled = true;
            }
            return handled;
        });
        //dripRunCountSec = view.findViewById(R.id.dripRunCountSec);
        /* dripRunCountSec.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int rrun = parseInt(dripRunCountSec.getText().toString());
                    int minutes; // Declare here so it's visible after try-catch
                    int seconds; // Declare here so it's visible after try-catch
                    int totalSeconds; // Declare here so it's visible after try-catch
                    try {
                        minutes = Integer.parseInt(dripRunCount.getText().toString());
                        seconds = Integer.parseInt(dripRunCountSec.getText().toString());
                        totalSeconds = minutes * 60 + seconds; // Or just use value if that's what you want
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    sendPriorityCommand("rrun", Integer.toString(totalSeconds));
                    Log.d("Drip", "Reciculate Update from Seconds:" + totalSeconds);
                    ((InputMethodManager) getContext().getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(requireView().getWindowToken(), 0);
                    dripRunCountSec.clearFocus();
                    handled = true;
                }
                return handled;
            }
        });*/

        flowData.setOnClickListener(v -> {
            //instantiate the popup.xml layout file
            LayoutInflater layoutInflater = (LayoutInflater) Drip.this.getContext().getSystemService(LAYOUT_INFLATER_SERVICE);
            View customView = layoutInflater.inflate(R.layout.gallons_popup, null);
            manualInputTest.setEnabled(false);
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
            hourlyAverageValue.setText(panelData.getPanelString("hourAvg"));
            dailyTotalValue.setText(panelData.getPanelString("day1"));
            thirtyDayTotalValue.setText(panelData.getPanelString("day30"));
            thirtyDayTotalAverageValue.setText(panelData.getPanelString("day30Avg"));
            lifetimeDaysValue.setText(panelData.getPanelString("lifedays"));
            lifetimeValue.setText(panelData.getPanelString("life"));
            lifetimeDaysAverageValue.setText(panelData.getPanelString("lifetimeAvg"));
            //close the popup window on button click
            closeGallonsBtn.setOnClickListener(v16 -> {
                manualInputTest.setEnabled(true);
                popupWindow.dismiss();
            });
        });
        alarmHistory.setOnClickListener(v ->
            AlarmHistoryPopup.show(getContext(), view, panelData,
                () -> sendPriorityCommand("log", "query"),
                () -> sendPriorityCommand("clrlog", "query")));
        manualInputTest.setOnClickListener(v -> manualTestCallback(view));

        // Micro Dose mode: hide Field Flush button and relabel title
        TextView textAnr = view.findViewById(R.id.textAnr);
        if (microdose == 1) {
            ffTest.setVisibility(View.INVISIBLE);
            textAnr.setText("Micro Dose");
        }

        // Start Update timer to sync UI
        mainLooper.postDelayed(update, UPDATE_INTERVAL_MILLIS);
        panelData = new ViewModelProvider(requireActivity()).get(PanelViewModel.class).panelData;
        return view;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            // eiveText.setText("");
            return true;
        } else if (id == R.id.matrix) {
            Toast.makeText(getActivity(), "Matrix", Toast.LENGTH_SHORT).show();
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View popupView = inflater.inflate(R.layout.matrix, null);
            int width = LinearLayout.LayoutParams.WRAP_CONTENT;
            int height = LinearLayout.LayoutParams.WRAP_CONTENT;
            boolean focusable = true;
            final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);
            popupWindow.showAtLocation(popupView, Gravity.CENTER, 0, 0);
            popupView.setBackgroundColor(Color.GRAY);
            popupView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    popupWindow.dismiss();
                    return true;
                }
            });
            return true;
        } else if (id == R.id.update_time) {
            mainLooper.post(setPanelTime);
            return true;
        } else if (id == R.id.send_break) {
            if (!connected) {
                Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            } else {
                try {
                    usbSerialPort.setBreak(true);
                    Thread.sleep(100);
                    usbSerialPort.setBreak(false);
                    SpannableStringBuilder spn = new SpannableStringBuilder();
                    spn.append("send <break>\n");
                    spn.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (UnsupportedOperationException ignored) {
                    Toast.makeText(getActivity(), "BREAK not supported", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(getActivity(), "BREAK failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    public void onNewData(byte[] data) {
        mainLooper.post(() -> receive(data));
    }

    public void onRunError(Exception e) {
        mainLooper.post(() -> {
            status("connection lost: " + e.getMessage());
            disconnect();
        });
    }

    // Local Methods and Callbacks
    public void sendPriorityCommand(String pCmd, String pValue) {
        priorityCommandEnabled = true;
        priorityCommand = pCmd;
        priorityCommandValue = pValue;
    }


    private void connect() {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        for (UsbDevice v : usbManager.getDeviceList().values())
            if (v.getDeviceId() == deviceId)
                device = v;
        if (device == null) {
            status("connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if (driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        if (driver.getPorts().size() < portNum) {
            status("connection failed: not enough ports at device");
            return;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if (usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(INTENT_ACTION_GRANT_USB), flags);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE);
            if (withIoManager) {
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
        if (usbIoManager != null) {
            usbIoManager.setListener(null);
            usbIoManager.stop();
        }
        usbIoManager = null;
        try {
            usbSerialPort.close();
        } catch (IOException ignored) {
        }
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

    private void putAlarmWaterText(TextView tv, boolean value) {
        if (value) {
            waterAlarm.setText("Water Level Good");
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
        } else {
            waterAlarm.setText("Water Level Alarm");
            tv.setTextColor(Color.BLACK);
            tv.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.RedAlarmBackground));
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

    //===POST DATA LAYER ================================
    public void postDataLayer() {                           // Take action on all Panel Data
        if (!isAdded() || getContext() == null) return;

        if (panelData.containsKey("bok")) {
            putRedAlarmTextColor(systemOk, panelData.getPanelBool("bok"));
        }
        if (panelData.containsKey("bAlarm")) {
            putRedAlarmTextColorFlash(alarm, panelData.getPanelBool("bAlarm"));
        }
        if (panelData.containsKey("bwater"))   //  Water Alarm Button
            if (panelData.getPanelBool("bAlarm"))
                putWaterLevelText(waterAlarm, true);
            else if (panelData.getPanelBool("bHigh"))
                putHighWaterText(waterAlarm, true);
            else if (panelData.getPanelBool("bLow"))
                putLowWaterText(waterAlarm, false);
            else
                putLowWaterText(waterAlarm, true);

        if (panelData.containsKey("bairalrm")) // Aeration Alarm
            putRedAlarmTextColor(airAlarm, !panelData.getPanelBool("bairalrm"));
        // Buttons
        if (panelData.containsKey("bptest"))
            putTextColor(effPumpTest, panelData.getPanelBool("effstat"));
        if (panelData.containsKey("balmrset"))
            putTextColor(alarmReset, panelData.getPanelBool("balmrset"));
        if (panelData.containsKey("so1"))
            putTextColor(ffTest, panelData.getPanelBool("so1"));
        if (panelData.containsKey("so0"))
            putTextColor(zone1, panelData.getPanelBool("so0"));
        if (panelData.containsKey("so2"))
            putTextColor( zone2, panelData.getPanelBool("so2"));
        if (panelData.containsKey("balrmltch"))
            putTextColor(alarmLatch, panelData.getPanelBool("balrmltch"));
        // Variables
         if (panelData.containsKey("dosesday") && !doseDayCount.hasFocus())
            doseDayCount.setText(String.format(panelData.getPanelString("dosesday")));

        if (panelData.containsKey("fdrun") && !(FdRunTimeCount.hasFocus() || FdRunTimeCountSec.hasFocus()) ) {
            int fdrun;
            int fdrunSec;
            if (panelData.getPanelString("fdrun").contentEquals("")) {
                //FdRunTimeCount.setText("0");
                //FdRunTimeCountSec.setText("0");
            } else {
                fdrun = (parseInt(panelData.getPanelString("fdrun")) / 60);
                fdrunSec = (parseInt(panelData.getPanelString("fdrun")) % 60);
                FdRunTimeCount.setText(Integer.toString(fdrun));
                FdRunTimeCountSec.setText(Integer.toString(fdrunSec));
            }
        }
/*      if (panelData.containsKey("rrepeat") && !recirRepeatCount.hasFocus()) {
            if (panelData.getPanelString("rrepeat").contentEquals("")) {
                int rrepeat = 0;
                recirRepeatCount.setText(String.format("000"));
            } else {
                int rrepeat = parseInt(panelData.getPanelString("rrepeat"));
                recirRepeatCount.setText(String.format("%d", rrepeat / 60));
            }
        }*/
/*      if (panelData.containsKey("rrun") && !(dripRunCount.hasFocus() || dripRunCountSec.hasFocus())) {
            int rrun;
            int rrunSec;
            if (panelData.getPanelString("rrun").contentEquals("")) {
                //recirRunCount.setText(String.format("000"));
                //recirRunCountSec.setText(String.format("00"));
            } else {
                rrun = (parseInt(panelData.getPanelString("rrun")) / 60);
                rrunSec = (parseInt(panelData.getPanelString("rrun")) % 60);
                dripRunCount.setText(Integer.toString(rrun));
                dripRunCountSec.setText(Integer.toString(rrunSec));
            }
        }*/
        if (panelData.containsKey("airpres"))
            airPressure.setText(String.format("Air Compressor Pressure WCI:                                          %s", panelData.getPanelString("airpres")));
        if (panelData.containsKey("palmtime") && !effPumpAlarmTimeCount.hasFocus()) {
            if (panelData.getPanelString("palmtime").contentEquals(""))
                effPumpAlarmTimeCount.setText(String.format("%d", 0));
            else {
                int palmtime = parseInt(panelData.getPanelString("palmtime"));
                effPumpAlarmTimeCount.setText(String.format("%d", palmtime / 60));
            }
        }
        if (panelData.containsKey("zone") && !zoneCount.hasFocus())
            zoneCount.setText(String.format(panelData.getPanelString("zone")));
 /*       if (panelData.containsKey("perdur") && !(peristolticCount.hasFocus() || peristolticCountSec.hasFocus())) {
            int perdur = 0;
            int perdurSec = 0;
            if (panelData.getPanelString("perdur").contentEquals("")) {
                // peristolticCount.setText(String.format("000"));
                // peristolticCountSec.setText(String.format("00"));
            } else {
                perdur = (parseInt(panelData.getPanelString("perdur")) / 60);
                perdurSec = (parseInt(panelData.getPanelString("perdur")) % 60);
                peristolticCount.setText(Integer.toString(perdur));
                peristolticCountSec.setText(Integer.toString(perdurSec));
            }
        }*/
        // Time and Gallons Averages
        if (panelData.containsKey("time"))
            timeRemote.setText(panelData.getPanelString("time"));
        if (panelData.containsKey("life"))
            remoteSec = panelData.getPanelString("life");
        if (panelData.containsKey("lifedays"))
            remoteSec = panelData.getPanelString("lifedays");
        if (panelData.containsKey("lifetimeAvg"))
            remoteSec = panelData.getPanelString("lifetimeAvg");
        if (panelData.containsKey("hours24"))
            remoteSec = panelData.getPanelString("hours24");
        if (panelData.containsKey("hours1"))
            remoteSec = panelData.getPanelString("hours1");
        if (panelData.containsKey("hourAvg"))
            remoteSec = panelData.getPanelString("hourAvg");
        if (panelData.containsKey("day30"))
            remoteSec = panelData.getPanelString("day30");
        if (panelData.containsKey("day30Avg"))
            remoteSec = panelData.getPanelString("day30Avg");
        if (panelData.containsKey("KEY")) {
            if (panelData.getPanelString("KEY").contains("log")) {
                alarmList = panelData.displayFilterLog("log");         // Filter log* to alarm list
            }
        }
    }

    public static String secondsToTimeString(String secondsStr) {
        int totalSeconds;
        try {
            totalSeconds = Integer.parseInt(secondsStr);
        } catch (NumberFormatException e) {
            return "000:00";
        }
        if (totalSeconds < 0) totalSeconds = 0;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%03d:%02d", minutes, seconds);
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
            if (commandListIndex == updateCommandList.size())
                commandListIndex = 0;
        }
    }

    public void showTankPopUp() {
        DialogFragment newFragment = new PopUpFragment();
        assert getParentFragmentManager() != null;
        newFragment.show(getParentFragmentManager(), "tank");
    }

    private void setTextViewFlavor(TextView textview, @NonNull String value) {
        if (value.equalsIgnoreCase("true")) {
            textview.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            textview.setTextColor(Color.BLACK);
        } else {
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

    @NonNull
    private SpannableStringBuilder localTime(int remoteHr, int remoteMin, int remoteSec) {
        SpannableStringBuilder remoteTime = new SpannableStringBuilder();
        remoteTime.append(String.valueOf(remoteHr));
        remoteTime.append(":");
        remoteTime.append(String.valueOf(remoteMin));
        remoteTime.append(":");
        remoteTime.append(String.valueOf(remoteSec));
        return (remoteTime);
    }

    @NonNull
    private SpannableStringBuilder updateTime(String remoteYear, String remoteMonth, String remoteDay, String remoteHr, String remoteMin, String remoteSec) {
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
        return (updateTime);
    }

    private String formatSeconds(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private void showTimeInputDialog() {
        //sendPriorityCommand("rrun", String.valueOf(editText.getText()));

    }

    private int convertToSecondsSafe(String time) {
        try {
            String[] parts = time.split(":");
            int minutes = Integer.parseInt(parts[0]);
            int seconds = Integer.parseInt(parts[1]);
            return minutes * 60 + seconds;
        } catch (Exception e) {
            return -1; // or handle error
        }
    }

    private boolean isValidTimeFormat(String value) {
        return value.matches("^[0-5]?\\d:[0-5]\\d$");
    }

    private void effPumpTestCallback() {
        if (panelData.getPanelBool("effstat")) {
            effPumpTest.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.textOff));
            sendPriorityCommand("effstat", "false");
        } else {
            effPumpTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            sendPriorityCommand("effstat", "true");
        }
    }

    private void alarmResetCallback() {
        sendPriorityCommand("balmrset", "true");
    }

    private void ffTestCallback() {
        if (panelData.getPanelBool("so0")) {
            ffTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendPriorityCommand("so0", "false");
        } else {
            ffTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            sendPriorityCommand("so0", "true");
        }
    }

    private void manualTestCallback(View anchorView) {
        popupManualTest = ManualInputTestHelper.show(this, anchorView, manualInputTest, panelData, this::sendPriorityCommand);
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

/*    private void peristalticTestCallback() {
        if (panelData.getPanelBool("so2")) {
            peristalticTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textOff));
            sendPriorityCommand("so2", "false");
        } else {
            peristalticTest.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textGoodBackground));
            sendPriorityCommand("so2", "true");
        }
    }*/


    private boolean sendJson(String cmd, String value) {
        // int j = 0;
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
        if (!connected) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] data = (str + '\n').getBytes();
            SpannableStringBuilder spn = new SpannableStringBuilder();
            spn.append(str);
            spn.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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
            int len = usbSerialPort.read(buffer, READ_WAIT_MILLIS);*/
    /*            if(len == -1)
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
    public void receive(@NonNull byte[] data) {
        if (data.length > 0) {
            panelData.parse(data, () -> mainLooper.post(postMsg));
        }
    }

    void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.yellow)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        //receiveText.append(spn);
    }
}
