package combu.combudashi;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.opengl.GLUtils;

import javax.microedition.khronos.opengles.GL10;

/**
 * OpenGLで平面に描画するためのテクスチャを保持するクラス
 *
 */
public class GLTexture2D {
	private int fTexId; //テクスチャID
	private int fTexSize; //テクスチャの1辺の長さ

	/**
	 * テクスチャの素材となるBitmapを保持するためのクラス
	 *
	 */
	public static class TextureSource {
		private Bitmap fBmp; //テクスチャバインド用ビットマップ
		private int fBaseWidth, fBaseHeight; //元々のサイズ
		private int fAfterSize; //加工後サイズ

		public TextureSource(Bitmap bmp, int baseWidth, int baseHeight, int afterSize) {
			fBmp = bmp;
			fBaseWidth = baseWidth;
			fBaseHeight = baseHeight;
			fAfterSize = afterSize;
		}


		public int getBaseWidth() {
			return fBaseWidth;
		}

		public int getBaseHeight() {
			return fBaseHeight;
		}

		public int getAfterSize() {
			return fAfterSize;
		}

		public Bitmap getBitmap() {
			return fBmp;
		}

		public void dispose() {
			fBmp.recycle();
		}

		private static int getTextureSize(int width, int height) {
			//長辺を確認して記憶する
			int longside = width;
			if (height > width)
				longside = height;
			//1を作成
			int bit = 1;
			//長辺を収められる2のべき乗の値を求める
			while (bit < longside) {
				bit = bit << 1;
			}
			//求めた値をテクスチャサイズとして返す
			return bit;
		}

		private static Bitmap resizeBitmap(Bitmap source, int width, int height) {
			//新しいBitmapを作成する
			Bitmap resized = Bitmap.createBitmap(width, height, Config.ARGB_8888);
			//Canvasを取得する
			Canvas c = new Canvas(resized);
			//リサイズデータの中心に転送するために座標を計算
			float left = (width / 2) - (source.getWidth() / 2);
			float top = (height / 2) - (source.getHeight() / 2);
			//新しいBitmapにソースを転送
			c.drawBitmap(source, left, top, null);
			//返す
			return resized;
		}

		private static Bitmap resizeBitmapLT(Bitmap source, int width, int height) {
			//新しいBitmapを作成する
			Bitmap resized = Bitmap.createBitmap(width, height, Config.ARGB_8888);
			//Canvasを取得する
			Canvas c = new Canvas(resized);
			//新しいBitmapにソースを転送
			c.drawBitmap(source, 0, 0, null);
			//返す
			return resized;
		}

		/**
		 * リソースを基にTextureSourceを作成します
		 * @param resources Androidリソースコンテキスト
		 * @param resId 読み込む画像のリソースID
		 * @return TextureSource
		 */
		public static TextureSource fromResource(Resources resources, int resId) {
			TextureSource source;

			//Bitmapをロードする
			Bitmap bmp = BitmapFactory.decodeResource(resources, resId);
			if (bmp == null)
				return null;

			//サイズ判定を行う
			int baseWidth = bmp.getWidth();
			int baseHeight = bmp.getHeight();
			int afterSize = getTextureSize(baseWidth, baseHeight);
			//サイズが元と一致しない場合はリサイズ
			if (baseWidth != afterSize || baseHeight != afterSize) {
				Bitmap src = bmp;
				bmp = resizeBitmap(src, afterSize, afterSize);
				src.recycle();
			}

			//数値を転送
			source = new TextureSource(bmp, baseWidth, baseHeight, afterSize);
			return source;
		}

		/**
		 * 継ぎ接ぎなテクスチャ画像を読み込みTextureSourceを作成します<br>
		 * リサイズ時の自動センタリングが行われません
		 * @param resources Androidリソースコンテキスト
		 * @param resId 読み込む画像のリソースID
		 * @return TextureSource
		 */
		public static TextureSource fromPatchworkResource(Resources resources, int resId) {
			TextureSource source;

			//Bitmapをロードする
			Bitmap bmp = BitmapFactory.decodeResource(resources, resId);
			if (bmp == null)
				return null;

			//サイズ判定を行う
			int baseWidth = bmp.getWidth();
			int baseHeight = bmp.getHeight();
			int afterSize = getTextureSize(baseWidth, baseHeight);
			//サイズが元と一致しない場合はリサイズ
			if (baseWidth != afterSize || baseHeight != afterSize) {
				Bitmap src = bmp;
				bmp = resizeBitmapLT(src, afterSize, afterSize);
				src.recycle();
			}

			//数値を転送
			source = new TextureSource(bmp, baseWidth, baseHeight, afterSize);
			return source;
		}

