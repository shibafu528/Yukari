package combu.combudashi;

import javax.microedition.khronos.opengles.GL10;

public abstract class GLDrawable {
	
	private boolean enableTranslate = false;
	private float translateX, translateY, translateZ;
	
	private boolean enableRotate = false;
	private float rotateAngle, rotateX, rotateY, rotateZ;
	
	private boolean enableScale = false;
	private float scaleX, scaleY, scaleZ;
	
	public void setTranslate(float x, float y, float z) {
		setEnableTranslate(true);
		this.translateX = x;
		this.translateY = y;
		this.translateZ = z;
	}
	
	public void setRotate(float angle, float x, float y, float z) {
		setEnableRotate(true);
		this.rotateAngle = angle;
		this.rotateX = x;
		this.rotateY = y;
		this.rotateZ = z;
	}
	
	public void setScale(float x, float y, float z) {
		setEnableScale(true);
		this.scaleX = x;
		this.scaleY = y;
		this.scaleZ = z;
	}
	
	public void setEnableTranslate(boolean enableTranslate) {
		this.enableTranslate = enableTranslate;
	}

	public void setEnableRotate(boolean enableRotate) {
		this.enableRotate = enableRotate;
	}

	public void setEnableScale(boolean enableScale) {
		this.enableScale = enableScale;
	}

	public void drawObject(GL10 gl) {
		gl.glPushMatrix();
		{
			if (enableTranslate) gl.glTranslatef(translateX, translateY, translateZ);
			if (enableRotate) gl.glRotatef(rotateAngle, rotateX, rotateY, rotateZ);
			if (enableScale) gl.glScalef(scaleX, scaleY, scaleZ);
			drawObjectImpl(gl);
		}
		gl.glPopMatrix();
	}
	
	protected abstract void drawObjectImpl(GL10 gl);
}
