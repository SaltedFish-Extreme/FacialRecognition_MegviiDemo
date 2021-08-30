package com.example.myapplication.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.myapplication.R;
import com.example.myapplication.adapter.FaceTokenAdapter;
import com.example.myapplication.adapter.GroupNameAdapter;
import com.example.myapplication.camera.CameraManager;
import com.example.myapplication.camera.CameraPreview;
import com.example.myapplication.camera.CameraPreviewData;
import com.example.myapplication.util.FaceView;
import com.example.myapplication.util.GlideEngine;
import com.example.myapplication.util.SettingVar;
import com.huantansheng.easyphotos.EasyPhotos;
import com.huantansheng.easyphotos.callback.SelectCallback;
import com.huantansheng.easyphotos.models.album.entity.Photo;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import androidx.appcompat.app.AppCompatActivity;
import butterknife.BindView;
import butterknife.ButterKnife;
import mcv.facepass.FacePassException;
import mcv.facepass.FacePassHandler;
import mcv.facepass.types.FacePassAddFaceResult;
import mcv.facepass.types.FacePassConfig;
import mcv.facepass.types.FacePassDetectionResult;
import mcv.facepass.types.FacePassFace;
import mcv.facepass.types.FacePassImage;
import mcv.facepass.types.FacePassImageType;
import mcv.facepass.types.FacePassModel;
import mcv.facepass.types.FacePassPose;
import mcv.facepass.types.FacePassRecognitionResult;
import mcv.facepass.types.FacePassRecognitionState;

