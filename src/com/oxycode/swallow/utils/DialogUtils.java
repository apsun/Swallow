package com.oxycode.swallow.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import com.oxycode.swallow.R;

public final class DialogUtils {
    public static abstract class ConfirmationDialogHandler {
        public abstract void onConfirm();
        public void onCancel() { }
    }

    public static abstract class TextEntryDialogHandler {
        public abstract void onSubmit(String text);
        public void onCancel() { }
        public boolean isValidInput(String text) {
            return !TextUtils.isEmpty(text);
        }
    }

    private DialogUtils() { }

    public static void showMessageDialog(Activity activity,
                                         int iconId,
                                         String title,
                                         String message) {

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
            .setIcon(iconId)
            .setTitle(title)
            .setMessage(message)
            .setNeutralButton(R.string.ok, null);

        AlertDialog alert = builder.create();
        alert.show();
    }

    public static void showConfirmationDialog(Activity activity,
                                              int iconId,
                                              String title,
                                              String message,
                                              String positiveText,
                                              final ConfirmationDialogHandler handler) {

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
            .setIcon(iconId)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    handler.onConfirm();
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    handler.onCancel();
                }
            });

        AlertDialog alert = builder.create();
        alert.show();
    }

    public static void showTextEntryDialog(Activity activity,
                                           int iconId,
                                           String title,
                                           String positiveText,
                                           final TextEntryDialogHandler handler) {

        LayoutInflater layoutInflater = activity.getLayoutInflater();
        View promptView = layoutInflater.inflate(R.layout.textedit_dialog, null);
        final EditText editText = (EditText)promptView.findViewById(R.id.textedit_dialog_edittext);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
            .setView(promptView)
            .setIcon(iconId)
            .setTitle(title)
            .setPositiveButton(positiveText, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    handler.onSubmit(editText.getText().toString());
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    handler.onCancel();
                }
            });

        final AlertDialog alert = builder.create();

        // Initially disable the positive button (since the textbox is empty)
        alert.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                Button button = alert.getButton(DialogInterface.BUTTON_POSITIVE);
                boolean isValid = handler.isValidInput(editText.getText().toString());
                button.setEnabled(isValid);
            }
        });

        // Enable/disable the positive button depending on whether the textbox is empty
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                Button button = alert.getButton(DialogInterface.BUTTON_POSITIVE);
                boolean isValid = handler.isValidInput(s.toString());
                button.setEnabled(isValid);
            }
        });

        // Display the keyboard when the alert is shown
        alert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        alert.show();
    }
}
