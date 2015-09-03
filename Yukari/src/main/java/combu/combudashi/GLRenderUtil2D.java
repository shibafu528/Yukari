package combu.combudashi;

import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

import combu.combudashi.GLRenderUtilCore.AlphaBlendingUtil;
import combu.combudashi.GLRenderUtilCore.AlphaBlendingUtil.AlphaBlendingMode;

/**
 * OpenGL ESでの2D描画処理を行うためのメソッド群クラスです
 *
 */
public class GLRenderUtil2D {

	public static float[] genRectArray(float width, float height) {
		float[] vert = GLRenderUtilCore.getVerticles(8);
		vert[0] = -0.5f * width; vert[1] = -0.5f * height;
		vert[2] =  0.5f * width; vert[3] = -0.5f * height;
		vert[4] = -0.5f * width; vert[5] =  0.5f * height;
		vert[6] =  0.5f * width; vert[7] =  0.5f * height;
		return vert;
	}

	/**
	 * 単純な色配列を作成します
	 * @param red
	 * @param green
	 * @param blue
	 * @param alpha
	 * @return
	 */
	public static float[] genColorArray(int red, int green, int blue, int alpha) {
		float[] colors = GLRenderUtilCore.getColors(16);
		for (int i = 0; i < 16; i++) {
			colors[i++] = (float)red / 255f;
			colors[i++] = (float)green / 255f;
			colors[i++] = (float)blue / 255f;
			colors[i]   = (float)alpha / 255f;
		}
		return colors;
	}

	/**
	 * 三角形を描画します<br>
	 * 座標は物体の中心を基準とします<br>
	 * 色情報は0-255で指定します
	 * @param gl GL10コンテキスト
	 * @param x X座標
	 * @param y Y座標
	 * @param radius 半径
	 * @param red 赤明度
	 * @param green 緑明度
	 * @param blue 青明度
	 * @param alpha アルファ値
	 * @param angle 回転度
	 * @param autoBlend 自動ブレンド(自前でアルファブレンドの設定を行う場合のみfalseにして下さい)
	 */
	public static void drawTriangle(GL10 gl,
			float x, float y, float radius,
			int red, int green, int blue, int alpha, float angle,
			boolean autoBlend) {
		//頂点座標配列を作成、ここでは大きさのみを考えて作成
		float[] verticles = GLRenderUtilCore.getVerticles(6);
		verticles[0] = -0.5f * radius;	verticles[1] = -0.5f * radius;
		verticles[2] =  0.5f * radius;	verticles[3] = -0.5f * radius;
		verticles[4] =  0.0f * radius;	verticles[5] =  0.5f * radius;

		//色配列を作成、どうせ全部同じ色
		float[] colors = genColorArray(red, green, blue, alpha);

		//ポリゴン描画を呼び出す
		drawPolygon(gl, x, y, verticles, 2, 3, colors, 4, angle, autoBlend);
	}

	/**
	 * 長方形を描画します<br>
	 * 座標は物体の中心を基準とします<br>
	 * 色情報は0-255で指定します
	 * @param gl GL10コンテキスト
	 * @param x X座標
	 * @param y Y座標
	 * @param width 幅
	 * @param height 高さ
	 * @param red 赤明度
	 * @param green 緑明度
	 * @param blue 青明度
	 * @param alpha アルファ値
	 * @param angle 回転度
	 * @param autoBlend 自動ブレンド(自前でアルファブレンドの設定を行う場合のみfalseにして下さい)
	 */
	public static void drawRectangle(GL10 gl,
			float x, float y, float width, float height,
			int red, int green, int blue, int alpha, float angle,
			boolean autoBlend) {

		//頂点座標配列を作成、ここでは大きさのみを考えて作成
		float[] verticles = genRectArray(width, height);

		//色配列を作成、どうせ全部同じ色
		float[] colors = genColorArray(red, green, blue, alpha);

		//ポリゴン描画を呼び出す
		drawPolygon(gl, x, y, verticles, 2, 4, colors, 4, angle, autoBlend);
	}

