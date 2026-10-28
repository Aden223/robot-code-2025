package first.robot.subsystems.vision;

import org.wpilib.math.geometry.Rotation3d;
import org.wpilib.math.geometry.Transform3d;
import org.wpilib.math.util.Units;

/** PhotonVision AprilTag camera constants (Orange Pi 5 coprocessor). */
public final class VisionConstants {
  private VisionConstants() {}

  /** One PhotonVision AprilTag camera. */
  public record CameraConfig(String name, Transform3d robotToCamera, double stdDevFactor) {}

  /**
   * AprilTag cameras. Names must match the camera names in the PhotonVision web UI
   * (http://photonvision.local:5800). Transforms are robot center (on the floor) to the camera
   * lens: X forward, Y left, Z up (meters); pitch is negative when the camera tilts UP.
   *
   * <p>Measure from CAD (Onshape) for best results. Add or remove entries for 2-4 cameras; nothing
   * else needs to change. stdDevFactor lets you trust a camera less (e.g. 1.5) if it is noisier.
   */
  public static final CameraConfig[] CAMERAS =
      new CameraConfig[] {
        new CameraConfig(
            "front_left",
            new Transform3d(
                Units.inchesToMeters(10.0),
                Units.inchesToMeters(10.0),
                Units.inchesToMeters(8.0),
                new Rotation3d(0.0, Units.degreesToRadians(-20.0), Units.degreesToRadians(30.0))),
            1.0),
        new CameraConfig(
            "front_right",
            new Transform3d(
                Units.inchesToMeters(10.0),
                Units.inchesToMeters(-10.0),
                Units.inchesToMeters(8.0),
                new Rotation3d(0.0, Units.degreesToRadians(-20.0), Units.degreesToRadians(-30.0))),
            1.0),
      };

  // Basic filtering thresholds
  /** Single-tag solutions with ambiguity above this are rejected. */
  public static final double MAX_AMBIGUITY = 0.2;

  /** Robot poses further than this from the floor (meters) are rejected. */
  public static final double MAX_Z_ERROR = 0.3;

  /** Single-tag observations further than this (meters) are rejected; multi-tag are kept. */
  public static final double MAX_SINGLE_TAG_DISTANCE = 4.0;

  /** Any observation with an average tag distance further than this (meters) is rejected. */
  public static final double MAX_TAG_DISTANCE = 7.0;

  // Standard deviation baselines, for 1 meter distance and 1 tag
  // (Adjusted automatically based on distance and # of tags)
  public static final double LINEAR_STD_DEV_BASELINE = 0.02; // Meters
  public static final double ANGULAR_STD_DEV_BASELINE = 0.06; // Radians

  /** Extra distrust for single-tag solutions (they flip and have poor heading). */
  public static final double SINGLE_TAG_STD_DEV_FACTOR = 3.0;
}
