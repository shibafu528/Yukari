package combu.excompass;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;

public class Compass implements SensorEventListener{
	
	private static final int[][] AXIS_X = {
		{ SensorManager.AXIS_X, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y },
		{ SensorManager.AXIS_X, SensorManager.AXIS_Z, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Z }
	};
	
	private static final int[][] AXIS_Y = {
		{ SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X },
		{ SensorManager.AXIS_Z, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Z, SensorManager.AXIS_X }
	};
	
	/** 端末を地面に向かって水平に置いた状態を基準とします */
	public static final int BASE_HORIZONTAL = 0;
	/** 端末を地面に向かって垂直に立てた状態を基準とします */
	public static final int BASE_VERTICAL = 1;
	
	private int axisBase = BASE_VERTICAL;
	
	private Activity registeredActivity;
	private CompassListener listener;
	private CompassValues values = new CompassValues();
	
	private float[] magneticValues = null;
	private float[] accelerometerValues = null;
	
	private float[] inR = new float[16];
	private float[] outR = new float[16];
	private float[] I = new float[16];
	
	public void registerSensors(Activity activity, CompassListener listener, SensorManager sensor) {
		registerSensors(activity, listener, sensor, SensorManager.SENSOR_DELAY_UI);
	}
	
	public void registerSensors(Activity activity, CompassListener listener, SensorManager sensor, int delay) {
		//センサーイベントの登録を行う
		sensor.registerListener(this, sensor.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), delay);
		sensor.registerListener(this, sensor.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), delay);
		//コンパスリスナーの登録を行う
		this.listener = listener;
		//Activityを参照できるようにする
		registeredActivity = activity;
	}
	
	public void unregisterSensors(SensorManager sensor) {
		//センサーイベントの登録を解除する
		sensor.unregisterListener(this);
		//コンパスリスナーを解除する
		listener = null;
		//Activity参照を解除
		registeredActivity = null;
	}
	
	/**
	 * 取得する値のY軸の基準を設定します。
	 * @param base {@link #BASE_HORIZONTAL} あるいは {@link #BASE_VERTICAL} を指定する
	 */
	public void setAxisBase(int base) {
		axisBase = base;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {}

	@Override
	public void onSensorChanged(SensorEvent event) {
		//どちらのセンサーの値が更新されたのか？ 更新された方の値をコピーする
		switch (event.sensor.getType()) {
		case Sensor.TYPE_MAGNETIC_FIELD:
			magneticValues = event.values.clone();
			break;
		case Sensor.TYPE_ACCELEROMETER:
			accelerometerValues = event.values.clone();
			break;
		}
		
		//両センサーの値が存在していればコンパスの値更新を行う
		if (magneticValues != null && accelerometerValues != null && registeredActivity != null) {
			//回転行列を取得
			SensorManager.getRotationMatrix(inR, I, accelerometerValues, magneticValues);
			
			//画面の回転状態を取得
			Display display = registeredActivity.getWindowManager().getDefaultDisplay();
			int rotation = display.getRotation();
			
			//端末の向きに合わせて傾きを計算させる
			SensorManager.remapCoordinateSystem(inR, AXIS_X[axisBase][rotation], AXIS_Y[axisBase][rotation], outR);
			
			//傾きを取得する
			SensorManager.getOrientation(outR, values.orientationValues);
			
			//ラジアンを度に変換する
			values.pushOrientationValues(
					(float)Math.toDegrees(values.orientationValues[0]),
					(float)Math.toDegrees(values.orientationValues[1]),
					(float)Math.toDegrees(values.orientationValues[2]));
			
			//方位角を(0-360)度に変換する
			values.angle = values.orientationValues[0];
			if (values.orientationValues[0] < 0) values.angle += 360;
			values.pushAngle(values.angle);
						
			//リスナーを呼び出して値を渡す
			if (listener != null) {
				listener.onCompassUpdated(values);
			}
		}
	}

}
