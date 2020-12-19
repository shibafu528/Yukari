package combu.combudashi;

import javax.microedition.khronos.opengles.GL10;

/**
 * OpenGL ESでの基本的な3D描画処理を行うためのメソッド群クラスです
 *
 */
public class GLRenderUtil3D {
	
	public static float[] genCubeArray(float width, float height, float depth) {
		float[] v = GLRenderUtilCore.getVerticles(12 * 6);
		
		//前面
		v[ 0] = -0.5f * width; v[ 1] = -0.5f * height; v[ 2] = 0.5f * depth;
		v[ 3] =  0.5f * width; v[ 4] = -0.5f * height; v[ 5] = 0.5f * depth;
		v[ 6] = -0.5f * width; v[ 7] =  0.5f * height; v[ 8] = 0.5f * depth;
		v[ 9] =  0.5f * width; v[10] =  0.5f * height; v[11] = 0.5f * depth;
		
		//背面
		v[12] = -0.5f * width; v[13] = -0.5f * height; v[14] = -0.5f * depth;
		v[15] =  0.5f * width; v[16] = -0.5f * height; v[17] = -0.5f * depth;
		v[18] = -0.5f * width; v[19] =  0.5f * height; v[20] = -0.5f * depth;
		v[21] =  0.5f * width; v[22] =  0.5f * height; v[23] = -0.5f * depth;
		
		//左面
		v[24] = -0.5f * width; v[25] = -0.5f * height; v[26] =  0.5f * depth;
		v[27] = -0.5f * width; v[28] = -0.5f * height; v[29] = -0.5f * depth;
		v[30] = -0.5f * width; v[31] =  0.5f * height; v[32] =  0.5f * depth;
		v[33] = -0.5f * width; v[34] =  0.5f * height; v[35] = -0.5f * depth;
		
		//右面
		v[36] =  0.5f * width; v[37] = -0.5f * height; v[38] =  0.5f * depth;
		v[39] =  0.5f * width; v[40] = -0.5f * height; v[41] = -0.5f * depth;
		v[42] =  0.5f * width; v[43] =  0.5f * height; v[44] =  0.5f * depth;
		v[45] =  0.5f * width; v[46] =  0.5f * height; v[47] = -0.5f * depth;
		
		//上面
		v[48] = -0.5f * width; v[49] =  0.5f * height; v[50] =  0.5f * depth;
		v[51] =  0.5f * width; v[52] =  0.5f * height; v[53] =  0.5f * depth;
		v[54] = -0.5f * width; v[55] =  0.5f * height; v[56] = -0.5f * depth;
		v[57] =  0.5f * width; v[58] =  0.5f * height; v[59] = -0.5f * depth;
		
		//底面
		v[60] = -0.5f * width; v[61] = -0.5f * height; v[62] =  0.5f * depth;
		v[63] =  0.5f * width; v[64] = -0.5f * height; v[65] =  0.5f * depth;
		v[66] = -0.5f * width; v[67] = -0.5f * height; v[68] = -0.5f * depth;
		v[69] =  0.5f * width; v[70] = -0.5f * height; v[71] = -0.5f * depth;
		
		return v;
	}
	
	/**
	 * 単純な色配列を作成します
	 * @param red
	 * @param green
	 * @param blue
	 * @param alpha
	 * @param 頂点数
	 * @return
	 */
	public static float[] genColorArray(int red, int green, int blue, int alpha,
			int vert_num) {
		int arraySize = vert_num * 4;
		float[] colors = GLRenderUtilCore.getColors(arraySize);
		for (int i = 0; i < arraySize; i++) {
			colors[i++] = (float)red / 255f;
			colors[i++] = (float)green / 255f;
			colors[i++] = (float)blue / 255f;
			colors[i]   = (float)alpha / 255f;
		}
		return colors;
	}
	
	public static void drawCube(GL10 gl,
			float x, float y, float z,
			float width, float height, float depth,
			int r, int g, int b, int a) {
		//頂点配列を作成
		float[] verticles = genCubeArray(width, height, depth);
		//色配列を作成
		float[] colors = genColorArray(r, g, b, a, verticles.length);
		
		//行列プッシュを行う
		gl.glPushMatrix();

		//引数の座標に矩形を移動させる
		gl.glTranslatef(x, y, z);
		//頂点座標配列をOpenGLに渡す
		gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
		gl.glVertexPointer(3, GL10.GL_FLOAT, 0, GLRenderUtilCore.makeVerticlesBuffer(verticles));
		//色配列をOpenGLに渡す
		gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
		gl.glColorPointer(4, GL10.GL_FLOAT, 0, GLRenderUtilCore.makeColorsBuffer(colors));
		
		/* -- ここから描画 -- */
		
		//前面の描画
		gl.glNormal3f(0.0f, 0.0f, 1.0f);
		gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
		
		//背面の描画
		gl.glNormal3f(0.0f, 0.0f, -1.0f);
		gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 4, 4);
		
		//左面の描画
		gl.glNormal3f(-1.0f, 0.0f, 0.0f);
		gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 8, 4);
		
		//右面の描画
		gl.glNormal3f(1.0f, 0.0f, 0.0f);
		gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 12, 4);
		
		//上面の描画
		gl.glNormal3f(0.0f, 1.0f, 0.0f);
		gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 16, 4);
		
		//底面の描画
		gl.glNormal3f(0.0f, -1.0f, 0.0f);
		gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 20, 4);

		/* -- ここまで描画 -- */
		
		//行列状態を戻す
		gl.glPopMatrix();

		//ステータスを元に戻す
		gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
		gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
	}
	
}
