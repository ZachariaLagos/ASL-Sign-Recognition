package aslframework.model;

/**
 * Represents a single hand landmark point in a 3D Space (akin to Point3D in Java) as detected by Mediapipe
 * Each hand contains 21 total landmarks corresponding to specific joints and keypoints
 * This class is immutable; (x,y and z) coordinates are final and cannot be modified
 */

public class HandLandmark {
  private final double x;
  private final double y;
  private final double z;

  /**
   * Constructs a HandLandmark with the given 3D coordinates
   *
   * @param x the normalized coordinate x-coordinate (0.0 to 1.0 left to right)
   * @param y the normalized coordinate y-coordinate (0.0 to 1.0 top to bottom)
   * @param z the depth coordinate relative to the wrist landmark
   */

  public HandLandmark(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  /**
   * Returns the x coordinate of this landmark.
   *
   * @return the x coordinate
   */
  public double getX() {
    return x;
  }

  /**
   * Returns the y coordinate of this landmark.
   *
   * @return the y coordinate
   */

  public double getY() {
    return y;
  }

  /**
   * Returns the z coordinate of this landmark.
   *
   * @return the z coordinate
   */

  public double getZ() {
    return z;
  }

  /**
   * Returns a formatted string representation of the landmark
   *
   * @return formatted string with x, y, z values
   */

  @Override
  public String toString(){
    return String.format("HandLandmark(x=%.4f, y=%.4f, z=%.4f)", x, y, z);
  }
}
