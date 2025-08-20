package com.hoho.android.usbserial.examples;

//inport com.hoho.android.usbserial.examples.anrFragment;
import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;

public class NumericTimePopup {

    public static void showNumericTimePopup(Context context, EditText targetEditText, AnrFragment anrFragment) {
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
            anrFragment.sendPriorityCommand("rrun", String.valueOf(editText.getText()));
           //anrFragment.sendPriorityCommand();
            //targetEditText.setText(editText.getText().toString());
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
}