	/**
	 * ポリゴンを描画します<br>
	 * 頂点座標配列、色配列を自前で作ってください
	 * @param gl GLコンテキスト
	 * @param x X座標
	 * @param y Y座標
	 * @param verticles 頂点座標配列
	 * @param vertex_size 頂点座標配列の1点のサイズ(x,yの2点なら2を指定)
	 * @param vertex_count 頂点の数
	 * @param colors 色配列
	 * @param color_size 色配列の1点のサイズ(RGBAなら4を指定)
	 * @param angle 回転度
	 * @param auto_blend 自動ブレンド(自前でアルファブレンドの設定を行う場合のみfalseにして下さい)
	 */
	public static void drawPolygon(GL10 gl,
			float x, float y,
			float[] verticles, int vertex_size, int vertex_count, float[] colors, int color_size,
			float angle, boolean auto_blend) {
		if (auto_blend) {
			//アルファブレンディングの設定を行う
			AlphaBlendingUtil.enableAlphaBlending(gl, AlphaBlendingMode.BLEND_NORMAL);
		}

		//行列プッシュを行う
		gl.glPushMatrix();

		//引数の座標に矩形を移動させる
		gl.glTranslatef(x, y, 0);
		//矩形の回転を行う
		gl.glRotatef(angle, 0f, 0f, 1f);
		//頂点座標配列をOpenGLに渡す
		gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
		gl.glVertexPointer(vertex_size, GL10.GL_FLOAT, 0, GLRenderUtilCore.makeVerticlesBuffer(verticles));
		//色配列をOpenGLに渡す
		gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
		gl.glColorPointer(color_size, GL10.GL_FLOAT, 0, GLRenderUtilCore.makeColorsBuffer(colors));
		//描画を実行する
		gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, vertex_count);

		//行列状態を戻す
		gl.glPopMatrix();

