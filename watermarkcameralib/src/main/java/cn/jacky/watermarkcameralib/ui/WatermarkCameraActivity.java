package cn.jacky.watermarkcameralib.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.ScaleAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import cn.jacky.watermarkcameralib.R;
import cn.jacky.watermarkcameralib.util.AppUtils;
import cn.jacky.watermarkcameralib.util.DateUtils;
import cn.jacky.watermarkcameralib.util.DensityUtils;
import cn.jacky.watermarkcameralib.util.ImageUtil;

/**
 * 水印相机,兼容6.0+
 *
 * @author hzj
 */
public class WatermarkCameraActivity extends AppCompatActivity {
    private SurfaceView surfaceView;
    private TextView tvText;
    private TextView tvDate;
    private ImageButton btnTakePic;
    private LinearLayout llSetting;
    private ImageView ivPhoto;
    private RelativeLayout rlPhoto;
    private ImageButton btnSavePic;
    private FrameLayout flWatermarkParent;
    private ImageButton btnClose;
    private RelativeLayout rlTopBar;
    private ImageButton btnLamp;
    private TextView tvWatermark;
    private View focusIndex;
    private Camera.Parameters parameters = null;
    private Camera cameraInst = null;
    private float pointX, pointY;
    static final int FOCUS = 1;            // 聚焦
    static final int ZOOM = 2;            // 缩放
    private int mode;                      //0是聚焦 1是放大
    private float dist;
    private boolean mark_on = true;//水印显示隐藏切换
    private int topBarHeight;// 顶部工具栏高度
    private int statusBarHeight;// 顶部导航栏高度
    private int settingBarHeight;// 按钮栏高度
    private int navigationBarHeight;// 底部导航栏高度
    private float surfaceHeightScale;// 预览比例
    private float topBarHeightScale;// 顶部工具栏加顶部导航栏比例
    private String photoPath;
    public static final int REQUEST_WATERMARK_CAMERA = 115;
    public static final String WATERMARK_CAMERA_EXTRA_RESULT = "water_mark_camera_result";
    private Handler handler = new Handler();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watermark_camera);
        initViewsAndEvents();
    }

    // 定点对焦
    public void onPointFocus(View view) {
        try {
            pointFocus((int) pointX, (int) pointY);
        } catch (Exception e) {
            e.printStackTrace();
        }
        RelativeLayout.LayoutParams layout = new RelativeLayout.LayoutParams(focusIndex.getLayoutParams());
        layout.setMargins((int) pointX - 60, (int) pointY - 60, 0, 0);
        focusIndex.setLayoutParams(layout);
        focusIndex.setVisibility(View.VISIBLE);
        ScaleAnimation sa = new ScaleAnimation(3f, 1f, 3f, 1f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
        sa.setDuration(800);
        focusIndex.startAnimation(sa);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                focusIndex.setVisibility(View.INVISIBLE);
            }
        }, 800);
    }

    // 水印显示状态切换
    public void showWatermark(View view) {
        flWatermarkParent.setVisibility(mark_on ? View.GONE : View.VISIBLE);
        mark_on = !mark_on;
        tvWatermark.setText(mark_on ? "关闭水印" : "显示水印");
    }

    // 闪光灯状态切换
    public void onSwitch(View view) {
        turnLight(cameraInst);
    }

    // 取消保存
    public void onClose(View view) {
        ivPhoto.setVisibility(View.GONE);
        surfaceView.setVisibility(View.VISIBLE);
        btnSavePic.setVisibility(View.GONE);
        btnTakePic.setVisibility(View.VISIBLE);
        btnClose.setVisibility(View.GONE);
        tvWatermark.setVisibility(View.VISIBLE);
    }

    // 拍照
    public void onTakePic(View view) {
        cameraInst.takePicture(null, null, new MyPictureCallback());
    }

    /**
     * ========================================输出拍照结果======================================
     *
     * @param view
     */
    public void onSavePic(View view) {
        Bitmap saveBitmap = getScreenPhoto(rlPhoto);
        try {
            String photoPath = ImageUtil.saveToFile(ImageUtil.getSystemPhotoPath(), true, saveBitmap);
            Intent dataBack = new Intent();
            dataBack.putExtra(WATERMARK_CAMERA_EXTRA_RESULT, photoPath);
            setResult(RESULT_OK, dataBack);
            finish();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    ======================================================================================

    // 修改文字
    public void onTextEdit(View view) {
        final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        final Dialog dialog = new Dialog(this, R.style.WatermarkCamera_Dialog);
        //填充对话框的布局
        View inflate = LayoutInflater.from(this).inflate(R.layout.water_mark_dialog_edit, null);
        final EditText etText = (EditText) inflate.findViewById(R.id.et_text);
        final ImageButton btnClear = (ImageButton) inflate.findViewById(R.id.btn_clear);
        final RelativeLayout rlCancel = (RelativeLayout) inflate.findViewById(R.id.rl_cancel);
        final RelativeLayout rlConfirm = (RelativeLayout) inflate.findViewById(R.id.rl_confirm);
        etText.setText(tvText.getText().toString());
        etText.setSelection(etText.getText().length());
        // 清空
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                etText.setText("");
            }
        });
        // 取消
        rlCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                imm.showSoftInput(etText, InputMethodManager.HIDE_IMPLICIT_ONLY);
            }
        });
        // 确认
        rlConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!TextUtils.isEmpty(etText.getText()))
                    tvText.setText(etText.getText());
                dialog.dismiss();
                imm.showSoftInput(etText, InputMethodManager.HIDE_IMPLICIT_ONLY);
            }
        });
        //将布局设置给Dialog
        dialog.setContentView(inflate);
        //获取当前Activity所在的窗体
        Window dialogWindow = dialog.getWindow();
        //设置Dialog从窗体底部弹出
        dialogWindow.setGravity(Gravity.BOTTOM);
        dialogWindow.getDecorView().setPadding(0, 0, 0, 0);
        dialogWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                imm.showSoftInput(etText, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        dialog.show();//显示对话框
    }

    /**
     * 截屏
     *
     * @param waterPhoto waterPhoto
     * @return Bitmap
     */
    public Bitmap getScreenPhoto(RelativeLayout waterPhoto) {
        View view = waterPhoto;
        view.setDrawingCacheEnabled(true);
        view.buildDrawingCache();
        Bitmap bitmap = view.getDrawingCache();
        int width = view.getWidth();
        int height = view.getHeight();
        Bitmap saveBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
        view.destroyDrawingCache();
        bitmap = null;
        return saveBitmap;
    }

    /**
     * 两点的距离
     */
    private float spacing(MotionEvent event) {
        if (event == null) {
            return 0;
        }
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    protected void initViewsAndEvents() {
        initView();
        tvDate.setText(DateUtils.getCurDateStr("yyyy-MM-dd"));
        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    // 主点按下
                    case MotionEvent.ACTION_DOWN:
                        pointX = event.getX();
                        pointY = event.getY();
                        mode = FOCUS;
                        break;
                    // 副点按下
                    case MotionEvent.ACTION_POINTER_DOWN:
                        dist = spacing(event);
                        // 如果连续两点距离大于10，则判定为多点模式
                        if (spacing(event) > 10f) {
                            mode = ZOOM;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        mode = FOCUS;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (mode == FOCUS) {
                            //pointFocus((int) event.getRawX(), (int) event.getRawY());
                        } else if (mode == ZOOM) {
                            float newDist = spacing(event);
                            if (newDist > 10f) {
                                float tScale = (newDist - dist) / dist;
                                if (tScale < 0) {
                                    tScale = tScale * 10;
                                }
                                addZoomIn((int) tScale);
                            }
                        }
                        break;
                }
                return false;
            }
        });
    }

    private void initView() {
        surfaceView = (SurfaceView) findViewById(R.id.sfv_camera);
        tvText = (TextView) findViewById(R.id.tv_text);
        tvDate = (TextView) findViewById(R.id.tv_date);
        btnTakePic = (ImageButton) findViewById(R.id.btn_take_pic);
        llSetting = (LinearLayout) findViewById(R.id.ll_setting);
        ivPhoto = (ImageView) findViewById(R.id.iv_photo);
        rlPhoto = (RelativeLayout) findViewById(R.id.rl_photo);
        btnSavePic = (ImageButton) findViewById(R.id.btn_save_pic);
        flWatermarkParent = (FrameLayout) findViewById(R.id.fl_watermark_parent);
        btnClose = (ImageButton) findViewById(R.id.btn_close);
        rlTopBar = (RelativeLayout) findViewById(R.id.rl_top_bar);
        btnLamp = (ImageButton) findViewById(R.id.btn_lamp);
        tvWatermark = (TextView) findViewById(R.id.tv_watermark);
        focusIndex = (View) findViewById(R.id.focus_index);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceHolder.setKeepScreenOn(true);
        surfaceView.setFocusable(true);
        surfaceView.setBackgroundColor(TRIM_MEMORY_BACKGROUND);
        surfaceView.getHolder().addCallback(new SurfaceCallback());//为SurfaceView的句柄添加一个回调函数
    }

    //放大缩小
    int curZoomValue = 0;

    private void addZoomIn(int delta) {

        try {
            Camera.Parameters params = cameraInst.getParameters();
            Log.d("Camera", "Is support Zoom " + params.isZoomSupported());
            if (!params.isZoomSupported()) {
                return;
            }
            curZoomValue += delta;
            if (curZoomValue < 0) {
                curZoomValue = 0;
            } else if (curZoomValue > params.getMaxZoom()) {
                curZoomValue = params.getMaxZoom();
            }

            if (!params.isSmoothZoomSupported()) {
                params.setZoom(curZoomValue);
                cameraInst.setParameters(params);
                return;
            } else {
                cameraInst.startSmoothZoom(curZoomValue);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final class MyPictureCallback implements Camera.PictureCallback {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            photoPath = saveToSDCard(data);
            Glide.with(WatermarkCameraActivity.this)
                    .load(photoPath)
                    .apply(new RequestOptions().fitCenter())
                    .into(ivPhoto);
            ivPhoto.setVisibility(View.VISIBLE);
            surfaceView.setVisibility(View.GONE);
            btnSavePic.setVisibility(View.VISIBLE);
            btnTakePic.setVisibility(View.GONE);
            btnClose.setVisibility(View.VISIBLE);
            tvWatermark.setVisibility(View.GONE);
        }
    }

    private final class SurfaceCallback implements SurfaceHolder.Callback {

        public void surfaceDestroyed(SurfaceHolder holder) {
            try {
                if (cameraInst != null) {
                    cameraInst.stopPreview();
                    cameraInst.release();
                    cameraInst = null;
                }
            } catch (Exception e) {
                //相机已经关了
            }

        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            if (null == cameraInst) {
                try {
                    topBarHeight = rlTopBar.getHeight();
                    settingBarHeight = llSetting.getHeight();
                    statusBarHeight = AppUtils.getStatusBarHeight(WatermarkCameraActivity.this);
                    if (AppUtils.checkDeviceHasNavigationBar(WatermarkCameraActivity.this))
                        navigationBarHeight = AppUtils.getNavigationBarHeight(WatermarkCameraActivity.this);
                    surfaceHeightScale = (float) (DensityUtils.getDisplayHeight(WatermarkCameraActivity.this) - statusBarHeight - topBarHeight - settingBarHeight - navigationBarHeight) / (float) DensityUtils.getDisplayHeight(WatermarkCameraActivity.this);
                    topBarHeightScale = (float) (topBarHeight) / (float) DensityUtils.getDisplayHeight(WatermarkCameraActivity.this);
                    cameraInst = Camera.open();
                    cameraInst.setPreviewDisplay(holder);
                    initCamera();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            autoFocus();
        }
    }

    //实现自动对焦
    private void autoFocus() {
        new Thread() {
            @Override
            public void run() {
                try {
                    sleep(100);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (cameraInst == null) {
                    return;
                }
                cameraInst.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        if (success) {
                            initCamera();//实现相机的参数初始化
                        }
                    }
                });
            }
        };
    }

    private Camera.Size adapterSize = null;
    private Camera.Size previewSize = null;

    private void initCamera() {
        parameters = cameraInst.getParameters();
        parameters.setPictureFormat(PixelFormat.JPEG);
        if (previewSize == null && adapterSize == null) {
            setUpPicSize(parameters);
            setUpPreviewSize(parameters);
        }
        if (previewSize != null && adapterSize != null) {
            parameters.setPreviewSize(previewSize.width, previewSize.height);
            parameters.setPictureSize(adapterSize.width, adapterSize.height);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);//1连续对焦
        } else {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        setDispaly(parameters, cameraInst);
        try {
            cameraInst.setParameters(parameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
        cameraInst.startPreview();
        cameraInst.cancelAutoFocus();// 2如果要实现连续的自动对焦，这一句必须加上
    }

    private void setUpPicSize(Camera.Parameters parameters) {

        if (adapterSize != null) {
            return;
        } else {
            adapterSize = findBestPictureResolution();
            return;
        }
    }

    private void setUpPreviewSize(Camera.Parameters parameters) {

        if (previewSize != null) {
            return;
        } else {
            previewSize = findBestPreviewResolution();
        }
    }

    /**
     * 最小预览界面的分辨率
     */
    private static final int MIN_PREVIEW_PIXELS = 480 * 320;
    /**
     * 最大宽高比差
     */
    private static final double MAX_ASPECT_DISTORTION = 0.15;
    private static final String TAG = "Camera";


    //控制图像的正确显示方向
    private void setDispaly(Camera.Parameters parameters, Camera camera) {
        if (Build.VERSION.SDK_INT >= 8) {
            setDisplayOrientation(camera, 90);
        } else {
            parameters.setRotation(90);
        }
    }

    //实现的图像的正确显示
    private void setDisplayOrientation(Camera camera, int i) {
        Method downPolymorphic;
        try {
            downPolymorphic = camera.getClass().getMethod("setDisplayOrientation",
                    new Class[]{int.class});
            if (downPolymorphic != null) {
                downPolymorphic.invoke(camera, new Object[]{i});
            }
        } catch (Exception e) {
            Log.e("Came_e", "图像出错");
        }
    }

    /**
     * 将拍下来的照片存放在SD卡中
     *
     * @param data
     * @throws IOException
     */
    public String saveToSDCard(byte[] data) {
        Bitmap croppedImage = null;
        String imagePath = null;
        try {
            croppedImage = decodeRegionCrop(data);
            imagePath = ImageUtil.saveToFile(ImageUtil.getSystemPhotoPath(), true,
                    croppedImage);
        } catch (IOException e) {
            e.printStackTrace();
        }
        croppedImage.recycle();
        return imagePath;
    }

    private Bitmap decodeRegionCrop(byte[] data) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        Matrix matrix = new Matrix();
        matrix.preRotate(90);
//        if(bitmap.getWidth() == previewSize.width && bitmap.getHeight() == previewSize.height)
        bitmap = Bitmap.createBitmap(bitmap, (int) (bitmap.getWidth() * topBarHeightScale), 0, (int) (bitmap.getWidth() * surfaceHeightScale), bitmap.getHeight(), matrix, true);
//        else
//            bitmap = Bitmap.createBitmap(bitmap , 0, 0, (int) (bitmap.getWidth() * heightScale), bitmap.getHeight(), matrix, true);
        return bitmap;
    }

    /**
     * 闪光灯开关   开->关->自动
     *
     * @param mCamera
     */
    private void turnLight(Camera mCamera) {
        if (mCamera == null || mCamera.getParameters() == null
                || mCamera.getParameters().getSupportedFlashModes() == null) {
            return;
        }
        Camera.Parameters parameters = mCamera.getParameters();
        String flashMode = mCamera.getParameters().getFlashMode();
        List<String> supportedModes = mCamera.getParameters().getSupportedFlashModes();
        if (Camera.Parameters.FLASH_MODE_OFF.equals(flashMode)
                && supportedModes.contains(Camera.Parameters.FLASH_MODE_ON)) {//关闭状态
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
            mCamera.setParameters(parameters);
            btnLamp.setBackgroundResource(R.drawable.fresh_open);
        } else if (Camera.Parameters.FLASH_MODE_ON.equals(flashMode)) {//开启状态
            if (supportedModes.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                btnLamp.setBackgroundResource(R.drawable.fresh_auto);
                mCamera.setParameters(parameters);
            } else if (supportedModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                btnLamp.setBackgroundResource(R.drawable.fresh_close);
                mCamera.setParameters(parameters);
            }
        } else if (Camera.Parameters.FLASH_MODE_AUTO.equals(flashMode)
                && supportedModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            mCamera.setParameters(parameters);
            btnLamp.setBackgroundResource(R.drawable.fresh_close);
        }
    }

    /**
     * 找出最适合的预览界面分辨率
     *
     * @return
     */
    private Camera.Size findBestPreviewResolution() {
        Camera.Parameters cameraParameters = cameraInst.getParameters();
        Camera.Size defaultPreviewResolution = cameraParameters.getPreviewSize();

        List<Camera.Size> rawSupportedSizes = cameraParameters.getSupportedPreviewSizes();
        if (rawSupportedSizes == null) {
            return defaultPreviewResolution;
        }

        // 按照分辨率从大到小排序
        List<Camera.Size> supportedPreviewResolutions = new ArrayList<Camera.Size>(rawSupportedSizes);
        Collections.sort(supportedPreviewResolutions, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });

        StringBuilder previewResolutionSb = new StringBuilder();
        for (Camera.Size supportedPreviewResolution : supportedPreviewResolutions) {
            previewResolutionSb.append(supportedPreviewResolution.width).append('x').append(supportedPreviewResolution.height)
                    .append(' ');
        }
        Log.v(TAG, "Supported preview resolutions: " + previewResolutionSb);


        // 移除不符合条件的分辨率
        double screenAspectRatio = (double) getResources().getDisplayMetrics().widthPixels
                / (double) getResources().getDisplayMetrics().heightPixels;
        Iterator<Camera.Size> it = supportedPreviewResolutions.iterator();
        while (it.hasNext()) {
            Camera.Size supportedPreviewResolution = it.next();
            int width = supportedPreviewResolution.width;
            int height = supportedPreviewResolution.height;

            // 移除低于下限的分辨率，尽可能取高分辨率
            if (width * height < MIN_PREVIEW_PIXELS) {
                it.remove();
                continue;
            }

            // 在camera分辨率与屏幕分辨率宽高比不相等的情况下，找出差距最小的一组分辨率
            // 由于camera的分辨率是width>height，我们设置的portrait模式中，width<height
            // 因此这里要先交换然preview宽高比后在比较
            boolean isCandidatePortrait = width > height;
            int maybeFlippedWidth = isCandidatePortrait ? height : width;
            int maybeFlippedHeight = isCandidatePortrait ? width : height;
            double aspectRatio = (double) maybeFlippedWidth / (double) maybeFlippedHeight;
            double distortion = Math.abs(aspectRatio - screenAspectRatio);
            if (distortion > MAX_ASPECT_DISTORTION) {
                it.remove();
                continue;
            }

            // 找到与屏幕分辨率完全匹配的预览界面分辨率直接返回
            if (maybeFlippedWidth == getResources().getDisplayMetrics().widthPixels
                    && maybeFlippedHeight == getResources().getDisplayMetrics().heightPixels) {
                return supportedPreviewResolution;
            }
        }

        // 如果没有找到合适的，并且还有候选的像素，则设置其中最大比例的，对于配置比较低的机器不太合适
        if (!supportedPreviewResolutions.isEmpty()) {
            Camera.Size largestPreview = supportedPreviewResolutions.get(0);
            return largestPreview;
        }

        // 没有找到合适的，就返回默认的

        return defaultPreviewResolution;
    }

    private Camera.Size findBestPictureResolution() {
        Camera.Parameters cameraParameters = cameraInst.getParameters();
        List<Camera.Size> supportedPicResolutions = cameraParameters.getSupportedPictureSizes(); // 至少会返回一个值

        StringBuilder picResolutionSb = new StringBuilder();
        for (Camera.Size supportedPicResolution : supportedPicResolutions) {
            picResolutionSb.append(supportedPicResolution.width).append('x')
                    .append(supportedPicResolution.height).append(" ");
        }
        Log.d(TAG, "Supported picture resolutions: " + picResolutionSb);

        Camera.Size defaultPictureResolution = cameraParameters.getPictureSize();
        Log.d(TAG, "default picture resolution " + defaultPictureResolution.width + "x"
                + defaultPictureResolution.height);

        // 排序
        List<Camera.Size> sortedSupportedPicResolutions = new ArrayList<Camera.Size>(
                supportedPicResolutions);
        Collections.sort(sortedSupportedPicResolutions, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });

        // 移除不符合条件的分辨率
        double screenAspectRatio = (double) getResources().getDisplayMetrics().widthPixels
                / (double) getResources().getDisplayMetrics().heightPixels;
        Iterator<Camera.Size> it = sortedSupportedPicResolutions.iterator();
        while (it.hasNext()) {
            Camera.Size supportedPreviewResolution = it.next();
            int width = supportedPreviewResolution.width;
            int height = supportedPreviewResolution.height;

            // 在camera分辨率与屏幕分辨率宽高比不相等的情况下，找出差距最小的一组分辨率
            // 由于camera的分辨率是width>height，我们设置的portrait模式中，width<height
            // 因此这里要先交换然后在比较宽高比
            boolean isCandidatePortrait = width > height;
            int maybeFlippedWidth = isCandidatePortrait ? height : width;
            int maybeFlippedHeight = isCandidatePortrait ? width : height;
            double aspectRatio = (double) maybeFlippedWidth / (double) maybeFlippedHeight;
            double distortion = Math.abs(aspectRatio - screenAspectRatio);
            if (distortion > MAX_ASPECT_DISTORTION) {
                it.remove();
                continue;
            }
        }

        // 如果没有找到合适的，并且还有候选的像素，对于照片，则取其中最大比例的，而不是选择与屏幕分辨率相同的
        if (!sortedSupportedPicResolutions.isEmpty()) {
            return sortedSupportedPicResolutions.get(0);
        }

        // 没有找到合适的，就返回默认的
        return defaultPictureResolution;
    }

    //定点对焦
    private void pointFocus(int x, int y) {
        cameraInst.cancelAutoFocus();
        parameters = cameraInst.getParameters();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            showPoint(x, y);
        }
        cameraInst.setParameters(parameters);
        autoFocus();
    }

    private void showPoint(int x, int y) {
        if (parameters.getMaxNumMeteringAreas() > 0) {
            List<Camera.Area> areas = new ArrayList<Camera.Area>();
            //xy变换了
            int rectY = -x * 2000 / getResources().getDisplayMetrics().widthPixels + 1000;
            int rectX = y * 2000 / getResources().getDisplayMetrics().heightPixels - 1000;

            int left = rectX < -900 ? -1000 : rectX - 100;
            int top = rectY < -900 ? -1000 : rectY - 100;
            int right = rectX > 900 ? 1000 : rectX + 100;
            int bottom = rectY > 900 ? 1000 : rectY + 100;
            Rect area1 = new Rect(left, top, right, bottom);
            areas.add(new Camera.Area(area1, 800));
            parameters.setMeteringAreas(areas);
        }

        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
    }

}
