package first.robot.subsystems.objectdetection;

import org.wpilib.math.geometry.Rotation3d;
import org.wpilib.math.geometry.Transform3d;
import org.wpilib.math.util.Units;

/**
 * Game piece detection with a PhotonVision object detection pipeline (RKNN model on the Orange Pi 5
 * NPU). Game pieces for BIOCORE are unknown until kickoff, so update these after January 9.
 */
public final class ObjectDetectionConstants {
  private ObjectDetectionConstants() {}

  /** PhotonVision camera running the object detection pipeline. */
  public static final String CAMERA_NAME = "intake_cam";

  /** Robot center (floor) to camera lens. Pitch is negative when the camera tilts UP. */
  public static final Transform3d ROBOT_TO_CAMERA =
      new Transform3d(
          Units.inchesToMeters(12.0),
          Units.inchesToMeters(0.0),
          Units.inchesToMeters(18.0),
          new Rotation3d(0.0, Units.degreesToRadians(25.0), 0.0));

  /** Class IDs (from the model's labels file) that count as game pieces. Empty = every class. */
  public static final int[] GAME_PIECE_CLASS_IDS = new int[] {};

  /** Height of the game piece's center above the floor (meters). */
  public static final double GAME_PIECE_CENTER_HEIGHT = Units.inchesToMeters(3.0);

  /** Ignore detections below this confidence. */
  public static final double MIN_CONFIDENCE = 0.5;

  /** Ignore detections projected further than this from the robot (meters). */
  public static final double MAX_DETECTION_DISTANCE = 5.0;

  /** Detections closer than this to a tracked piece are treated as the same piece (meters). */
  public static final double MERGE_DISTANCE = 0.3;

  /** Forget pieces that haven't been seen for this long (seconds). */
  public static final double PIECE_TIMEOUT_SECONDS = 1.0;

  /** Smoothing for tracked piece positions (0-1, higher follows new detections faster). */
  public static final double POSITION_SMOOTHING = 0.4;
}
