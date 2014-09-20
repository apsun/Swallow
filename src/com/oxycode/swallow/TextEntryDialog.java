package com.oxycode.swallow;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

public class TextEntryDialog extends DialogFragment {
    public static final String TITLE = "title";
    public static final String OK_TEXT = "okText";
    public static final String CANCEL_TEXT = "cancelText";

    public static interface Listener {
        void onTextEntryDialogOk(String tag, String text);
        void onTextEntryDialogCancel(String tag);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Activity context = getActivity();
        View promptView = context.getLayoutInflater().inflate(R.layout.textedit_dialog, null);
        Bundle arguments = getArguments();
        String title = arguments.getString(TITLE);
        String okText = arguments.getString(OK_TEXT);
        String cancelText = arguments.getString(CANCEL_TEXT);
        if (okText == null) okText = getString(android.R.string.ok);
        if (cancelText == null) cancelText = getString(android.R.string.cancel);

        final EditText editText = (EditText)promptView.findViewById(R.id.text_entry_dialog_edittext);

        AlertDialog alertDialog = new AlertDialog.Builder(context)
            .setView(promptView)
            .setTitle(title)
            .setCancelable(false)
            .setPositiveButton(okText,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Listener listener = (Listener)getActivity();
                        String text = editText.getText().toString();
                        listener.onTextEntryDialogOk(getTag(), text);
                    }
                })
            .setNegativeButton(cancelText,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Listener listener = (Listener)getActivity();
                        listener.onTextEntryDialogCancel(getTag());
                        dialog.cancel();
                    }
                })
            .create();

        alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        return alertDialog;
    }
}
