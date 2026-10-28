package first.robot.subsystems.objectdetection;

import org.littletonrobotics.junction.AutoLog;

public interface ObjectDetectionIO {
  @AutoLog
  public static class ObjectDetectionIOInputs {
    public boolean connected = false;
    public Detection[] detections = new Detection[0];
  }

  /**
   * One detected object in one camera frame.
   *
   * @param timestamp Frame timestamp in seconds
   * @param yawDegrees Horizontal angle to the object, positive to the RIGHT (PhotonVision)
   * @param pitchDegrees Vertical angle to the object, positive UP
   * @param area Percent of the image the bounding box covers
   * @param classId Model class ID
   * @param confidence Model confidence 0-1
   */
  public static record Detection(
      double timestamp,
      double yawDegrees,
      double pitchDegrees,
      double area,
      int classId,
      double confidence) {}

  public default void updateInputs(ObjectDetectionIOInputs inputs) {}
}
