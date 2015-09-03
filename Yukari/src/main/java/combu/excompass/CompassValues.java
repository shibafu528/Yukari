package combu.excompass;

public class CompassValues {
	private static final int SAMPLE_NUM = 5;
	
	private FloatSampler sampleX = new FloatSampler(SAMPLE_NUM);
	private FloatSampler sampleY = new FloatSampler(SAMPLE_NUM);
	private FloatSampler sampleZ = new FloatSampler(SAMPLE_NUM);
	private FloatSampler sampleAngle = new FloatSampler(SAMPLE_NUM);
	
	public float[] orientationValues = new float[3];
	public float angle;
	
	private float[] ov_avg = new float[3];
	
	public void pushOrientationValues(float x, float y, float z) {
		sampleX.push(x);
		sampleY.push(y);
		sampleZ.push(z);
		
		orientationValues[0] = x;
		orientationValues[1] = y;
		orientationValues[2] = z;
	}
	
	public void pushAngle(float angle) {
		sampleAngle.push(angle);
		
		this.angle = angle;
	}
	
	public float[] getOrientationValuesAverage() {
		ov_avg[0] = sampleX.getAverage();
		ov_avg[1] = sampleY.getAverage();
		ov_avg[2] = sampleZ.getAverage();
		return ov_avg;
	}
	
	public float getAngleAverage() {
		return sampleAngle.getAverage();
	}
}
