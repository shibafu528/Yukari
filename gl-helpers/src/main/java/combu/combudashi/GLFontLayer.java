package combu.combudashi;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;

import javax.microedition.khronos.opengles.GL10;

import combu.combudashi.GLTexture2D.TextureSource;

public class GLFontLayer {

	private GLTexture2D fTexture;
	private TextureSource fTexBitmap;
	private Canvas fCanvas;
	private int fLayerWidth, fLayerHeight;

	/**
	 * フォントレイヤーを生成します。<br>
	 * 辺の長さは基本的には2のべき乗のみ使用できます。<br>
	 * それ以外の数値の有効性は機種依存となります。
	 * @param layer_width レイヤー幅
	 * @param layer_height レイヤー高さ
	 */
	public void createLayer(GL10 gl, int layer_width, int layer_height) {
		//Bitmapを作成する
		fLayerWidth = layer_width;
		fLayerHeight = layer_height;
		Bitmap layer = Bitmap.createBitmap(layer_width, layer_height, Config.ARGB_8888);
		fTexBitmap = TextureSource.fromBitmap(layer);
		//Canvasを取得する
		fCanvas = new Canvas(fTexBitmap.getBitmap());
		//テクスチャ生成
		fTexture = GLTexture2D.fromSource(gl, fTexBitmap);
	}

	public void onResume(GL10 gl) {
		if (fTexture != null) {
			fTexture.unloadTexture(gl);
			fTexture.bindTexture(gl, fTexBitmap);
		}
	}

	public void onDestroy(GL10 gl) {
		if (fTexture != null)
			fTexture.unloadTexture(gl);
	}

	/**
	 * フォントレイヤーの描画内容を消去します
	 */
	public void clearFontLayer() {
		fTexBitmap.getBitmap().eraseColor(0);
	}

	/**
	 * 呼び出したテキスト描画をテクスチャメモリに転送します
	 * @param gl GL10コンテキスト
	 */
	public void updateLayerTexture(GL10 gl) {
		fTexture.updateTexture(gl, fTexBitmap);
	}

	/**
	 * フォントレイヤーのテクスチャを取得します
	 * @return テクスチャ名(ID)
	 */
	public int getTexture() {
		return fTexture.get();
	}

	/**
	 * Canvasを取得します。GLRenderActivityとは座標系が異なることに注意してください。
	 * @return Canvasコンテキスト
	 */
	public Canvas getCanvas() {
		return fCanvas;
	}

	/**
	 * フォントレイヤーを画面上に描画します
	 * @param gl GL10コンテキスト
	 * @param world_height 描画空間の高さ(左下原点)
	 */
	public void drawTexture(GL10 gl, int world_height) {
		GLRenderUtil2D.drawTexture(gl, getTexture(), fLayerWidth / 2, world_height - fLayerHeight / 2, fLayerWidth, fLayerHeight, 0f, 1f, 1f);
	}
}
