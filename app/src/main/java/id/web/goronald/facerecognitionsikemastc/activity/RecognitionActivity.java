package id.web.goronald.facerecognitionsikemastc.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.io.File;
import java.util.List;

import ch.zhaw.facerecognitionlibrary.Helpers.CustomCameraView;
import ch.zhaw.facerecognitionlibrary.Helpers.FileHelper;
import ch.zhaw.facerecognitionlibrary.Helpers.MatOperation;
import ch.zhaw.facerecognitionlibrary.PreProcessor.PreProcessorFactory;
import ch.zhaw.facerecognitionlibrary.Recognition.Recognition;
import ch.zhaw.facerecognitionlibrary.Recognition.RecognitionFactory;
import id.web.goronald.facerecognitionsikemastc.R;

public class RecognitionActivity extends AppCompatActivity {
    private CustomCameraView mRecognitionView;
    private static final String TAG = "Recognition";
    private FileHelper fh;
    private Recognition rec;
    private PreProcessorFactory ppF;
    private ProgressBar progressBar;
    private boolean front_camera;
    private boolean night_portrait;
    private int exposure_compensation;

    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG,"called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_training);
        progressBar = (ProgressBar)findViewById(R.id.progressBar);
        fh = new FileHelper();
        File folder = new File(fh.getFolderPath());
        if(folder.mkdir() || folder.isDirectory()){
            Log.i(TAG,"New directory for photos created");
        } else {
            Log.i(TAG,"Photos directory already existing");
        }
        mRecognitionView = (CustomCameraView) findViewById(R.id.RecognitionView);
        // Use camera which is selected in settings
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        front_camera = sharedPref.getBoolean("key_front_camera", true);
        night_portrait = sharedPref.getBoolean("key_night_portrait", false);
        exposure_compensation = Integer.valueOf(sharedPref.getString("key_exposure_compensation", "20"));

        if (front_camera){
            mRecognitionView.setCameraIndex(1);
        } else {
            mRecognitionView.setCameraIndex(-1);
        }
        mRecognitionView.setVisibility(SurfaceView.VISIBLE);
        mRecognitionView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mRecognitionView != null)
            mRecognitionView.disableView();
    }

    public void onDestroy() {
        super.onDestroy();
        if (mRecognitionView != null)
            mRecognitionView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {

        if (night_portrait) {
            mRecognitionView.setNightPortrait();
        }

        if (exposure_compensation != 50 && 0 <= exposure_compensation && exposure_compensation <= 100)
            mRecognitionView.setExposure(exposure_compensation);
    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat imgRgba = inputFrame.rgba();
        Mat img = new Mat();
        imgRgba.copyTo(img);
        List<Mat> images = ppF.getProcessedImage(img, PreProcessorFactory.PreprocessingMode.RECOGNITION);
        Rect[] faces = ppF.getFacesForRecognition();

        // Selfie / Mirror mode
        if(front_camera){
            Core.flip(imgRgba,imgRgba,1);
        }
        if(images == null || images.size() == 0 || faces == null || faces.length == 0 || ! (images.size() == faces.length)){
            // skip
            return imgRgba;
        } else {
            faces = MatOperation.rotateFaces(imgRgba, faces, ppF.getAngleForRecognition());
            for(int i = 0; i<faces.length; i++){
                MatOperation.drawRectangleAndLabelOnPreview(imgRgba, faces[i], rec.recognize(images.get(i), ""), front_camera);
            }
            return imgRgba;
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        ppF = new PreProcessorFactory(getApplicationContext());

        final android.os.Handler handler = new android.os.Handler(Looper.getMainLooper());
        Thread t = new Thread(new Runnable() {
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                });
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                String algorithm = sharedPref.getString("key_classification_method", getResources().getString(R.string.eigenfaces));
                rec = RecognitionFactory.getRecognitionAlgorithm(getApplicationContext(), Recognition.RECOGNITION, algorithm);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                    }
                });
            }
        });

        t.start();

        // Wait until Eigenfaces loading thread has finished
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mRecognitionView.enableView();
    }

}
