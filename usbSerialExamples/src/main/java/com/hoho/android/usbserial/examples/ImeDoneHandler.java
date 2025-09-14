package com.hoho.android.usbserial.examples;

import android.app.Activity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Handles IME_ACTION_DONE for EditText: submits command, hides keyboard, defocuses safely.
 */
public class ImeDoneHandler implements TextView.OnEditorActionListener {
    private final EditText editText;
    private final View rootView;
    private final String commandKey;
    private final CommandCallback callback;

    public interface CommandCallback {
        void sendCommand(String key, String value);
    }

    public ImeDoneHandler(EditText editText, View rootView, String commandKey, CommandCallback callback) {
        this.editText = editText;
        this.rootView = rootView;
        this.commandKey = commandKey;
        this.callback = callback;

        this.editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        this.editText.setOnEditorActionListener(this);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            if (callback != null) {
                callback.sendCommand(commandKey, editText.getText().toString());
            }

            InputMethodManager imm = (InputMethodManager) editText.getContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);

            // Redirect focus to dummy view if available
            editText.postDelayed(() -> {
                View sink = rootView.findViewById(R.id.focusSink);
                if (sink != null && sink.requestFocus()) {
                    Log.d("ImeDoneHandler", "Focus successfully shifted to dummy view");
                } else {
                    editText.clearFocus();
                    Log.d("ImeDoneHandler", "Fallback to clearFocus()");
                }
            }, 100); // 100ms gives the IME time to finish

            return true;
        }
        return false;
    }
}