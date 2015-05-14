package combu.framehelper;

import android.os.SystemClock;

import java.util.LinkedList;

/**
 * FPSの測定を行います
 * @author Shibafu
 *
 */
public class FPSChecker {

	//前回呼出時間
	private long prevTime;
	//前回呼出との差
	private long elapsedTime;

	//FPS
	private float fps;

	//呼出経過時間の合計
	private long times;

	//平均用フレーム経過時間サンプル
	private LinkedList<Long> elapsedTimeList;
	//サンプル数
	private int sampleNum;

	public FPSChecker(int sample_num) {
		//数値の初期化
		prevTime = 0L;
		elapsedTime = 0L;
		fps = 0F;
		times = 0L;
		//リストの初期化
		elapsedTimeList = new LinkedList<Long>();
		for (int i = 0; i < sample_num; i++)
			elapsedTimeList.add(0L);
		//サンプル数を記憶
		sampleNum = sample_num;
	}

	//FPSの計測
	public void checkFPS() {
		//現在時間を取得
		long now_time = SystemClock.uptimeMillis();

		//前回フレームからの経過時間を計算
		elapsedTime = now_time - prevTime;
		//現在時間を記憶
		prevTime = now_time;

		//経過時間を合計変数に加算
		times += elapsedTime;
		//サンプルリストに追加
		elapsedTimeList.add(elapsedTime);
		//リスト内のもっとも古いデータを溢れとして処理
		times -= elapsedTimeList.poll();

		//平均経過時間を計算
		long elapsed_average = times / sampleNum;

		//FPSを算出する
		if (elapsed_average != 0L)
			fps = 1000F / elapsed_average;
		else
			fps = 0F;//ゼロ除算防止
	}

	//現在FPSを返す
	public float getFPS() {
		return fps;
	}

	//最新のフレーム経過時間を取得
	public long getElapsedTime() {
		return elapsedTime;
	}
}
