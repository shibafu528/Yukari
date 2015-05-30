package shibafu.dissonance.activity;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout.LayoutParams;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import combu.combudashi.GLRenderUtil2D;
import combu.combudashi.GLTexture2D;
import combu.framehelper.FPSManager;
import shibafu.dissonance.R;
import shibafu.dissonance.activity.base.ActionBarYukariBase;

import static combu.combudashi.GLRenderUtilCore.AlphaBlendingUtil;
import static combu.combudashi.GLRenderUtilCore.makeFloatBuffer;

/**
 * Created by shibafu on 15/05/13.
 */
public class AboutActivity extends ActionBarYukariBase {
    private GLSurfaceView view;

    private int touchCounter = 0;
    private boolean touching = false;

    private MediaPlayer mpYukari;
    private MediaPlayer mpDefault;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        view = new GLSurfaceView(this);
        view.setRenderer(new Renderer());
        setContentView(view);

        getSupportActionBar().hide();

        View overlay = getLayoutInflater().inflate(R.layout.activity_about, null);
        addContentView(overlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        TextView[] textViews = {(TextView) overlay.findViewById(R.id.textView1), (TextView) overlay.findViewById(R.id.textView2)};
        Typeface typeface = Typeface.createFromAsset(getAssets(), "Roboto-Thin.ttf");
        for (TextView textView : textViews) {
            textView.setTypeface(typeface);
        }

        mpDefault = MediaPlayer.create(this, R.raw.se_reply);
        mpYukari = MediaPlayer.create(this, R.raw.y_apply);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mpDefault.release();
        mpYukari.release();
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.onResume();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touching = true;
                break;
            case MotionEvent.ACTION_UP:
                if (touching) {
                    touchCounter++;
                    touching = false;

                    if (touchCounter % 32 == 0) {
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        if (sp.getBoolean("j_yukari_voice", false)) {
                            // to disable
                            sp.edit().putBoolean("j_yukari_voice", false).apply();
                            // play sound
                            mpDefault.start();
                        } else {
                            // to enable
                            sp.edit().putBoolean("j_yukari_voice", true).apply();
                            // play sound
                            mpYukari.start();
                        }
                    }
                }
                break;
        }
        return true;
    }

    @Override
    public void onServiceConnected() {

    }

    @Override
    public void onServiceDisconnected() {

    }

    private class Renderer implements GLSurfaceView.Renderer {
        private FPSManager fpsManager = new FPSManager(30);
        private boolean skipFrame;
        private int surfaceWidth;
        private int surfaceHeight;
        private float ratio;
        private GLTexture2D texYukariLogo;
        private float logoRotateX, logoRotateY, logoRotateZ, logoRotateAngle;
        private int vboGrid, vboGridCount;

        public Renderer() {
            Random r = new Random();
            logoRotateX = r.nextFloat();
            logoRotateY = r.nextFloat();
            logoRotateZ = r.nextFloat();
        }

        private void draw(GL10 gl) {
            GL11 gl11 = (GL11) gl;

            // 画面のクリア
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

            gl.glEnable(GL10.GL_DEPTH_TEST);
            // グリッド箱とロゴの描画
            gl.glPushMatrix();
            {
                gl.glRotatef(-logoRotateAngle, logoRotateX, logoRotateY, logoRotateZ);
                gl.glScalef(0.75f, 0.75f, 0.75f);
                gl.glPushMatrix();
                {
                    gl.glScalef(0.625f, 0.625f, 0.625f);
                    gl.glPushMatrix();
                    {
                        gl.glTranslatef(0.0f, -1.0f, 0.0f);
                        gl.glRotatef(90.0f, 1.0f, 0.0f, 0.0f);
                        drawGrid(gl11);
                    }
                    gl.glPopMatrix();
                    gl.glPushMatrix();
                    {
                        gl.glTranslatef(0.0f, 1.0f, 0.0f);
                        gl.glRotatef(90.0f, 1.0f, 0.0f, 0.0f);
                        drawGrid(gl11);
                    }
                    gl.glPopMatrix();
                    gl.glPushMatrix();
                    {
                        gl.glTranslatef(-1.0f, 0.0f, 0.0f);
                        gl.glRotatef(90.0f, 0.0f, 1.0f, 0.0f);
                        drawGrid(gl11);
                    }
                    gl.glPopMatrix();
                    gl.glPushMatrix();
                    {
                        gl.glTranslatef(1.0f, 0.0f, 0.0f);
                        gl.glRotatef(90.0f, 0.0f, 1.0f, 0.0f);
                        drawGrid(gl11);
                    }
                    gl.glPopMatrix();
                    gl.glPushMatrix();
                    {
                        gl.glTranslatef(0.0f, 0.0f, -1.0f);
                        drawGrid(gl11);
                    }
                    gl.glPopMatrix();
                    gl.glPushMatrix();
                    {
                        gl.glTranslatef(0.0f, 0.0f, 1.0f);
                        drawGrid(gl11);
                    }
                    gl.glPopMatrix();
                }
                gl.glPopMatrix();
                GLRenderUtil2D.drawTexture(gl, texYukariLogo, 0f, 0f, 1f, 1f, 0f, 1f, 1f);
            }
            gl.glPopMatrix();
            gl.glDisable(GL10.GL_DEPTH_TEST);
        }

        private void drawGrid(GL11 gl11) {
            gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, vboGrid);
            gl11.glVertexPointer(2, GL10.GL_FLOAT, 0, 0);
            gl11.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            gl11.glColor4f(0.47f, 0.29f, 0.73f, 1f);
            gl11.glDrawArrays(GL10.GL_LINES, 0, vboGridCount);
            gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, 0);
        }

        private void update(GL10 gl) {
            logoRotateAngle++;
            if (logoRotateAngle % 4.0f == 0f) {
                switch (new Random().nextInt(3)) {
                    case 0:
                        logoRotateX += 0.006f;
                        break;
                    case 1:
                        logoRotateY += 0.006f;
                        break;
                    case 2:
                        logoRotateZ += 0.006f;
                        break;
                }
            }
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            // フレームスキップを一時的に無効化する
            skipFrame = false;

            // アルファブレンドを有効
            AlphaBlendingUtil.enableAlphaBlending(gl, AlphaBlendingUtil.AlphaBlendingMode.BLEND_NORMAL);

            // 基本背景色の設定
            gl.glClearColor(0.92f, 0.70f, 0.86f, 1);
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT);

            // テクスチャロード
            texYukariLogo = GLTexture2D.fromResource(gl, AboutActivity.this, R.drawable.ic_launcher);

            // グリッドの座標情報をつくってバッファに投げ込む
            List<Float> gridVerts = new ArrayList<>();
            for (float x = -1.0f; x < 1.1f; x += 0.1f) {
                gridVerts.add(x);
                gridVerts.add(-1.0f);
                gridVerts.add(x);
                gridVerts.add(1.0f);
                gridVerts.add(-1.0f);
                gridVerts.add(x);
                gridVerts.add(1.0f);
                gridVerts.add(x);
            }
            GL11 gl11 = (GL11)gl;
            int[] bufferIds = new int[1];
            gl11.glGenBuffers(1, bufferIds, 0);
            gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferIds[0]);
            gl11.glBufferData(GL11.GL_ARRAY_BUFFER, gridVerts.size() * 4, makeFloatBuffer(gridVerts), GL11.GL_STATIC_DRAW);
            vboGrid = bufferIds[0];
            vboGridCount = gridVerts.size() / 2;
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            // フレームスキップを一時的に無効化する
            skipFrame = false;
            // サーフェイスのサイズを記憶する
            surfaceWidth = width;
            surfaceHeight = height;
            ratio = (float) height / width;
            // ビューポートを設定
            gl.glViewport(0, 0, width, height);
            // 座標系の設定
            gl.glMatrixMode(GL10.GL_PROJECTION);
            gl.glLoadIdentity();

            gl.glOrthof(-1f, 1f, -1f * ratio, 1f * ratio, -1.75f, 1.75f);

            // 画面サイズと比をログ出力
            Log.d("Renderer", "w:" + width + " h:" + height + " r:" + ratio);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            // FPSの計測を行う
            fpsManager.checkFPS();

            // フレームスキップが有効ならその処理
            if (skipFrame) {
                // フレームの均一化を行い、必要フレームスキップ数を受け取る
                int skipFrames = fpsManager.homogenizeFrame();
                // フレームスキップを行う
                for (int i = 0; i < skipFrames; i++)
                    update(gl);
            } else {
                // 次のフレームから有効とする
                skipFrame = true;
            }
            // 定期処理メソッドを呼び出す
            update(gl);
            // 描画メソッドを呼び出す
            {
                // モデルビュー行列の設定を行う
                gl.glMatrixMode(GL10.GL_MODELVIEW);
                gl.glLoadIdentity();
                // メソッドを呼び出す
                draw(gl);
            }
        }
    }
}
