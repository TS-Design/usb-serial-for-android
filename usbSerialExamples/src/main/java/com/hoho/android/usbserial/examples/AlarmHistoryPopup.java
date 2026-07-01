package com.hoho.android.usbserial.examples;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * Shared helper that shows the Alarm History popup for any fragment.
 *
 * On open: clears local log/hist buffers, shows popup, then sends a priority
 * "log" query.  The panel responds with up to 50 active (log1..log50) and up
 * to 200 history (hist1..hist200) alarms via the normal parse path.
 * A Handler polls panelData every 250 ms and refreshes the display as data
 * arrives.  If no data arrives within TIMEOUT_MILLIS, "No alarms" is shown.
 *
 * Usage:
 *   alarmHistory.setOnClickListener(v ->
 *       AlarmHistoryPopup.show(getContext(), view, panelData,
 *           () -> sendPriorityCommand("log", "query")));
 */
public class AlarmHistoryPopup {

    private static final int    MAX_ACTIVE    = 50;
    private static final int    MAX_HISTORY   = 200;
    private static final int    POLL_MILLIS   = 250;
    private static final int    TIMEOUT_MILLIS = 5000;
    private static final String WAITING       = "Waiting for panel data...";
    private static final String NO_ALARMS     = "No alarms";

    private static int countEntries(PanelData panelData, String prefix) {
        int count = 0;
        for (String[] row : panelData.displayFilterLog(prefix))
            if (row[1] != null) count++;
        return count;
    }

    private static void loadAlarms(String prefix, int maxCount,
                                   PanelData panelData, TextView tv) {
        String[][] logs = panelData.displayFilterLog(prefix);
        tv.setText("");
        int shown = 0;
        for (int i = logs.length - 1; i >= 0 && shown < maxCount; i--) {
            if (logs[i][1] != null) {
                tv.append(logs[i][1]);
                tv.append("\n");
                shown++;
            }
        }
        if (shown == 0)
            tv.setText(NO_ALARMS);
    }

    /** Delay (ms) between sending "clrlog" and sending "log" on clear. */
    private static final int CLEAR_DELAY_MILLIS = 400;

    public static void show(Context context, View anchorView,
                            PanelData panelData,
                            Runnable onRequest,   // sends "log"  — on open and after clear
                            Runnable onClear) {   // sends "clrlog" — priority before reload

        // 1. Clear both local buffers
        panelData.deletePanelLogs("log");
        panelData.deletePanelLogs("hist");

        // 2. Inflate and show popup FIRST, then issue the request
        View customView = LayoutInflater.from(context)
                .inflate(R.layout.alarm_history, null);

        Button   closeAlarmBtn   = customView.findViewById(R.id.closeAlarmBtn);
        Button   clearAlarm      = customView.findViewById(R.id.clearAlarm);
        Button   alarmSelect     = customView.findViewById(R.id.alarmSelect);
        TextView alarmTextWindow = customView.findViewById(R.id.alarmTextWindow);
        TextView textAlarmTime   = customView.findViewById(R.id.textAlarmTime);

        PopupWindow popupWindow = new PopupWindow(
                customView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.showAtLocation(anchorView, Gravity.CENTER, 0, 0);

        alarmTextWindow.setMovementMethod(new ScrollingMovementMethod());
        alarmTextWindow.setVerticalScrollBarEnabled(true);

        if (panelData.containsKey("time"))
            textAlarmTime.setText(panelData.getPanelString("time"));

        alarmTextWindow.setText(WAITING);

        // 3. Send the log request AFTER popup is visible
        if (onRequest != null) onRequest.run();

        // Shared state
        boolean[] showActive  = {true};
        boolean[] dismissed   = {false};
        int[]     lastCount   = {-1};           // -1 = force first poll refresh
        long[]    requestTime = {SystemClock.elapsedRealtime()};

        alarmSelect.setText("History");
        clearAlarm.setVisibility(View.VISIBLE);

        Handler handler = new Handler(Looper.getMainLooper());

        // 4. Poll loop — refreshes display when data changes; times out to "No alarms"
        Runnable pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (dismissed[0]) return;
                String prefix   = showActive[0] ? "log"      : "hist";
                int    maxCount = showActive[0] ? MAX_ACTIVE : MAX_HISTORY;
                int    current  = countEntries(panelData, prefix);

                if (current > 0 && current != lastCount[0]) {
                    lastCount[0] = current;
                    loadAlarms(prefix, maxCount, panelData, alarmTextWindow);
                } else if (current == 0
                        && WAITING.equals(alarmTextWindow.getText().toString())
                        && SystemClock.elapsedRealtime() - requestTime[0] > TIMEOUT_MILLIS) {
                    alarmTextWindow.setText(NO_ALARMS);
                }
                handler.postDelayed(this, POLL_MILLIS);
            }
        };
        handler.postDelayed(pollRunnable, POLL_MILLIS);

        // Toggle Active / History
        alarmSelect.setOnClickListener(v -> {
            showActive[0] = !showActive[0];
            lastCount[0]  = -1;
            if (showActive[0]) {
                alarmSelect.setText("History");
                clearAlarm.setVisibility(View.VISIBLE);
                int cnt = countEntries(panelData, "log");
                if (cnt > 0)
                    loadAlarms("log", MAX_ACTIVE, panelData, alarmTextWindow);
                else if (SystemClock.elapsedRealtime() - requestTime[0] > TIMEOUT_MILLIS)
                    alarmTextWindow.setText(NO_ALARMS);
                else
                    alarmTextWindow.setText(WAITING);
            } else {
                alarmSelect.setText("Active");
                clearAlarm.setVisibility(View.INVISIBLE);
                int cnt = countEntries(panelData, "hist");
                if (cnt > 0)
                    loadAlarms("hist", MAX_HISTORY, panelData, alarmTextWindow);
                else if (SystemClock.elapsedRealtime() - requestTime[0] > TIMEOUT_MILLIS)
                    alarmTextWindow.setText(NO_ALARMS);
                else
                    alarmTextWindow.setText(WAITING);
            }
        });

        closeAlarmBtn.setOnClickListener(v -> {
            dismissed[0] = true;
            handler.removeCallbacks(pollRunnable);
            popupWindow.dismiss();
        });

        // Clear: send "clrlog" to panel, then after a short delay reload via "log"
        clearAlarm.setOnClickListener(v -> {
            panelData.deletePanelLogs("log");
            panelData.deletePanelLogs("hist");
            lastCount[0]   = -1;
            requestTime[0] = SystemClock.elapsedRealtime() + CLEAR_DELAY_MILLIS; // offset timeout
            alarmTextWindow.setText(WAITING);
            if (onClear != null) onClear.run();                    // priority: "clrlog"
            handler.postDelayed(() -> {
                if (!dismissed[0] && onRequest != null) onRequest.run(); // priority: "log"
            }, CLEAR_DELAY_MILLIS);
        });
    }
}

