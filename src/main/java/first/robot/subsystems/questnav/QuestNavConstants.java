package first.robot.subsystems.questnav;

import org.wpilib.math.geometry.Rotation3d;
import org.wpilib.math.geometry.Transform3d;
import org.wpilib.math.util.Units;

/** Constants for the Meta Quest 3S running QuestNav. */
public final class QuestNavConstants {
  private QuestNavConstants() {}

  /**
   * Robot center (on the floor) to the Quest 3S "third-eye" pose origin, which is centered between
   * the two lenses on the lens plane. X forward, Y left, Z up, in meters.
   *
   * <p>MEASURE THIS ON THE REAL ROBOT. Every millimeter of error here shows up as pose error when
   * the robot rotates. The Quest 3S has no lens tilt, so a vertically mounted headset facing forward
   * has zero rotation. If it faces backward use yaw = Math.PI, sideways = +/- Math.PI / 2.
   */
  public static final Transform3d ROBOT_TO_QUEST =
      new Transform3d(
          Units.inchesToMeters(0.0), // X
          Units.inchesToMeters(0.0), // Y
          Units.inchesToMeters(10.0), // Z
          new Rotation3d(0.0, 0.0, 0.0));

  /**
   * How much the pose estimator trusts QuestNav (x meters, y meters, theta radians). QuestNav VIO
   * is very smooth and accurate over short periods, so these are small. Tune with log replay.
   */
  public static final double LINEAR_STD_DEV = 0.02;

  public static final double ANGULAR_STD_DEV = 0.035;

  /** Frames from right after a pose reset may still be in the old frame; ignore them. */
  public static final double RESET_SETTLE_SECONDS = 0.25;

  /**
   * Reject Quest frames whose timestamp is further than this from "now". Protects the estimator if
   * the headset's NetworkTables time sync is wrong (for example right after it connects).
   */
  public static final double MAX_TIMESTAMP_ERROR_SECONDS = 0.5;

  /** Quest-derived poses this far outside the field are rejected. */
  public static final double FIELD_MARGIN_METERS = 0.5;

  /** Quest-derived poses with |Z| (robot height) larger than this indicate a bad mount transform. */
  public static final double MAX_Z_ERROR_METERS = 0.25;

  // ----- Automatic drift correction from PhotonVision AprilTags -----

  /** Only correct drift with tag observations at least this good (std dev, meters). */
  public static final double DRIFT_MAX_TAG_STD_DEV = 0.05;

  /** Exponential moving average weight for Quest vs. AprilTag error (0-1, higher = faster). */
  public static final double DRIFT_EMA_ALPHA = 0.1;

  /** Re-seed the Quest when the averaged Quest vs. AprilTag error exceeds this (meters). */
  public static final double DRIFT_RESEED_THRESHOLD_METERS = 0.12;

  /** Minimum number of good tag observations before trusting the drift estimate. */
  public static final int DRIFT_MIN_OBSERVATIONS = 15;

  /** Don't re-seed while moving faster than this (m/s); fast motion makes comparisons noisy. */
  public static final double DRIFT_MAX_ROBOT_SPEED = 1.0;

  /** Battery percent at which to warn the drive team. */
  public static final int LOW_BATTERY_PERCENT = 25;
}
