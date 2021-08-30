package com.example.myapplication.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.widget.Toast;

import com.example.myapplication.R;
import com.example.myapplication.util.LoadDialog;
import com.srp.AuthApi.AuthApi;
import com.srp.AuthApi.ErrorCodeConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class InitActivity extends AppCompatActivity {

    private static final String CERT_PATH = "Download/ddd.cert";
    private static final int PERMISSIONS_REQUEST = 1;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_WRITE_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final String[] Permissions = new String[]{PERMISSION_CAMERA, PERMISSION_WRITE_STORAGE};
    private final AuthApi obj = new AuthApi();
    private LoadDialog loadDialog;
    private boolean flag = true;

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_init);
        checkPermission();
    }

    /**
     * 判断程序是否有所需权限
     */
    private void checkPermission() {
        int hasWriteStoragePermission = ContextCompat.checkSelfPermission(getApplication(), PERMISSION_WRITE_STORAGE);
        int hasCameraPermission = ContextCompat.checkSelfPermission(getApplication(), PERMISSION_CAMERA);
        if (hasWriteStoragePermission == PackageManager.PERMISSION_GRANTED && hasCameraPermission == PackageManager.PERMISSION_GRANTED) {
            //拥有权限，执行操作
            judgmentActivation();
        } else {
            //没有权限，向用户请求权限
            ActivityCompat.requestPermissions(this, Permissions, PERMISSIONS_REQUEST);
        }
    }

    /**
     * 权限回调
     *
     * @param requestCode  请求码
     * @param permissions  请求权限
     * @param grantResults 请求结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int grantResult : grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                //未同意授权
                flag = false;
                break;
            }
        }
        if (grantResults.length > 0 && flag) {
            //授权成功
            judgmentActivation();
        } else {
            //授权失败
            toast(getString(R.string.NeedPermissions));
        }
    }

    /**
     * 判断设备是否激活过
     */
    private void judgmentActivation() {
        if (obj.authCheck(this)) {
            //激活跳转主页
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            //未激活进行激活认证
            singleCertification();
        }
    }

    /**
     * 进行认证
     */
    private void singleCertification() {
        String cert = readExternal(CERT_PATH).trim();
        if (TextUtils.isEmpty(cert)) {
            toast(getString(R.string.CertFile_Error));
            return;
        }
        loadDialog = new LoadDialog(this);
        runOnUiThread(() -> loadDialog.showloading(getString(R.string.Updating)));
        obj.authDevice(this.getApplicationContext(), cert, "", result -> {
            if (result.errorCode == ErrorCodeConfig.AUTH_SUCCESS) {
                loadDialog.cancelloading();
                runOnUiThread(() -> toast(getString(R.string.Update_Success)));
                enterMainactivity();
            } else {
                loadDialog.cancelloading();
                runOnUiThread(() -> toast(getString(R.string.Update_Fail, result.errorCode, result.errorMessage)));
            }
        });
    }

    /**
     * 读取认证文件
     *
     * @param filename 文件相对路径
     * @return 文件内容
     */
    public String readExternal(String filename) {
        StringBuilder sb = new StringBuilder();
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            filename = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + filename;
            File file = new File(filename);
            if (!file.exists()) {
                return "";
            }
            try (FileInputStream inputStream = new FileInputStream(filename)) {
                byte[] buffer = new byte[1024];
                int len = inputStream.read(buffer);
                while (len > 0) {
                    sb.append(new String(buffer, 0, len));
                    len = inputStream.read(buffer);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    /**
     * 认证成功进入主页
     */
    private void enterMainactivity() {
        if (obj.authCheck(this)) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            this.finish();
        }
    }
}