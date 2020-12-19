package combu.framehelper;

/**
 * FPSの測定と均一化処理を行います
 * @author Shibafu
 *
 */
public class FPSManager {

	//FPS計測クラス
	private FPSChecker fpsChecker;
	//1フレームの長さ
	private long frameTime;
	//フレーム余り時間でどれだけSleep()したか
	private long sleepTime;

	public FPSManager(float fps) {
		fpsChecker = new FPSChecker(10);
		sleepTime = 0L;
		//1フレームの長さを計算する
		frameTime = (long)(1000F / fps);
	}

	//FPS計測を行う
	public void checkFPS() {
		fpsChecker.checkFPS();
	}

	//FPSを取得する
	public float getFPS() {
		return fpsChecker.getFPS();
	}

	//フレーム余りを埋め、フレームスキップが必要な場合超過フレーム数を返す
	public int homogenizeFrame() {
		//FPS前回計測からの経過を取得
		long elapsed = fpsChecker.getElapsedTime();
		//直前のSleep時間を引く
		elapsed -= sleepTime;

		//時間が余っているならその分Sleepで埋めてフレームの均一化を試みる
		if (elapsed < frameTime && elapsed > 0L) {
			//埋める時間を計算
			sleepTime = frameTime - elapsed;
			//埋める
			try {
				Thread.sleep(sleepTime);
			} catch (InterruptedException e) {}
			//フレーム残り時間を0にする
			elapsed = 0;
		}
		else {
			//時間が余っていないならSleep時間を0にする
			sleepTime = 0;
			//フレームスリープが必要か確かめるために、フレーム時間を引いておく
			elapsed -= frameTime;
		}
		//フレーム残り時間が1フレームを超過している場合、スキップ数を出す
		if (elapsed >= frameTime) {
			int skip = (int)(elapsed / frameTime);
			if (elapsed % frameTime > 0)
				skip++;//割った余りがあったら、スキップ数を１つカウント
			return skip;
		}
		else
			return 0;//フレーム超過がなかった場合は、スキップ数0を返す
	}
}
