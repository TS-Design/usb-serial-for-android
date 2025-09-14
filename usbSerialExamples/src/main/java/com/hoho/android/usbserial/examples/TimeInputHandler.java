package com.hoho.android.usbserial.examples;

import android.content.Context;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Handles mmm:ss input using two EditText fields and submits on IME Done.
 */
public class TimeInputHandler {
    private final EditText minutesInput;
    private final EditText secondsInput;
    private final String displayKey;
    private final String cmdKey;
    private final SubmitCallback callback;

    public interface SubmitCallback {
        void sendCommand(String key, String value);
    }

    public TimeInputHandler(EditText minutesInput, EditText secondsInput,
                            String displayKey, String cmdKey, SubmitCallback callback) {
        this.minutesInput = minutesInput;
        this.secondsInput = secondsInput;
        this.displayKey = displayKey;
        this.cmdKey = cmdKey;
        this.callback = callback;

        setupSecondsListener();
    }

    private void setupSecondsListener() {
        secondsInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        secondsInput.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitTime();
                return true;
            }
            return false;
        });
    }

    private void submitTime() {
        String minStr = minutesInput.getText().toString().trim();
        String secStr = secondsInput.getText().toString().trim();

        int min = minStr.isEmpty() ? 0 : Integer.parseInt(minStr);
        int sec = secStr.isEmpty() ? 0 : Integer.parseInt(secStr);
        if (sec > 59) sec = 59;

        int totalSeconds = min * 60 + sec;
        String formatted = String.format("%d:%02d", min, sec);

        InputMethodManager imm = (InputMethodManager) secondsInput.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(secondsInput.getWindowToken(), 0);
        secondsInput.clearFocus();

        if (callback != null) {
            callback.sendCommand(displayKey, formatted);
            callback.sendCommand(cmdKey, String.valueOf(totalSeconds));
        }
    }
}