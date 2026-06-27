package com.hoho.android.usbserial.examples;

import android.content.Context;
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
 * Log entries (log1, log2, …) are continuously pushed into panelData by the
 * normal data stream, so the popup reads them directly on open — no serial
 * query or artificial delay needed.
 *
 * On clear : sends "clrlog" to wipe the panel's log store and clears local data.
 *
 * Usage:
 *   alarmHistory.setOnClickListener(v ->
 *       AlarmHistoryPopup.show(getContext(), view, panelData,
 *           () -> sendPriorityCommand("clrlog", "query")));
 */
public class AlarmHistoryPopup {

    private static final int MAX_ACTIVE  = 50;
    private static final int MAX_HISTORY = 200;

    private static void loadAlarms(String prefix, int maxCount, PanelData panelData, TextView alarmTextWindow) {
        String[][] logs = panelData.displayFilterLog(prefix);
        alarmTextWindow.setText("");
        int shown = 0;
        // Iterate highest index first so the most-recent entries appear at the top
        for (int i = logs.length - 1; i >= 0 && shown < maxCount; i--) {
            if (logs[i][1] != null) {
                alarmTextWindow.append(logs[i][1]);
                alarmTextWindow.append("\n");
                shown++;
            }
        }
        if (shown == 0)
            alarmTextWindow.setText("No alarms");
    }

    public static void show(Context context, View anchorView, PanelData panelData,
                            Runnable onClear) {

        View customView = LayoutInflater.from(context).inflate(R.layout.alarm_history, null);

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

        // Initialize with Active (log) alarms - state array for lambda capture
        boolean[] showActive = {true};
        alarmSelect.setText("History");
        loadAlarms("log", MAX_ACTIVE, panelData, alarmTextWindow);

        // Toggle between Active (log) and History (hist) on button click
        alarmSelect.setOnClickListener(v -> {
            showActive[0] = !showActive[0];
            if (showActive[0]) {
                alarmSelect.setText("History");
                clearAlarm.setVisibility(View.VISIBLE);
                loadAlarms("log", MAX_ACTIVE, panelData, alarmTextWindow);
            } else {
                alarmSelect.setText("Active");
                clearAlarm.setVisibility(View.INVISIBLE);
                loadAlarms("hist", MAX_HISTORY, panelData, alarmTextWindow);
            }
        });

        closeAlarmBtn.setOnClickListener(v -> {
            alarmTextWindow.setText("");
            popupWindow.dismiss();
        });

        clearAlarm.setOnClickListener(v -> {
            panelData.deletePanelLogs("log");   // remove local log entries
            panelData.deletePanelLogs("hist");  // remove local hist entries
            alarmTextWindow.setText("");
            if (onClear != null) onClear.run(); // send clrlog to panel
        });
    }
}

