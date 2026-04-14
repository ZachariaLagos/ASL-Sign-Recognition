package aslframework.persistence;

public class AttemptRecord {
  private final String gestureId;
  private final double accuracy;
  private final long timestamp;
  private final boolean passed;

  public AttemptRecord(String gestureId, double accuracy, long timestamp, boolean passed) {
    this.gestureId = gestureId;
    this.accuracy = accuracy;
    this.timestamp = timestamp;
    this.passed = passed;
  }

  public String getGestureId() { return gestureId; }
  public double getAccuracy() { return accuracy; }
  public long getTimestamp() { return timestamp; }
  public boolean isPassed() { return passed; }
}