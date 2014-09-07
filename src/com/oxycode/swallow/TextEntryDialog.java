package com.oxycode.swallow;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.app.DialogFragment;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

public class TextEntryDialog extends DialogFragment {
    public static interface Listener {
        void onTextEntryDialogOk(String tag, String text);
        void onTextEntryDialogCancel(String tag);
    }

    private EditText _editText;

    public EditText getEditText() {
        return _editText;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Activity context = getActivity();
        @SuppressLint("InflateParams")
        View promptView = context.getLayoutInflater().inflate(R.layout.text_entry_dialog, null);
        Bundle arguments = getArguments();
        String title = arguments.getString("title");
        String okText = arguments.getString("okText");
        if (okText == null) okText = getString(android.R.string.ok);
        String cancelText = arguments.getString("cancelText");
        if (cancelText == null) cancelText = getString(android.R.string.cancel);

        _editText = (EditText)promptView.findViewById(R.id.text_entry_dialog_edittext);

        AlertDialog alertDialog = new AlertDialog.Builder(context)
            .setView(promptView)
            .setTitle(title)
            .setCancelable(false)
            .setPositiveButton(okText,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Listener listener = (Listener)getActivity();
                        String text = _editText.getText().toString();
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
