package com.hoho.android.usbserial.examples;

// import com.hoho.android.usbserial.examples.AnrFragment;
import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * Utility class to display a numeric time input popup and convert the time string to seconds (as byte).
 * Includes guards for invalid or malformed input.
 */
public class NumericTimePopup {

    public static void showNumericTimePopup(Context context, String cmd, AnrFragment anrFragment) {
        // Create an EditText for time input
        EditText editText = new EditText(context);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setHint("00:00");
        editText.setText("00:00");
        editText.setSelection(0);

        // Add a TextWatcher to ensure the time format includes ":"
        editText.addTextChangedListener(new TextWatcher() {
            private boolean isEditing = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isEditing) return;
                isEditing = true;

                String text = s.toString().replace(":", "");
                if (text.length() >= 3) {
                    String hours = text.length() >= 2 ? text.substring(0, 2) : "00";
                    String minutes = text.length() > 2 ? text.substring(2, Math.min(4, text.length())) : "00";
                    editText.setText(hours + ":" + minutes);
                    editText.setSelection(editText.getText().length()); // Move cursor to the end
                }
                isEditing = false;
            }
        });

        // Create an AlertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Enter Time");
        builder.setView(createDialogLayout(context, editText));
        builder.setPositiveButton("OK", (dialog, which) -> {
            // Update the target EditText with the entered time
            int seconds = timeStringToIntSeconds(editText.getText().toString());
            anrFragment.sendPriorityCommand(cmd, String.valueOf(seconds));
            // targetEditText.setText(editText.getText().toString());
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // Helper method to create a dialog layout
    private static LinearLayout createDialogLayout(Context context, EditText editText) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(editText);
        return layout;
    }

    /**
     * Converts a time string in the format "MM:SS" to the total number of seconds as a byte.
     * If the input is invalid, returns 0.
     * Note: If total seconds > 127 or < -128, byte overflow will occur.
     */
    public static int timeStringToIntSeconds(String time) {
        if (time == null) {
            return 0;
        }
        String[] parts = time.split(":");
        if (parts.length != 2) {
            return 0;
        }
        int minutes, seconds;
        try {
            minutes = Integer.parseInt(parts[0]);
            seconds = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return 0;
        }
        if (minutes < 0 || seconds < 0 || seconds > 59) {
            return 0;
        }
        int totalSeconds = minutes * 60 + seconds;
        return totalSeconds;
    }
}