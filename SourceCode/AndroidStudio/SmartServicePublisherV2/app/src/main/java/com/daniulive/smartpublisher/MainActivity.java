/*
 * MainActivity.java
 * MainActivity
 * 
 * Github: https://github.com/daniulive/SmarterStreaming
 * 
 * Created by DaniuLive on 2016/12/12.
 * Copyright © 2014~2018 DaniuLive. All rights reserved.
 */

package com.daniulive.smartpublisher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

public class MainActivity extends Activity {
    MediaProjectionManager mMediaProjectionManager;

    private static final int REQUEST_MEDIA_PROJECTION = 1;

    private static final String TAG = "DaniuliveActivity";
    static int mResultCode;
    static Intent mResultData;

    private Spinner screenResolutionSelector;
    private Spinner recorderSelector;

    private Button btnRecorderMgr;
    private Button btnEncoderType;
    private Button btnInputPushUrl;

    private Button btnPermissionCheck;
    private Button btnPublisher;

    private boolean is_need_local_recorder = false; //默认不录像
    private boolean is_hardware_encoder = false;    //默认软编码
    private boolean is_running = false;

    final private String baseURL = "rtmp://player.daniulive.com:1935/hls/stream";
    private String inputPushURL = "";
    private TextView textCurURL = null;
    private String printText = "URL:";

    private String publishURL = "rtmp://player.daniulive.com:1935/hls/streamservice";

    private final int SCREEN_RESOLUTION_STANDARD = 0;
    private final int SCREEN_RESOLUTION_LOW = 1;

    private int screenResolution = SCREEN_RESOLUTION_STANDARD;

    private String recDir = "/sdcard/daniulive/rec"; // for recorder path

    private Intent intent_bgd_service = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        intent_bgd_service = new Intent(MainActivity.this, BackgroudService.class);