		if (auto_blend) {
			//アルファブレンディングの設定を元に戻す
			AlphaBlendingUtil.disableAlphaBlending(gl);
		}
	}

	/**
	 * テクスチャを描画します<br>
	 * 座標は物体の中心を基準とします
	 * @param gl GL10コンテキスト
	 * @param texture_id テクスチャ名(ID)
	 * @param x X座標
	 * @param y Y座標
	 * @param width 幅
	 * @param height 高さ
	 * @param angle 回転度
	 * @param scale_x 横拡大率
	 * @param scale_y 縦拡大率
	 */
	public static void drawTexture(GL10 gl, int texture_id,
			float x, float y, float width, float height,
			float angle, float scale_x, float scale_y) {
		//テクスチャマッピング座標配列を作成
		float[] coords = GLRenderUtilCore.getCoords(8);
		coords[0] = 0f; coords[1] = 1f;
		coords[2] = 1f; coords[3] = 1f;
		coords[4] = 0f; coords[5] = 0f;
		coords[6] = 1f; coords[7] = 0f;
		//描画呼出
		drawTexture(gl, texture_id, coords, 2, x, y, width, height, angle, scale_x, scale_y);
	}

	/**
	 * テクスチャを描画します<br>
	 * 座標は物体の中心を基準とします
	 * @param gl GLコンテキスト
	 * @param texture_id テクスチャ名(ID)
	 * @param coords テクスチャマッピング座標配列バッファ
	 * @param coords_size テクスチャマッピング座標配列の1点のサイズ
	 * @param x X座標
	 * @param y Y座標
	 * @param width 幅
	 * @param height 高さ
	 * @param angle 回転度
	 * @param scale_x 横拡大率
	 * @param scale_y 縦拡大率
	 */
	public static void drawTexture(GL10 gl, int texture_id,
			FloatBuffer coords, int coords_size,
			float x, float y, float width, float height,
			float angle, float scale_x, float scale_y) {

		//頂点座標配列を作成、ここでは大きさのみを考えて作成
		float[] verticles = genRectArray(width, height);

		//アルファブレンディングの設定を行う
		AlphaBlendingUtil.enableAlphaBlending(gl, AlphaBlendingMode.BLEND_NORMAL);

		//2Dテクスチャを有効にする
		gl.glEnable(GL10.GL_TEXTURE_2D);
		//テクスチャオブジェクトを指定する
		gl.glBindTexture(GL10.GL_TEXTURE_2D, texture_id);

		//行列プッシュを行う
		gl.glPushMatrix();

		//引数の座標に矩形を移動させる
		gl.glTranslatef(x, y, 0);
		//矩形の回転を行う
		gl.glRotatef(angle, 0f, 0f, 1f);
		//矩形の拡縮を行う
		gl.glScalef(scale_x, scale_y, 1.0f);
		//頂点座標配列をOpenGLに渡す
		gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
		gl.glVertexPointer(2, GL10.GL_FLOAT, 0, GLRenderUtilCore.makeVerticlesBuffer(verticles));
		//色を設定
		gl.glColor4f(1f, 1f, 1f, 1f);
		//テクスチャマッピングを行う
		gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
		gl.glTexCoordPointer(coords_size, GL10.GL_FLOAT, 0, coords);
		//描画を実行する
		gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);

		//行列状態を戻す
		gl.glPopMatrix();

		//2Dテクスチャを無効にする
		gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
		gl.glDisable(GL10.GL_TEXTURE_2D);

		//アルファブレンディングの設定を元に戻す
		AlphaBlendingUtil.disableAlphaBlending(gl);
	}

	/**
	 * テクスチャを描画します<br>
	 * 座標は物体の中心を基準とします
	 * @param gl GLコンテキスト
	 * @param texture_id テクスチャ名(ID)
	 * @param coords テクスチャマッピング座標配列
	 * @param coords_size テクスチャマッピング座標配列の1点のサイズ
	 * @param x X座標
	 * @param y Y座標
	 * @param width 幅
	 * @param height 高さ
	 * @param angle 回転度
	 * @param scale_x 横拡大率
	 * @param scale_y 縦拡大率
	 */
	public static void drawTexture(GL10 gl, int texture_id,
			float[] coords, int coords_size,
			float x, float y, float width, float height,
			float angle, float scale_x, float scale_y) {

		//テクスチャマッピング座標バッファを作成
		FloatBuffer coords_buffer = GLRenderUtilCore.makeCoordsBuffer(coords);
		//呼出
		drawTexture(gl, texture_id, coords_buffer, coords_size, x, y, width, height, angle, scale_x, scale_y);
	}

	/**
	 * テクスチャを描画します<br>
	 * 座標は物体の中心を基準とします
	 * @param gl GL10コンテキスト
	 * @param texture テクスチャインスタンス
	 * @param x X座標
	 * @param y Y座標
	 * @param width 幅
	 * @param height 高さ
	 * @param angle 回転度
	 * @param scale_x 横拡大率
	 * @param scale_y 縦拡大率
	 */
	public static void drawTexture(GL10 gl, GLTexture2D texture,
			float x, float y, float width, float height,
			float angle, float scale_x, float scale_y) {
		//呼出
		drawTexture(gl, texture.get(), x, y, width, height, angle, scale_x, scale_y);
	}

	/**
	 * テクスチャを描画します<br>
	 * 座標は物体の中心を基準とします<br>
	 * 幅および高さはテクスチャのサイズが使用されます
	 * @param gl GL10コンテキスト
	 * @param texture テクスチャインスタンス
	 * @param x X座標
	 * @param y Y座標
	 * @param angle 回転度
	 * @param scale_x 横拡大率
	 * @param scale_y 縦拡大率
	 */
	public static void drawTexture(GL10 gl, GLTexture2D texture,
			float x, float y, float angle, float scale_x, float scale_y) {
		//呼出
		drawTexture(gl, texture.get(), x, y, texture.getTexSize(), texture.getTexSize(), angle, scale_x, scale_y);
	}

	/**
	 * テクスチャの指定領域を描画します<br>
	 * 座標は物体の中心を基準とします<br>
	 * 幅および高さは領域サイズが使用されます
	 * @param gl GLコンテキスト
	 * @param texture テクスチャインスタンス
	 * @param x X座標
	 * @param y Y座標
	 * @param rectX 領域始点X
	 * @param rectY 領域始点Y
	 * @param rectW 領域の幅
	 * @param rectH 領域の高さ
	 * @param angle 回転度
	 * @param scale_x 横拡大率
	 * @param scale_y 縦拡大率
	 */
	public static void drawTextureRect(GL10 gl, GLTexture2D texture,
			float x, float y, float rectX, float rectY, float rectW, float rectH,
			float angle, float scale_x, float scale_y) {
		//テクスチャサイズを取得
		float texSize = texture.getTexSize();
		//UV座標を計算
		float u = rectX / texSize;
		float v = rectY / texSize;
		float u_end = rectW / texSize + u;
		float v_end = rectH / texSize + v;
		//マッピング配列を作成
		float[] coords = GLRenderUtilCore.getCoords(8);
		coords[0] =     u; coords[1] = v_end;
		coords[2] = u_end; coords[3] = v_end;
		coords[4] =     u; coords[5] = v;
		coords[6] = u_end; coords[7] = v;
		//描画呼出
		drawTexture(gl, texture.get(), coords, 2, x, y, rectW, rectH, angle, scale_x, scale_y);
	}
}
