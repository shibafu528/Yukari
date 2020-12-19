package combu.excompass;

import java.util.LinkedList;

public class FloatSampler {
	private int sampleNum;
	private LinkedList<Float> sampleList = new LinkedList<Float>();
	private float sum;
	private float average;
	
	public FloatSampler(int sampleNum) {
		this.sampleNum = sampleNum;
		for (int i = 0; i < sampleNum; ++i) {
			sampleList.add(0f);
		}
	}
	
	public void push(float f) {
		sampleList.add(f);
		sum += f;
		sum -= sampleList.poll();
		average = sum / sampleNum;
	}
	
	public float getAverage() {
		return average;
	}
}
