package combu.combudashi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Hashtable;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

/**
 * OpenGL ESで頻繁に使用する命令のメソッド群の提供、およびメモリ周りの管理を行うクラスです
 *
 */
public class GLRenderUtilCore {

	/**
	 * OpenGLのアルファブレンディングの設定を行うための補助クラスです
	 *
	 */
	public static class AlphaBlendingUtil {
		/**
		 * アルファブレンドの設定を指定するための定数
		 *
		 */
		public enum AlphaBlendingMode {
			/** 通常合成 */
			BLEND_NORMAL,
			/** 加算合成 */
			BLEND_ADD,
			/** 乗算合成 */
			BLEND_COMPOSITING
		}
	
		/**
		 * OpenGLのアルファブレンディングを有効にします
		 * @param gl GL10コンテキスト
		 * @param mode 合成アルゴリズム
		 */
		public static final void enableAlphaBlending(GL10 gl, AlphaBlendingMode mode) {
			//アルファブレンディングを有効にする
			gl.glEnable(GL10.GL_BLEND);
			//mode変数に応じて合成アルゴリズムの設定を呼び出す
			switch (mode) {
			case BLEND_NORMAL:
				gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
				break;
			case BLEND_ADD:
				gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE);
				break;
			case BLEND_COMPOSITING:
				gl.glBlendFunc(GL10.GL_ZERO, GL10.GL_SRC_COLOR);
				break;
			}
		}
	
		/**
		 * OpenGLのアルファブレンディングを無効にします
		 * @param gl GL10コンテキスト
		 */
		public static final void disableAlphaBlending(GL10 gl) {
			//アルファブレンディングを無効にする
			gl.glDisable(GL10.GL_BLEND);
		}
	}

	//配列バッファ
	private static Hashtable<Integer, float[]> verticlesPool = new Hashtable<Integer, float[]>();
	private static Hashtable<Integer, float[]> colorsPool = new Hashtable<Integer, float[]>();
	private static Hashtable<Integer, float[]> coordsPool = new Hashtable<Integer, float[]>();
	//FloatBuffer保持
	private static Hashtable<Integer, FloatBuffer> fbVerticlesPool = new Hashtable<Integer, FloatBuffer>();
	private static Hashtable<Integer, FloatBuffer> fbColorsPool = new Hashtable<Integer, FloatBuffer>();
	private static Hashtable<Integer, FloatBuffer> fbCoordsPool = new Hashtable<Integer, FloatBuffer>();

	/**
	 * 頂点座標バッファからメモリ確保済み配列を取得します
	 * @param length 配列長
	 * @return
	 */
	public static float[] getVerticles(int length) {
		if (verticlesPool.containsKey(length)) {
			return verticlesPool.get(length);
		}
		float[] v = new float[length];
		verticlesPool.put(length, v);
		return v;
	}

	/**
	 * 色座標バッファからメモリ確保済み配列を取得します
	 * @param length 配列長
	 * @return
	 */
	public static float[] getColors(int length) {
		if (colorsPool.containsKey(length)) {
			return colorsPool.get(length);
		}
		float[] v = new float[length];
		colorsPool.put(length, v);
		return v;
	}

	/**
	 * UV座標バッファからメモリ確保済み配列を取得します
	 * @param length 配列長
	 * @return
	 */
	public static float[] getCoords(int length) {
		if (coordsPool.containsKey(length)) {
			return coordsPool.get(length);
		}
		float[] v = new float[length];
		coordsPool.put(length, v);
		return v;
	}

	/**
	 * 頂点バッファを作成します。キャッシュ付き。
	 * @param array 頂点配列
	 * @return
	 */
	public static FloatBuffer makeVerticlesBuffer(float[] array) {
		FloatBuffer fb = null;
		if (fbVerticlesPool.containsKey(array.length)) {
			fb = fbVerticlesPool.get(array.length);
			fb.clear();
			fb.put(array);
			fb.position(0);
			return fb;
		}
		fb = makeFloatBuffer(array);
		fbVerticlesPool.put(array.length, fb);
		return fb;
	}

	/**
	 * 色頂点バッファを作成します。キャッシュ付き。
	 * @param array 頂点配列
	 * @return
	 */
	public static FloatBuffer makeColorsBuffer(float[] array) {
		FloatBuffer fb = null;
		if (fbColorsPool.containsKey(array.length)) {
			fb = fbColorsPool.get(array.length);
			fb.clear();
			fb.put(array);
			fb.position(0);
			return fb;
		}
		fb = makeFloatBuffer(array);
		fbColorsPool.put(array.length, fb);
		return fb;
	}

	/**
	 * テクスチャマッピング頂点バッファを作成します。キャッシュ付き。
	 * @param array 頂点配列
	 * @return
	 */
	public static FloatBuffer makeCoordsBuffer(float[] array) {
		FloatBuffer fb = null;
		if (fbCoordsPool.containsKey(array.length)) {
			fb = fbCoordsPool.get(array.length);
			fb.clear();
			fb.put(array);
			fb.position(0);
			return fb;
		}
		fb = makeFloatBuffer(array);
		fbCoordsPool.put(array.length, fb);
		return fb;
	}

	/**
	 * システム上のメモリを確保し、引数の配列をバッファに格納します
	 * @param array float型の配列
	 * @return 配列を転送したFloatBufferインスタンス
	 */
	public static final FloatBuffer makeFloatBuffer(float[] array) {
		//システム上のメモリを直接確保する
		ByteBuffer bytebuf = ByteBuffer.allocateDirect(array.length * 4);
		//エンディアンを合わせる
		bytebuf.order(ByteOrder.nativeOrder());
		//確保したバッファをFloat形式にする
		FloatBuffer floatbuf = bytebuf.asFloatBuffer();
		//引数の配列をバッファに転送する
		floatbuf.put(array);
		floatbuf.position(0);
		return floatbuf;
	}

	/**
	 * システム上のメモリを確保し、引数の配列をバッファに格納します
	 * @param list float型の配列
	 * @return 配列を転送したFloatBufferインスタンス
	 */
	public static final FloatBuffer makeFloatBuffer(List<Float> list) {
		//システム上のメモリを直接確保する
		ByteBuffer bytebuf = ByteBuffer.allocateDirect(list.size() * 4);
		//エンディアンを合わせる
		bytebuf.order(ByteOrder.nativeOrder());
		//確保したバッファをFloat形式にする
		FloatBuffer floatbuf = bytebuf.asFloatBuffer();
		//引数の配列をバッファに転送する
		for (Float aFloat : list) {
			floatbuf.put(aFloat);
		}
		floatbuf.position(0);
		return floatbuf;
	}

}
