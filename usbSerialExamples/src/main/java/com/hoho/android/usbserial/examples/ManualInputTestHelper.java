package com.hoho.android.usbserial.examples;

import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.PopupWindow;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class ManualInputTestHelper {

    @FunctionalInterface
    public interface CommandSender {
        void send(String cmd, String value);
    }

    /**
     * Inflates and shows the manual input test popup for the given fragment.
     * Wires up all button listeners and a 250 ms color-sync Runnable.
     *
     * @param fragment      the hosting Fragment (used for context, isAdded checks)
     * @param anchorView    the view passed to PopupWindow.showAtLocation()
     * @param manualTestBtn the button that triggered the popup (hidden while open)
     * @param panelData     live panel state
     * @param sendCommand   abstraction over sendPriorityCommand / sendJson
     * @return the created PopupWindow (caller should store it for lifecycle cleanup)
     */
    public static PopupWindow show(
            Fragment fragment,
            View anchorView,
            Button manualTestBtn,
            PanelData panelData,
            CommandSender sendCommand) {

        LayoutInflater inflater = LayoutInflater.from(fragment.requireContext());
        View customView = inflater.inflate(R.layout.manual_input_popup, null);

        Button closeBtn   = customView.findViewById(R.id.closeManualInputBtn);
        Button yellowInput = customView.findViewById(R.id.yellowInput);
        Button blueInput   = customView.findViewById(R.id.blueInput);
        Button redInput    = customView.findViewById(R.id.redInput);

        PopupWindow popup = new PopupWindow(customView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        popup.showAtLocation(anchorView, Gravity.BOTTOM | Gravity.RIGHT, 0, 0);

        manualTestBtn.setVisibility(View.INVISIBLE);
        sendCommand.send("bENA", "true");

        boolean[] dismissed = {false};
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable syncColors = new Runnable() {
            @Override
            public void run() {
                if (dismissed[0] || !fragment.isAdded() || fragment.getContext() == null) return;
                yellowInput.setBackgroundColor(ContextCompat.getColor(fragment.requireContext(),
                        panelData.getPanelBool("bLow") ? R.color.textGoodBackground : R.color.yellow));
                blueInput.setBackgroundColor(ContextCompat.getColor(fragment.requireContext(),
                        panelData.getPanelBool("bHigh") ? R.color.light_blue_900 : R.color.textGoodBackground));
                redInput.setBackgroundColor(ContextCompat.getColor(fragment.requireContext(),
                        panelData.getPanelBool("bAlarm") ? R.color.red : R.color.textGoodBackground));
                handler.postDelayed(this, 250);
            }
        };
        handler.post(syncColors);

        closeBtn.setOnClickListener(v -> {
            dismissed[0] = true;
            handler.removeCallbacks(syncColors);
            sendCommand.send("bENA", "false");
            manualTestBtn.setVisibility(View.VISIBLE);
            popup.dismiss();
        });

        yellowInput.setOnClickListener(v -> {
            if (panelData.getPanelBool("bLow")) {
                yellowInput.setBackgroundColor(ContextCompat.getColor(fragment.requireContext(), R.color.textGoodBackground));
                sendCommand.send("bLowUi", "false");
            } else {
                yellowInput.setBackgroundColor(ContextCompat.getColor(fragment.requireContext(), R.color.yellow));
                sendCommand.send("bLowUi", "true");
            }
        });

        blueInput.setOnClickListener(v -> {
            if (panelData.getPanelBool("bHigh")) {
                blueInput.setBackgroundColor(ContextCompat.getColor(fragment.requireContext(), R.color.textGoodBackground));
                sendCommand.send("bHighUi", "false");
            } else {
                blueInput.setBackgroundColor(ContextCompat.getColor(fragment.requireContext(), R.color.light_blue_900));
                sendCommand.send("bHighUi", "true");
            }
        });

        redInput.setOnClickListener(v -> {
            if (panelData.getPanelBool("bAlarm")) {
                redInput.setBackgroundColor(ContextCompat.getColor(fragment.requireContext(), R.color.textGoodBackground));
                sendCommand.send("bAlarmUi", "false");
            } else {
                redInput.setBackgroundColor(ContextCompat.getColor(fragment.requireContext(), R.color.red));
                sendCommand.send("bAlarmUi", "true");
            }
        });

        return popup;
    }
}