import static com.example.myapplication.util.SettingVar.cameraFacingFront;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Activity.this";
    /**
     * 选择的图片集
     */
    private final ArrayList<Photo> selectedPhotoList = new ArrayList<>();
    FacePassHandler mFacePassHandler;
    /**
     * 检测队列
     */
    ArrayBlockingQueue<CameraPreviewData> mFeedFrameQueue_rgb;
    ArrayBlockingQueue<CameraPreviewData> mFeedFrameQueue_ir;
    /**
     * 识别队列
     */
    ArrayBlockingQueue<byte[]> mDetectResultQueue;
    /**
     * 检测线程
     */
    FeedFrameThread mFeedFrameThread;
    /**
     * 识别线程
     */
    RecognizeThread mRecognizeThread;
    @BindView(R.id.iv_group)
    ImageView ivGroup;
    @BindView(R.id.iv_face)
    ImageView ivFace;
    @BindView(R.id.iv_log)
    ImageView ivLog;
    @BindView(R.id.preview_rgb)
    CameraPreview preview_rgb;
    @BindView(R.id.preview_ir)
    CameraPreview preview_ir;
    @BindView(R.id.fcview)
    FaceView faceView;
    @BindView(R.id.tv_meg)
    TextView tvMeg;
    @BindView(R.id.scrollView)
    ScrollView scrollView;
    /* 相机实例 */
    private CameraManager manager_rgb;
    private CameraManager manager_ir;
    /*Toast*/
    private Toast mRecoToast;
    /*初始组名*/
    private String group_name = "facepass";
    /*人脸操作的FaceToken*/
    private byte[] faceToken;
    /*底库操作弹框*/
    private AlertDialog mGroupOperationDialog;
    /*人脸操作弹框*/
    private AlertDialog mFaceOperationDialog;
    /*显示隐藏人脸日志*/
    private boolean buttonFlag = true;
    /*显示隐藏底库详情*/
    private boolean listview = false;
    private long exitTime = 0;
    private Handler mAndroidHandler;

    /**
     * 关闭虚拟键盘
     *
     * @param edSearch 输入文本框
     * @param mContext 上下文对象
     */
    public static void closeKeybord(EditText edSearch, Context mContext) {
        InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(edSearch.getWindowToken(), 0);
    }

    private void toast(String msg) {
        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mDetectResultQueue = new ArrayBlockingQueue<>(5);
        mFeedFrameQueue_rgb = new ArrayBlockingQueue<>(1);
        mFeedFrameQueue_ir = new ArrayBlockingQueue<>(1);
        mAndroidHandler = new Handler();
        initView();
        initFaceHandler();
        mFeedFrameThread = new FeedFrameThread();
        mFeedFrameThread.start();
        mRecognizeThread = new RecognizeThread();
        mRecognizeThread.start();
        ivFace.setOnClickListener(v -> showFaceDialog());
        ivGroup.setOnClickListener(v -> showGroupDialog());
        ivLog.setOnClickListener(v -> ShowRecognitionLog());
    }

    @Override
    protected void onResume() {
        /* 打开相机 */
        manager_rgb.open(getWindowManager(), false, SettingVar.mWidth, SettingVar.mHeight);
        manager_ir.open(getWindowManager(), true, SettingVar.mWidth, SettingVar.mHeight);
        super.onResume();
    }

    @Override
    protected void onStop() {
        mDetectResultQueue.clear();
        if (manager_rgb != null) {
            manager_rgb.release();
        }
        if (manager_ir != null) {
            manager_ir.release();
        }
        super.onStop();
    }

    @Override
    protected void onRestart() {
        faceView.clear();
        faceView.invalidate();
        super.onRestart();
    }

    @Override
    protected void onDestroy() {
        mRecognizeThread.isInterrupt = true;
        mFeedFrameThread.isInterrupt = true;
        mRecognizeThread.interrupt();
        mFeedFrameThread.interrupt();
        if (manager_rgb != null) {
            manager_rgb.release();
        }
        if (manager_ir != null) {
            manager_ir.release();
        }
        if (mAndroidHandler != null) {
            mAndroidHandler.removeCallbacksAndMessages(null);
        }
        if (mFacePassHandler != null) {
            mFacePassHandler.release();
        }
        super.onDestroy();
    }

    /**
     * 初始化视图
     */
    private void initView() {
        getDeviceDensity();
        manager_rgb = new CameraManager();
        manager_ir = new CameraManager();
        manager_rgb.setPreviewDisplay(preview_rgb);
        manager_ir.setPreviewDisplay(preview_ir);
        manager_rgb.setListener(cameraPreviewData -> mFeedFrameQueue_rgb.offer(cameraPreviewData));
        manager_ir.setListener(cameraPreviewData -> mFeedFrameQueue_ir.offer(cameraPreviewData));
    }

    /**
     * 初始化人脸识别程序
     */
    private void initFaceHandler() {
        FacePassHandler.initSDK(getApplicationContext());
        new Thread() {
            @Override
            public void run() {
                while (!isFinishing()) {
                    if (FacePassHandler.isAvailable()) {
                        FacePassConfig config;
                        try {
                            /* 填入所需要的模型配置 */
                            config = new FacePassConfig();
                            config.poseBlurModel = FacePassModel.initModel(getApplicationContext().getAssets(), "attr.pose_blur.arm.190630.bin");
                            config.livenessModel = FacePassModel.initModel(getApplicationContext().getAssets(), "liveness.CPU.rgb.G.bin");
                            config.rgbIrLivenessModel = FacePassModel.initModel(getApplicationContext().getAssets(), "liveness.CPU.rgbir.G.bin");
                            config.searchModel = FacePassModel.initModel(getApplicationContext().getAssets(), "feat2.arm.J2.v1.0_1core.bin");
                            config.detectModel = FacePassModel.initModel(getApplicationContext().getAssets(), "detector.arm.G.bin");
                            config.detectRectModel = FacePassModel.initModel(getApplicationContext().getAssets(), "detector_rect.arm.G.bin");
                            config.landmarkModel = FacePassModel.initModel(getApplicationContext().getAssets(), "pf.lmk.arm.E.bin");
                            config.rcAttributeModel = FacePassModel.initModel(getApplicationContext().getAssets(), "attr.RC.arm.E.bin");
                            config.occlusionFilterModel = FacePassModel.initModel(getApplicationContext().getAssets(), "attr.occlusion.arm.20201209.bin");
                            /* 送入识别阈值参数 */
                            config.rcAttributeAndOcclusionMode = 3;
                            config.searchThreshold = 65f;
                            config.livenessThreshold = 80f;
                            config.livenessEnabled = false;
                            config.rgbIrLivenessEnabled = true;
                            config.retryCount = 5;

                            config.poseThreshold = new FacePassPose(35f, 35f, 35f);
                            config.blurThreshold = 0.8f;
                            config.lowBrightnessThreshold = 30f;
                            config.highBrightnessThreshold = 210f;
                            config.brightnessSTDThreshold = 80f;
                            config.faceMinThreshold = 100;
                            config.maxFaceEnabled = true;

                            config.fileRootPath = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();

                            /* 创建SDK实例 */
                            mFacePassHandler = new FacePassHandler(config);

                            /* 入库阈值参数 */
                            FacePassConfig addFaceConfig = mFacePassHandler.getAddFaceConfig();
                            addFaceConfig.poseThreshold.pitch = 35f;
                            addFaceConfig.poseThreshold.roll = 35f;
                            addFaceConfig.poseThreshold.yaw = 35f;
                            addFaceConfig.blurThreshold = 0.7f;
                            addFaceConfig.lowBrightnessThreshold = 70f;
                            addFaceConfig.highBrightnessThreshold = 220f;
                            addFaceConfig.brightnessSTDThreshold = 60f;
                            addFaceConfig.faceMinThreshold = 100;
                            addFaceConfig.rcAttributeAndOcclusionMode = 2;
                            mFacePassHandler.setAddFaceConfig(addFaceConfig);

                            initGroup();
                        } catch (FacePassException e) {
                            e.printStackTrace();
                            toast(getString(R.string.FacePassHandlerIsNull));
                            return;
                        }
                        return;
                    }
                    try {
                        /* 如果SDK初始化未完成则需等待 */
                        sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    /**
     * 初始化底库
     */
    private void initGroup() {
        try {
            String[] groups = mFacePassHandler.getLocalGroups();
            if (mFacePassHandler == null) {
                toast(getString(R.string.FacePassHandlerIsNull));
                return;
            }
            if (groups == null || groups.length == 0) {
                mFacePassHandler.createLocalGroup(group_name);
            }
        } catch (FacePassException e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示人脸识别框
     *
     * @param detectResult 检测信息
     */
    private void showFacePassFace(FacePassFace[] detectResult) {
        faceView.clear();
        for (FacePassFace face : detectResult) {
            boolean mirror = cameraFacingFront; /* 前摄像头时mirror为true */
            StringBuilder faceIdString = new StringBuilder();
            faceIdString.append(getString(R.string.ID)).append(face.trackId);
            SpannableString faceViewString = new SpannableString(faceIdString);
            faceViewString.setSpan(null, 0, faceViewString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            Matrix mat = new Matrix();
            int w = preview_rgb.getMeasuredWidth();
            int h = preview_rgb.getMeasuredHeight();

            int cameraHeight = manager_rgb.getCameraheight();
            int cameraWidth = manager_rgb.getCameraWidth();

            float left = 0;
            float top = 0;
            float right = 0;
            float bottom = 0;

            switch (SettingVar.faceRotation) {
                case 0:
                    left = face.rect.left;
                    top = face.rect.top;
                    right = face.rect.right;
                    bottom = face.rect.bottom;
                    mat.setScale(mirror ? -1 : 1, 1);
                    mat.postTranslate(mirror ? (float) cameraWidth : 0f, 0f);
                    mat.postScale((float) w / (float) cameraWidth, (float) h / (float) cameraHeight);
                    break;
                case 90:
                    mat.setScale(mirror ? -1 : 1, 1);
                    mat.postTranslate(mirror ? (float) cameraHeight : 0f, 0f);
                    mat.postScale((float) w / (float) cameraHeight, (float) h / (float) cameraWidth);
                    left = face.rect.top;
                    top = cameraWidth - face.rect.right;
                    right = face.rect.bottom;
                    bottom = cameraWidth - face.rect.left;
                    break;
                case 180:
                    mat.setScale(1, mirror ? -1 : 1);
                    mat.postTranslate(0f, mirror ? (float) cameraHeight : 0f);
                    mat.postScale((float) w / (float) cameraWidth, (float) h / (float) cameraHeight);
                    left = face.rect.right;
                    top = face.rect.bottom;
                    right = face.rect.left;
                    bottom = face.rect.top;
                    break;
                case 270:
                    mat.setScale(mirror ? -1 : 1, 1);
                    mat.postTranslate(mirror ? (float) cameraHeight : 0f, 0f);
                    mat.postScale((float) w / (float) cameraHeight, (float) h / (float) cameraWidth);
                    left = cameraHeight - face.rect.bottom;
                    top = face.rect.left;
                    right = cameraHeight - face.rect.top;
                    bottom = face.rect.right;
            }
            RectF drect = new RectF();
            RectF srect = new RectF(left, top, right, bottom);
            mat.mapRect(drect, srect);
            faceView.addRect(drect);
            faceView.addId(faceIdString.toString());
            faceView.addRoll(getString(R.string.Spin) + (int) face.pose.roll + "°");
            faceView.addPitch(getString(R.string.Seesaw) + (int) face.pose.pitch + "°");
            faceView.addYaw(getString(R.string.Around) + (int) face.pose.yaw + "°");
            faceView.addBlur(getString(R.string.Blur) + face.blur);
        }
        faceView.invalidate();
    }

    /**
     * 通过人像库唯一标识获取人像图片
     *
     * @param trackId   识别到的一个人的ID
     * @param faceToken 识别到的人像库的唯一标识
     */
    private void getFaceImageByFaceToken(final long trackId, String faceToken) {
        if (TextUtils.isEmpty(faceToken)) {
            return;
        }
        try {
            final Bitmap bitmap = mFacePassHandler.getFaceImage(faceToken.getBytes());
            mAndroidHandler.post(() -> showToast(getString(R.string.ID) + trackId, Toast.LENGTH_SHORT, true, bitmap, true));
        } catch (FacePassException e) {
            e.printStackTrace();
        }
    }

    /**
     * 弹框显示识别信息
     *
     * @param text      识别ID
     * @param duration  显示时间
     * @param isSuccess 是否成功
     * @param bitmap    对比图像
     * @param isShow    是否显示文字信息
     */
    public void showToast(CharSequence text, int duration, boolean isSuccess, Bitmap bitmap, boolean isShow) {
        LayoutInflater inflater = getLayoutInflater();
        View toastView = inflater.inflate(R.layout.toast, null);
        LinearLayout toastLayout = toastView.findViewById(R.id.toastll);
        if (toastLayout == null) {
            return;
        }
        toastLayout.getBackground().setAlpha(100);
        ImageView imageView = toastView.findViewById(R.id.toastImageView);
        TextView stateView = toastView.findViewById(R.id.toastState);
        TextView idTextView = toastView.findViewById(R.id.toastTextView);
        if (!isShow) {
            stateView.setVisibility(View.GONE);
            idTextView.setVisibility(View.GONE);
        } else {
            SpannableString s;
            if (isSuccess) {
                s = new SpannableString(getString(R.string.Verified_Successfully));
            } else {
                s = new SpannableString(getString(R.string.Verification_Failed));
            }
            stateView.setText(s);
            idTextView.setText(text);
        }
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }
        if (mRecoToast == null) {
            mRecoToast = new Toast(getApplicationContext());
        }
        mRecoToast.setDuration(duration);
        mRecoToast.setView(toastView);
        mRecoToast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        mRecoToast.show();
    }

    /**
     * 列表输出识别结果信息
     *
     * @param trackId       识别到的一个人的ID
     * @param searchScore   识别分
     * @param livenessScore 活体分
     * @param isRecognizeOK 识别正常
     */
    private void showRecognizeResult(final long trackId, final float searchScore, final float livenessScore, final boolean isRecognizeOK) {
        mAndroidHandler.post(() -> {
            tvMeg.append(getString(R.string.ID) + trackId + " " + (isRecognizeOK ? getString(R.string.Recognized_Successfully) : getString(R.string.Recognized_Failed)));
            tvMeg.append(getString(R.string.Identification_Score) + searchScore);
            tvMeg.append("  ");
            tvMeg.append(getString(R.string.Live_Score) + livenessScore);
            tvMeg.append("\n");
            scrollView.fullScroll(ScrollView.FOCUS_DOWN);
        });
    }

    /**
     * 底库操作
     */
    private void showGroupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final GroupNameAdapter groupNameAdapter = new GroupNameAdapter();

        View view = LayoutInflater.from(this).inflate(R.layout.layout_dialog_group, null);
        final EditText groupNameEt = view.findViewById(R.id.et_group_name);
        Button obtainGroupsBtn = view.findViewById(R.id.btn_obtain_groups);
        Button createGroupBtn = view.findViewById(R.id.btn_submit);
        ImageView closeWindowIv = view.findViewById(R.id.iv_close);
        final ListView groupNameLv = view.findViewById(R.id.lv_group_name);
        builder.setView(view);

        /*获取底库*/
        obtainGroupsBtn.setOnClickListener(v -> {
            closeKeybord(groupNameEt, this);
            if (mFacePassHandler == null) {
                toast(getString(R.string.FacePassHandlerIsNull));
                return;
            }
            try {
                String[] groups = mFacePassHandler.getLocalGroups();
                if (groups != null && groups.length > 0) {
                    List<String> data = Arrays.asList(groups);
                    groupNameAdapter.setData(data);
                    groupNameLv.setAdapter(groupNameAdapter);
                } else {
                    toast(getString(R.string.GroupIsNull));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        /*创建底库*/
        createGroupBtn.setOnClickListener(v -> {
            closeKeybord(groupNameEt, this);
            if (mFacePassHandler == null) {
                toast(getString(R.string.FacePassHandlerIsNull));
                return;
            }

            if (TextUtils.isEmpty(groupNameEt.getText().toString())) {
                toast(getString(R.string.InputGroupName));
                return;
            }
            boolean isSuccess = false;
            try {
                group_name = groupNameEt.getText().toString();
                isSuccess = mFacePassHandler.createLocalGroup(group_name);
            } catch (FacePassException e) {
                e.printStackTrace();
            }
            toast(getString(R.string.Create_Group) + (isSuccess ? getString(R.string.Success) : getString(R.string.Fail)));
        });

        /*删除底库*/
        groupNameAdapter.setOnItemDeleteButtonClickListener(position -> {
            closeKeybord(groupNameEt, this);
            List<String> groupNames = groupNameAdapter.getData();
            if (groupNames == null) {
                return;
            }
            if (mFacePassHandler == null) {
                toast(getString(R.string.FacePassHandlerIsNull));
                return;
            }
            group_name = groupNames.get(position);
            boolean isSuccess;
            try {
                isSuccess = mFacePassHandler.deleteLocalGroup(group_name);
                if (isSuccess) {
                    String[] groups = mFacePassHandler.getLocalGroups();
                    if (groups != null) {
                        groupNameAdapter.setData(Arrays.asList(groups));
                        groupNameAdapter.notifyDataSetChanged();
                    }
                    toast(getString(R.string.Delete_Success));
                } else {
                    toast(getString(R.string.Delete_Fail));
                }
            } catch (FacePassException e) {
                e.printStackTrace();
            }
        });

        closeWindowIv.setOnClickListener(v -> mGroupOperationDialog.dismiss());

        mGroupOperationDialog = builder.create();
        WindowManager.LayoutParams attributes = mGroupOperationDialog.getWindow().getAttributes();
        attributes.height = SettingVar.mHeight;
        attributes.width = SettingVar.mWidth;
        mGroupOperationDialog.getWindow().setAttributes(attributes);
        mGroupOperationDialog.show();
    }

    /**
     * 人脸操作
     */
    private void showFaceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final FaceTokenAdapter faceTokenAdapter = new FaceTokenAdapter();

        View view = LayoutInflater.from(this).inflate(R.layout.layout_dialog_face, null);
        builder.setView(view);
        final EditText faceImagePathEt = view.findViewById(R.id.et_face_image_path);
        final EditText faceTokenEt = view.findViewById(R.id.et_face_token);
        final EditText groupNameEt = view.findViewById(R.id.et_group_name);
        Button choosePictureBtn = view.findViewById(R.id.btn_choose_picture);
        Button addFaceBtn = view.findViewById(R.id.btn_add_face);
        Button deleteFaceBtn = view.findViewById(R.id.btn_delete_face);
        Button showFaceImageBtn = view.findViewById(R.id.btn_show_face_image);
        Button bindGroupFaceTokenBtn = view.findViewById(R.id.btn_bind_group);
        Button getGroupInfoBtn = view.findViewById(R.id.btn_get_group_info);
        ImageView closeIv = view.findViewById(R.id.iv_close);
        final ListView groupInfoLv = view.findViewById(R.id.lv_group_info);

        groupNameEt.setText(group_name);
        closeIv.setOnClickListener(v -> {
            listview = false;
            mFaceOperationDialog.dismiss();
        });

        /*选择图片*/
        choosePictureBtn.setOnClickListener(v -> EasyPhotos.createAlbum(this, true, false, GlideEngine.getInstance())//参数说明：上下文，是否显示相机按钮，是否使用宽高数据（false时宽高数据为0，扫描速度更快），[配置Glide为图片加载引擎]
                .setFileProviderAuthority(getString(R.string.FileProvider))//参数说明：见下方`FileProvider的配置`
                .setPuzzleMenu(false)//不显示拼图
                .setCleanMenu(false)//不显示清空
                .setCount(1)//参数说明：最大可选数，默认1
                .setSelectedPhotos(selectedPhotoList)
                .start(new SelectCallback() {
                    @Override
                    public void onResult(ArrayList<Photo> photos, boolean isOriginal) {
                        selectedPhotoList.clear();
                        selectedPhotoList.addAll(photos);
                        for (Photo photo : selectedPhotoList) {
                            faceImagePathEt.setText(photo.path);
                        }
                    }

                    @Override
                    public void onCancel() {
                    }
                }));

        /*添加人脸*/
        addFaceBtn.setOnClickListener(v -> {
            if (mFacePassHandler == null) {
                toast(getString(R.string.FacePassHandlerIsNull));
                return;
            }
            String imagePath = faceImagePathEt.getText().toString();
            if (TextUtils.isEmpty(imagePath)) {
                toast(getString(R.string.No_Image_Path));
                return;
            }
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                toast(getString(R.string.No_Picture));
                return;
            }
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            try {
                FacePassAddFaceResult result = mFacePassHandler.addFace(bitmap);
                if (result != null) {
                    switch (result.result) {
                        case 0:
                            toast(getString(R.string.Add_Face_Success));
                            faceTokenEt.setText(new String(result.faceToken));
                            break;
                        case 1:
                            toast(getString(R.string.No_Face));
                            break;
                        case 2:
                            toast(getString(R.string.Quality_Inspection_Fail));
                            break;
                        default:
                            toast(getString(R.string.UnknownMistake));
                            break;
                    }
                }
            } catch (FacePassException e) {
                e.printStackTrace();
                toast(getString(R.string.No_Picture));
            }
        });

        /*显示人脸*/
        showFaceImageBtn.setOnClickListener(v -> {
            if (mFacePassHandler == null) {
                toast(getString(R.string.FacePassHandlerIsNull));
                return;
            }
            if (TextUtils.isEmpty(faceTokenEt.getText().toString())) {
                toast(getString(R.string.Add_Face_First));
                return;
            }
            try {
                faceToken = faceTokenEt.getText().toString().getBytes();
                Bitmap bmp = mFacePassHandler.getFaceImage(faceToken);
                showToast(null, Toast.LENGTH_SHORT, true, bmp, false);
            } catch (Exception e) {
                e.printStackTrace();
                toast(getString(R.string.No_Picture));
            }
        });

        /*删除人脸*/
        deleteFaceBtn.setOnClickListener(v -> {
            if (mFacePassHandler == null) {
                toast(getString(R.string.FacePassHandlerIsNull));
                return;
            }
            if (TextUtils.isEmpty(faceTokenEt.getText().toString())) {
                toast(getString(R.string.Add_Face_First));
                return;
            }
            try {
                faceToken = faceTokenEt.getText().toString().getBytes();
                boolean b = mFacePassHandler.deleteFace(faceToken);
                if (b) {
                    toast(getString(R.string.Delete_Success));
                    faceTokenEt.setText(null);
                    refeshGroup(faceTokenAdapter, groupInfoLv, group_name);
                } else {
                    toast(getString(R.string.Delete_Fail));
                }
            } catch (FacePassException e) {
                e.printStackTrace();
                toast(e.getLocalizedMessage());
            }
        });

        /*绑定底库*/
        bindGroupFaceTokenBtn.setOnClickListener(v -> {
            if (mFacePassHandler == null) {
                toast(getString(R.string.FacePassHandlerIsNull));
                return;
            }
            if (TextUtils.isEmpty(faceTokenEt.getText().toString())) {
                toast(getString(R.string.Add_Face_First));
                return;
            }
            if (TextUtils.isEmpty(groupNameEt.getText().toString())) {
                toast(getString(R.string.InputGroupName));
                return;
            }
            faceToken = faceTokenEt.getText().toString().getBytes();
            group_name = groupNameEt.getText().toString();
            try {
                boolean b = mFacePassHandler.bindGroup(group_name, faceToken);
                String result = b ? getString(R.string.Bind_Success) : getString(R.string.Bind_Fail);
                toast(result);
                if (b) {
                    refeshGroup(faceTokenAdapter, groupInfoLv, group_name);
                }
            } catch (Exception e) {
                e.printStackTrace();
                toast(e.getLocalizedMessage());
            }
        });

        /*获取人脸*/
        getGroupInfoBtn.setOnClickListener(v -> {
            if (mFacePassHandler == null) {
                toast(getString(R.string.FacePassHandlerIsNull));
                return;
            }
            group_name = groupNameEt.getText().toString();
            if (TextUtils.isEmpty(group_name)) {
                toast(getString(R.string.InputGroupName));
                return;
            }
            //点击按钮显示或隐藏
            listview = !listview;
            refeshGroup(faceTokenAdapter, groupInfoLv, group_name);
        });

        /*解绑人脸*/
        faceTokenAdapter.setOnItemButtonClickListener(position -> {
            if (mFacePassHandler == null) {
                toast(getString(R.string.FacePassHandlerIsNull));
                return;
            }
            group_name = groupNameEt.getText().toString();
            if (TextUtils.isEmpty(group_name)) {
                toast(getString(R.string.InputGroupName));
                return;
            }
            try {
                faceToken = faceTokenAdapter.getData().get(position).getBytes();
                boolean b = mFacePassHandler.unBindGroup(group_name, faceToken);
                String result = b ? getString(R.string.Success) : getString(R.string.Fail);
                toast(getString(R.string.UnBind) + result);
                if (b) {
                    refeshGroup(faceTokenAdapter, groupInfoLv, group_name);
                }
            } catch (Exception e) {
                e.printStackTrace();
                toast(getString(R.string.UnBind) + getString(R.string.Error));
            }
        });

        mFaceOperationDialog = builder.create();
        WindowManager.LayoutParams attributes = mFaceOperationDialog.getWindow().getAttributes();
        attributes.height = SettingVar.mHeight;
        attributes.width = SettingVar.mWidth;
        mFaceOperationDialog.getWindow().setAttributes(attributes);
        mFaceOperationDialog.setOnCancelListener(v -> listview = false);
        mFaceOperationDialog.show();
    }

    /**
     * 刷新底库详细信息
     *
     * @param faceTokenAdapter 要更新的适配器
     * @param groupInfoLv      填充位置
     * @param groupName        底库名
     */
    private void refeshGroup(FaceTokenAdapter faceTokenAdapter, ListView groupInfoLv, String groupName) {
        //listview判断是否显示详情，true显示详情并读取列表设置适配器，false则隐藏详情列表不执行操作
        if (!listview) {
            groupInfoLv.setVisibility(View.INVISIBLE);
            return;
        } else {
            groupInfoLv.setVisibility(View.VISIBLE);
        }
        try {
            byte[][] faceTokens = mFacePassHandler.getLocalGroupInfo(groupName);
            List<String> faceTokenList = new ArrayList<>();
            if (faceTokens != null && faceTokens.length > 0) {
                for (byte[] faceToken : faceTokens) {
                    if (faceToken.length > 0) {
                        faceTokenList.add(new String(faceToken));
                    }
                }
            }
            faceTokenAdapter.setData(faceTokenList);
            groupInfoLv.setAdapter(faceTokenAdapter);
        } catch (Exception e) {
            e.printStackTrace();
            toast(e.getLocalizedMessage());
        }
    }

    /**
     * 人脸识别日志显示隐藏
     */
    private void ShowRecognitionLog() {
        if (buttonFlag) {
            scrollView.setVisibility(View.VISIBLE);
        } else {
            scrollView.setVisibility(View.INVISIBLE);
            tvMeg.setText("");
        }
        buttonFlag = !buttonFlag;
    }

    /**
     * 获取当前设备的参数
     */
    protected void getDeviceDensity() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        SettingVar.mHeight = metrics.heightPixels;
        SettingVar.mWidth = metrics.widthPixels;
    }

    /**
     * 双击退出程序
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            if ((System.currentTimeMillis() - exitTime) > 2000) {
                toast(getString(R.string.Exit_APP));
                exitTime = System.currentTimeMillis();
            } else {
                finish();
                System.exit(0);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 检测线程
     */
    private class FeedFrameThread extends Thread {
        boolean isInterrupt;

        @Override
        public void run() {
            while (!isInterrupt) {
                CameraPreviewData cameraPreviewData_rgb;
                CameraPreviewData cameraPreviewData_ir;
                try {
                    cameraPreviewData_rgb = mFeedFrameQueue_rgb.take();
                    cameraPreviewData_ir = mFeedFrameQueue_ir.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                if (mFacePassHandler == null) {
                    continue;
                }
                /* 将相机预览帧转成SDK算法所需帧的格式 FacePassImage */
                FacePassImage image_rgb;
                FacePassImage image_ir;
                try {
                    image_rgb = new FacePassImage(cameraPreviewData_rgb.nv21Data, cameraPreviewData_rgb.width, cameraPreviewData_rgb.height, SettingVar.faceRotation, FacePassImageType.NV21);
                    image_ir = new FacePassImage(cameraPreviewData_ir.nv21Data, cameraPreviewData_ir.width, cameraPreviewData_ir.height, SettingVar.faceRotation, FacePassImageType.NV21);
                } catch (FacePassException e) {
                    e.printStackTrace();
                    continue;
                }
                /* 将每一帧FacePassImage 送入SDK算法， 并得到返回结果 */
                FacePassDetectionResult detectionResult = null;
                try {
                    detectionResult = mFacePassHandler.feedFrameRGBIR(image_rgb, image_ir);
                } catch (FacePassException e) {
                    e.printStackTrace();
                }
                if (detectionResult == null || detectionResult.faceList.length == 0) {
                    /* 当前帧没有检出人脸 */
                    runOnUiThread(() -> {
                        faceView.clear();
                        faceView.invalidate();
                    });
                } else {
                    /* 将识别到的人脸在预览界面中圈出，并在上方显示人脸位置及角度信息 */
                    final FacePassFace[] bufferFaceList = detectionResult.faceList;
                    runOnUiThread(() -> showFacePassFace(bufferFaceList));
                }
                /*离线模式，将识别到人脸的，message不为空的result添加到处理队列中*/
                if (detectionResult != null && detectionResult.message.length != 0) {
                    mDetectResultQueue.offer(detectionResult.message);
                }
            }
        }

        @Override
        public void interrupt() {
            isInterrupt = true;
            super.interrupt();
        }
    }

    /**
     * 识别线程
     */
    private class RecognizeThread extends Thread {

        boolean isInterrupt;

        @Override
        public void run() {
            while (!isInterrupt) {
                try {
                    byte[] detectionResult = mDetectResultQueue.take();
                    FacePassRecognitionResult[] recognizeResult = mFacePassHandler.recognize(group_name, detectionResult);
                    if (recognizeResult != null && recognizeResult.length > 0) {
                        for (FacePassRecognitionResult result : recognizeResult) {
                            String faceToken = new String(result.faceToken);
                            if (FacePassRecognitionState.RECOGNITION_PASS == result.recognitionState) {
                                getFaceImageByFaceToken(result.trackId, faceToken);
                            }
                            showRecognizeResult(result.trackId, result.detail.searchScore, result.detail.livenessScore, !TextUtils.isEmpty(faceToken));
                        }
                    }
                } catch (InterruptedException | FacePassException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage());
                }
            }
        }

        @Override
        public void interrupt() {
            isInterrupt = true;
            super.interrupt();
        }
    }
}