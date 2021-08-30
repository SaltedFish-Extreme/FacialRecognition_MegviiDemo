package com.example.myapplication.util;

import android.app.Dialog;
import android.content.Context;
import android.widget.TextView;

import com.example.myapplication.R;

import androidx.annotation.NonNull;

public class LoadDialog extends Dialog {
    private final TextView tvContent;

    public LoadDialog(@NonNull Context context) {
        super(context, R.style.dialog);
        setContentView(R.layout.dialog_load);
        setCanceledOnTouchOutside(false);
        tvContent = findViewById(R.id.tvDialogContent);
    }

    public void showloading(String content) {
        tvContent.setText(content);
        super.show();
    }

    public void cancelloading() {
        super.dismiss();
    }
}
