package com.hoho.android.usbserial.examples;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;

public class PopUpFragment extends DialogFragment {

//    public boolean dialogHasRun = false;
 //   public boolean isDialogHasRun() { return dialogHasRun;}
 //   public void setDialogHasRun(boolean dialogHasRun) {this.dialogHasRun = dialogHasRun;}

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(" Tank Not Set")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // START THE GAME!
                    }
                })
                .setNegativeButton("Done", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                    }
                });
        // Create the AlertDialog object and return it
       // dialogHasRun = true;
        return builder.create();
    }
    }