		/**
		 * Bitmapを基にTextureSourceを作成します
		 * @param bmp テクスチャに使用するBitmap
		 * @return TextureSource
		 */
		public static TextureSource fromBitmap(Bitmap bmp) {
			TextureSource source;
			//サイズ判定を行う
			int baseWidth = bmp.getWidth();
			int baseHeight = bmp.getHeight();
			int afterSize = getTextureSize(baseWidth, baseHeight);
			//サイズが元と一致しない場合はリサイズして数値転送
			if (baseWidth != afterSize || baseHeight != afterSize) {
				Bitmap src = bmp;
				bmp = resizeBitmap(src, afterSize, afterSize);
				source = new TextureSource(bmp, baseWidth, baseHeight, afterSize);
				return source;
			}

			//数値を転送
			source = new TextureSource(bmp, baseWidth, baseHeight, afterSize);
			return source;
		}

	}

	public GLTexture2D(int texId, int texSize) {
		fTexId = texId;
		fTexSize = texSize;
	}

	/**
	 * テクスチャ名(ID)を取得します
	 * @return テクスチャ名(ID)
	 */
	public int get() {
		return fTexId;
	}

	/**
	 * テクスチャの1辺の長さを取得します
	 * @return テクスチャの長さ
	 */
	public int getTexSize() {
		return fTexSize;
	}

	/**
	 * ビットマップを読み込み、テクスチャインスタンスを作成します
	 * @param gl GL10コンテキスト
	 * @param context Androidコンテキスト
	 * @param resId 読み込む画像のリソースID
	 * @return テクスチャインスタンス
	 */
	public static GLTexture2D fromResource(GL10 gl, Context context, int resId) {
		//使用するビットマップを読み込む
		TextureSource source = TextureSource.fromResource(context.getResources(), resId);
		if (source == null)
			return null;

		//テクスチャを生成する準備
		int[] textures = new int[1];
		gl.glGenTextures(1, textures, 0);
		gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
		//テクスチャの拡縮設定を行う
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
		//繰り返し方法を設定
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
		//テクスチャの色が下地の色を置き換えるように設定
		gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE);