        screenResolutionSelector = (Spinner) findViewById(R.id.screen_resolution_selctor);
        final String[] sceenResolutionSel = new String[]{"屏幕标准分辨率", "屏幕低分辨率"};
        ArrayAdapter<String> adapterScreenResolution = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, sceenResolutionSel);
        adapterScreenResolution
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        screenResolutionSelector.setAdapter(adapterScreenResolution);

        screenResolutionSelector
                .setOnItemSelectedListener(new OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                                               View view, int position, long id) {

                        if (is_running) {
                            Log.e(TAG,
                                    "Could not switch screen resolution during publishing..");
                            return;
                        }

                        Log.i(TAG, "[推送屏幕分辨率]Currently choosing: "
                                + sceenResolutionSel[position]);

                        SwitchScreenResolution(position);

                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {

                    }
                });

        // Recorder related settings
        recorderSelector = (Spinner) findViewById(R.id.recoder_selctor);

        final String[] recoderSel = new String[]{"本地不录像", "本地录像"};
        ArrayAdapter<String> adapterRecoder = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, recoderSel);

        adapterRecoder
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recorderSelector.setAdapter(adapterRecoder);

        recorderSelector
                .setOnItemSelectedListener(new OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                                               View view, int position, long id) {

                        Log.i(TAG, "Currently choosing: "
                                + recoderSel[position]);

                        if (1 == position) {
                            is_need_local_recorder = true;
                        } else {
                            is_need_local_recorder = false;
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {

                    }
                });

        btnRecorderMgr = (Button) findViewById(R.id.button_recoder_manage);
        btnRecorderMgr.setOnClickListener(new ButtonRecorderMangerListener());

        btnEncoderType = (Button) findViewById(R.id.button_hwencoder);
        btnEncoderType.setOnClickListener(new ButtonHardwareEncoderListener());

        btnInputPushUrl = (Button) findViewById(R.id.button_input_push_url);
        btnInputPushUrl.setOnClickListener(new ButtonInputPushUrlListener());

        btnPermissionCheck = (Button) findViewById(R.id.permission_check);
        btnPermissionCheck.setOnClickListener(new OnClickListener() {
            @SuppressLint("NewApi")
            @Override
            public void onClick(View v) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mMediaProjectionManager = (MediaProjectionManager) getApplicationContext()
                            .getSystemService(MEDIA_PROJECTION_SERVICE);
                    startActivityForResult(mMediaProjectionManager
                                    .createScreenCaptureIntent(),
                            REQUEST_MEDIA_PROJECTION);
                }
            }
        });

        btnPublisher = (Button) findViewById(R.id.button_start_stop_capture);
        btnPublisher.setEnabled(false);
        btnPublisher.setOnClickListener(new OnClickListener() {

            @SuppressLint("NewApi")
            @Override
            public void onClick(View v) {
                if (!is_running) {
                    Log.i(TAG, "Start publish screen++");

                    intent_bgd_service.putExtra("SCREENRESOLUTION", screenResolution);

                    if (inputPushURL != null && inputPushURL.length() > 1) {
                        publishURL = inputPushURL;
                        Log.i(TAG, "start, input publish url:" + publishURL);
                    } else {
                        publishURL = baseURL
                                + String.valueOf((int) (System
                                .currentTimeMillis() % 1000000));
                        Log.i(TAG, "start, generate random url:" + publishURL);

                    }

                    printText = "URL:" + publishURL;

                    Log.i(TAG, printText);

                    textCurURL = (TextView) findViewById(R.id.txtCurURL);
                    textCurURL.setText(printText);

                    intent_bgd_service.putExtra("PUBLISHURL", publishURL);

                    intent_bgd_service.putExtra("RECORDER", is_need_local_recorder);    //是否录像
                    intent_bgd_service.putExtra("HWENCODER", is_hardware_encoder);      //软编还是硬编

                    startService(intent_bgd_service);

                    is_running = true;
                    btnPublisher.setText("停止推屏");
                    screenResolutionSelector.setEnabled(false);
                    recorderSelector.setEnabled(false);
                    btnRecorderMgr.setEnabled(false);
                    btnInputPushUrl.setEnabled(false);
                    btnEncoderType.setEnabled(false);
                    Log.i(TAG, "Start publish screen--");
                } else {
                    Log.i(TAG, "Stop publisher screen++");
                    stopService(intent_bgd_service);
                    is_running = false;
                    btnPublisher.setText("开始推屏");
                    btnPublisher.setEnabled(false);
                    screenResolutionSelector.setEnabled(true);
                    btnPermissionCheck.setEnabled(true);

                    recorderSelector.setEnabled(true);
                    btnRecorderMgr.setEnabled(true);
                    btnInputPushUrl.setEnabled(true);
                    btnEncoderType.setEnabled(true);
                    Log.i(TAG, "Stop publisher screen--");
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Log.e(TAG, "User cancelled");
                Toast.makeText(this, "User cancelled", Toast.LENGTH_SHORT)
                        .show();
                return;
            }

            mResultCode = resultCode;
            mResultData = data;
            btnPermissionCheck.setEnabled(false);
            btnPublisher.setEnabled(true);
        }
    }

    void SwitchScreenResolution(int position) {
        Log.i(TAG, "Current Screen Resolution position: " + position);

        switch (position) {
            case 0:
                screenResolution = SCREEN_RESOLUTION_STANDARD;
                break;
            case 1:
                screenResolution = SCREEN_RESOLUTION_LOW;
                break;
            default:
                screenResolution = SCREEN_RESOLUTION_STANDARD;
        }
    }

    class ButtonHardwareEncoderListener implements OnClickListener {
        public void onClick(View v) {
            is_hardware_encoder = !is_hardware_encoder;

            if (is_hardware_encoder)
                btnEncoderType.setText("当前硬编码");
            else
                btnEncoderType.setText("当前软编码");
        }
    }

    private void PopInputUrlDialog() {
        final EditText inputUrlTxt = new EditText(this);
        inputUrlTxt.setFocusable(true);
        inputUrlTxt.setText(baseURL
                + String.valueOf((int) (System.currentTimeMillis() % 1000000)));

        AlertDialog.Builder builderUrl = new AlertDialog.Builder(this);
        builderUrl.setTitle("如 rtmp://player.daniulive.com:1935/hls/stream123456")
                .setView(inputUrlTxt).setNegativeButton("取消", null);

        builderUrl.setPositiveButton("确认",
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        String fullPushUrl = inputUrlTxt.getText().toString();
                        SaveInputUrl(fullPushUrl);
                    }
                });

        builderUrl.show();
    }

    private void SaveInputUrl(String url) {
        inputPushURL = "";

        if (url == null)
            return;

        // rtmp://
        if (url.length() < 8) {
            Log.e(TAG, "Input publish url error:" + url);
            return;
        }

        if (!url.startsWith("rtmp://")) {
            Log.e(TAG, "Input publish url error:" + url);
            return;
        }

        inputPushURL = url;
    }

    class ButtonInputPushUrlListener implements OnClickListener {
        public void onClick(View v) {
            PopInputUrlDialog();
        }
    }

    class ButtonRecorderMangerListener implements OnClickListener {
        public void onClick(View v) {
            Intent intent_rec = new Intent();
            intent_rec.setClass(MainActivity.this, RecorderManager.class);
            intent_rec.putExtra("RecorderDir", recDir);
            startActivity(intent_rec);
        }
    }

}
