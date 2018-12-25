# WatermarkCamera
watermarkCamera for android which compat 6.0+
# 安卓简单水印相机,可以添加简单水印的相机
![效果图](https://github.com/huangzijie0303/WatermarkCamera/blob/master/art/TIM%E5%9B%BE%E7%89%8720171108174700.png)
![添加水印](https://github.com/huangzijie0303/WatermarkCamera/blob/master/art/TIM%E5%9B%BE%E7%89%8720171108175035.png)


## 使用方式：
### 第一步：下载项目库添加到你的项目中依赖
     暂时未上传至jcenter中，待续....
### 第二步：调用如下方式跳转到拍照界面：

                Intent intent = new Intent(Activity from, WatermarkCameraActivity.class);
                startActivityForResult(intent, WatermarkCameraActivity.REQUEST_WATERMARK_CAMERA);


### 第三步：拍照结果返回:

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case WatermarkCameraActivity.REQUEST_WATERMARK_CAMERA:
                    if (null!=data) {
                        String data1 = data.getStringExtra(WatermarkCameraActivity.WATERMARK_CAMERA_EXTRA_RESULT);
                       
                        //do something...
                    }
                    break;
            }
        }
    }

兼容6.0+机型，欢迎star，欢迎提issue
