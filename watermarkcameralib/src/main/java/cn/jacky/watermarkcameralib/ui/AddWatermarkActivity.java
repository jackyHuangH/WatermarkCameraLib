package cn.jacky.watermarkcameralib.ui;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.request.RequestOptions;

import java.io.IOException;

import cn.jacky.watermarkcameralib.R;
import cn.jacky.watermarkcameralib.util.DateUtils;
import cn.jacky.watermarkcameralib.util.ImageUtil;
import cn.jacky.watermarkcameralib.util.KeyboardUtils;


/**
 * 为图片添加水印
 *
 * @author hzj
 */
public class AddWatermarkActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "AddWatermarkActivity";

    public static final String IMG_PATH_EXTRA = "image_path_extra";
    public static final String WATERMARK_CAMERA_RESULT = "water_mark_camera_result";

    public static final int REQUEST_CODE_ADD_WATER_MARK = 1024;

    private Button mBtBack;
    private ImageView mIvPhoto;
    private TextView mTvText;
    private TextView mTvDate;
    private LinearLayout mlWatermark;

    private Button mBtDone;
    private ViewGroup mRlMarkPicture;
    private Button mBtShowOffWater;

    private boolean mark_on = true;
    private String mImagePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_water_mark);

        initUi();
        initData();
    }

    private void initUi() {
        mBtBack = (Button) findViewById(R.id.bt_back);
        mIvPhoto = (ImageView) findViewById(R.id.iv_photo);
        mTvText = (TextView) findViewById(R.id.tv_text);
        mTvDate = (TextView) findViewById(R.id.tv_date);
        mlWatermark = (LinearLayout) findViewById(R.id.ll_watermark);
        mBtDone = (Button) findViewById(R.id.bt_done);
        mRlMarkPicture = (ViewGroup) findViewById(R.id.rl_mark_picture);
        mBtShowOffWater = (Button) findViewById(R.id.bt_show_off_water);

        mBtBack.setOnClickListener(this);
        mBtDone.setOnClickListener(this);
        mlWatermark.setOnClickListener(this);
        mBtShowOffWater.setOnClickListener(this);
    }

    private void initData() {
        mTvDate.setText(DateUtils.getCurDateStr("yyyy-MM-dd"));
        mImagePath = getIntent().getStringExtra(IMG_PATH_EXTRA);
        if (!TextUtils.isEmpty(mImagePath)) {
            RequestOptions requestOptions = new RequestOptions();
			//glide4.5版本 8.0 上View.getDrawingCache()方法会报错，此为解决方案
			//后续版本修复了再删除即可
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requestOptions = requestOptions.disallowHardwareConfig()
                        .format(DecodeFormat.PREFER_ARGB_8888);
            }

            Glide.with(AddWatermarkActivity.this)
                    .load(mImagePath)
                    .apply(requestOptions
                            .fitCenter())
                    .into(mIvPhoto);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.bt_back) {
            onBackPressed();
        } else if (v.getId() == R.id.bt_done) {
            onDoneClicked();
        } else if (v.getId() == R.id.ll_watermark) {
            showEditDialog();
        } else if (v.getId() == R.id.bt_show_off_water) {
            mlWatermark.setVisibility(mark_on ? View.GONE : View.VISIBLE);
            mark_on = !mark_on;
            mBtShowOffWater.setText(mark_on ? "关闭水印" : "显示水印");
        }

    }

    /**
     * 完成，截图，并把截图的地址回传
     */
    private void onDoneClicked() {
        String photoPath = mImagePath;
        if (mark_on) {
            Bitmap saveBitmap = screenShotPhoto(mRlMarkPicture);
            try {
                photoPath = ImageUtil.saveToFile(ImageUtil.getSystemPhotoPath(), true, saveBitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Intent dataBack = new Intent();
        dataBack.putExtra(WATERMARK_CAMERA_RESULT, photoPath);
        setResult(RESULT_OK, dataBack);
        finish();
    }

    /**
     * 截屏
     *
     * @param waterPhoto waterPhoto
     * @return Bitmap
     */
    public Bitmap screenShotPhoto(View waterPhoto) {
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
     * 修改文字
     */
    public void showEditDialog() {
        final Dialog dialog = new Dialog(this, R.style.WatermarkCamera_Dialog);
        //填充对话框的布局
        View inflate = LayoutInflater.from(this).inflate(R.layout.water_mark_dialog_edit, null);
        final EditText etText = (EditText) inflate.findViewById(R.id.et_text);
        final ImageButton btnClear = (ImageButton) inflate.findViewById(R.id.btn_clear);
        final RelativeLayout rlCancel = (RelativeLayout) inflate.findViewById(R.id.rl_cancel);
        final RelativeLayout rlConfirm = (RelativeLayout) inflate.findViewById(R.id.rl_confirm);
        etText.setText(mTvText.getText().toString());
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
                KeyboardUtils.showSoftInput(AddWatermarkActivity.this, etText);
            }
        });
        // 确认
        rlConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!TextUtils.isEmpty(etText.getText())) {
                    mTvText.setText(etText.getText());
                }
                dialog.dismiss();
                KeyboardUtils.hideSoftInput(AddWatermarkActivity.this);
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
                KeyboardUtils.showSoftInput(AddWatermarkActivity.this, etText);
            }
        });
        dialog.show();//显示对话框
    }
}
