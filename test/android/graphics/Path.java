package android.graphics;

/** Test double that records path commands (shadows the android.jar stub on the test classpath). */
public class Path {
    public final StringBuilder log = new StringBuilder();
    public int count;
    public void moveTo(float x, float y) { log.append('M').append(x).append(',').append(y).append(' '); count++; }
    public void lineTo(float x, float y) { log.append('L').append(x).append(',').append(y).append(' '); count++; }
    public void cubicTo(float a, float b, float c, float d, float e, float f) { log.append('C').append(e).append(',').append(f).append(' '); count++; }
    public void close() { log.append("Z "); count++; }
}