		//ビットマップからテクスチャを生成する
		GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, source.getBitmap(), 0);
		//ビットマップを解放する
		source.dispose();

		return new GLTexture2D(textures[0], source.getAfterSize());
	}

	/**
	 * ビットマップを読み込み、テクスチャインスタンスを作成します
	 * 拡縮時のフィルタ設定を行います
	 * @param gl GL10コンテキスト
	 * @param context Androidコンテキスト
	 * @param resId 読み込む画像のリソースID
	 * @param min_filter 縮小時のフィルタ
	 * @param mag_filter 拡大時のフィルタ
	 * @return テクスチャインスタンス
	 */
	public static GLTexture2D fromResource(GL10 gl, Context context, int resId,
			int min_filter, int mag_filter) {
		//使用するビットマップを読み込む
		TextureSource source = TextureSource.fromResource(context.getResources(), resId);
		if (source == null)
			return null;

		//テクスチャを生成する準備
		int[] textures = new int[1];
		gl.glGenTextures(1, textures, 0);
		gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
		//テクスチャの拡縮設定を行う
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, min_filter);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, mag_filter);
		//繰り返し方法を設定
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
		//テクスチャの色が下地の色を置き換えるように設定
		gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE);

		//ビットマップからテクスチャを生成する
		GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, source.getBitmap(), 0);
		//ビットマップを解放する
		source.dispose();

		return new GLTexture2D(textures[0], source.getAfterSize());
	}

	/**
	 * ビットマップを読み込み、テクスチャインスタンスを作成します
	 * @param gl GL10コンテキスト
	 * @param context Androidコンテキスト
	 * @param resId 読み込む画像のリソースID
	 * @return テクスチャインスタンス
	 */
	public static GLTexture2D fromPatchworkResource(GL10 gl, Context context, int resId) {
		//使用するビットマップを読み込む
		TextureSource source = TextureSource.fromPatchworkResource(context.getResources(), resId);
		if (source == null)
			return null;

		//テクスチャを生成する準備
		int[] textures = new int[1];
		gl.glGenTextures(1, textures, 0);
		gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
		//テクスチャの拡縮設定を行う
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
		//繰り返し方法を設定
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
		//テクスチャの色が下地の色を置き換えるように設定
		gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE);

		//ビットマップからテクスチャを生成する
		GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, source.getBitmap(), 0);
		//ビットマップを解放する
		source.dispose();

		return new GLTexture2D(textures[0], source.getAfterSize());
	}

	/**
	 * ビットマップを読み込み、テクスチャインスタンスを作成します
	 * 拡縮時のフィルタ設定を行います
	 * @param gl GL10コンテキスト
	 * @param context Androidコンテキスト
	 * @param resId 読み込む画像のリソースID
	 * @param min_filter 縮小時のフィルタ
	 * @param mag_filter 拡大時のフィルタ
	 * @return テクスチャインスタンス
	 */
	public static GLTexture2D fromPatchworkResource(GL10 gl, Context context, int resId,
			int min_filter, int mag_filter) {
		//使用するビットマップを読み込む
		TextureSource source = TextureSource.fromPatchworkResource(context.getResources(), resId);
		if (source == null)
			return null;

		//テクスチャを生成する準備
		int[] textures = new int[1];
		gl.glGenTextures(1, textures, 0);
		gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
		//テクスチャの拡縮設定を行う
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, min_filter);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, mag_filter);
		//繰り返し方法を設定
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
		//テクスチャの色が下地の色を置き換えるように設定
		gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE);

		//ビットマップからテクスチャを生成する
		GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, source.getBitmap(), 0);
		//ビットマップを解放する
		source.dispose();

		return new GLTexture2D(textures[0], source.getAfterSize());
	}

	/**
	 * 既に生成されているTextureSourceを使用して、テクスチャインスタンスを作成します<br>
	 * TextureSourceは自分で解放してください
	 * @param gl GL10コンテキスト
	 * @param source Bitmapの格納されたTextureSource
	 * @return テクスチャインスタンス
	 */
	public static GLTexture2D fromSource(GL10 gl, TextureSource source) {
		//TextureSourceのnullチェック
		if (source == null)
			return null;

		//テクスチャを生成する準備
		int[] textures = new int[1];
		gl.glGenTextures(1, textures, 0);
		gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
		//テクスチャの拡縮設定を行う
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
		//繰り返し方法を設定
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
		//テクスチャの色が下地の色を置き換えるように設定
		gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE);

		//ビットマップからテクスチャを生成する
		GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, source.getBitmap(), 0);

		return new GLTexture2D(textures[0], source.getAfterSize());
	}

	/**
	 * テクスチャを読み込みます
	 * @param gl GL10コンテキスト
	 * @param context Androidコンテキスト
	 * @param resId 読み込む画像のリソースID
	 */
	public void loadTexture(GL10 gl, Context context, int resId) {
		GLTexture2D tex = fromResource(gl, context, resId);
		fTexId = tex.get();
		fTexSize = tex.getTexSize();
	}

	/**
	 * このインスタンスを再利用してテクスチャを読み込みます
	 * @param gl GL10コンテキスト
	 * @param source Bitmapの格納されたTextureSource
	 */
	public void bindTexture(GL10 gl, TextureSource source) {
		//テクスチャを生成する準備
		int[] textures = new int[1];
		gl.glGenTextures(1, textures, 0);
		gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
		//テクスチャの拡縮設定を行う
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
		//繰り返し方法を設定
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
		//テクスチャの色が下地の色を置き換えるように設定
		gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE);

		//ビットマップからテクスチャを生成する
		GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, source.getBitmap(), 0);

		fTexId = textures[0];
		fTexSize = source.getAfterSize();
	}

	/**
	 * 指定ソースでテクスチャデータの更新を行います
	 * @param gl
	 * @param source
	 */
	public void updateTexture(GL10 gl, TextureSource source) {
		//テクスチャの更新準備
		int[] textures = new int[1];
		textures[0] = this.get();
		gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
		//テクスチャの更新を行う
		GLUtils.texSubImage2D(GL10.GL_TEXTURE_2D, 0, 0, 0, source.getBitmap());
	}

	/**
	 * 読み込んだテクスチャを削除します
	 */
	public void unloadTexture(GL10 gl) {
		if (fTexId != 0) {
			gl.glDeleteTextures(1, new int[]{fTexId}, 0);
		}
	}
}
