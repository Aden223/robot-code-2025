package first.robot.subsystems.objectdetection;

import static first.robot.subsystems.objectdetection.ObjectDetectionConstants.*;

import first.robot.subsystems.objectdetection.ObjectDetectionIO.Detection;
import first.robot.util.FieldConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleFunction;
import org.littletonrobotics.junction.Logger;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.geometry.Rotation3d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.geometry.Translation3d;
import org.wpilib.system.Timer;
import org.wpilib.util.Alert;

/**
 * Turns PhotonVision object detections into field positions and tracks them over time.
 *
 * <p>Each detection is a ray from the camera. Using the fused robot pose at the frame's timestamp
 * (latency compensated, so this works while driving fast), the ray is intersected with the plane at
 * the game piece's height to get the piece's field position. Repeated detections of the same piece
 * are merged and smoothed; pieces that disappear are forgotten after a timeout.
 */
public class GamePieceTracker extends SubsystemBase {
  private final ObjectDetectionIO io;
  private final ObjectDetectionIOInputsAutoLogged inputs = new ObjectDetectionIOInputsAutoLogged();
  private final DoubleFunction<Pose2d> robotPoseAtTimestamp;
  private final List<TrackedPiece> pieces = new ArrayList<>();
  private final Alert disconnectedAlert =
      new Alert(
          "Vision", "ObjectCameraDisconnected", "Object detection camera disconnected.",
          Alert.Level.MEDIUM);

  private static final class TrackedPiece {
    Translation2d position;
    double lastSeenTimestamp;
    int timesSeen;

    TrackedPiece(Translation2d position, double timestamp) {
      this.position = position;
      this.lastSeenTimestamp = timestamp;
      this.timesSeen = 1;
    }
  }

  /**
   * @param io Object detection camera
   * @param robotPoseAtTimestamp Returns the fused robot pose at a timestamp (latency compensation)
   */
  public GamePieceTracker(ObjectDetectionIO io, DoubleFunction<Pose2d> robotPoseAtTimestamp) {
    this.io = io;
    this.robotPoseAtTimestamp = robotPoseAtTimestamp;
  }

  @Override
  public void periodic() {
    io.updateInputs(inputs);
    Logger.processInputs("ObjectDetection", inputs);
    disconnectedAlert.set(!inputs.connected);

    for (Detection detection : inputs.detections) {
      if (detection.confidence() < MIN_CONFIDENCE || !isGamePieceClass(detection.classId())) {
        continue;
      }
      Pose2d robotPose = robotPoseAtTimestamp.apply(detection.timestamp());
      projectToFloor(robotPose, detection)
          .filter(position -> position.getDistance(robotPose.getTranslation()) < MAX_DETECTION_DISTANCE)
          .filter(position -> FieldConstants.isOnField(position, 0.0))
          .ifPresent(position -> addObservation(position, detection.timestamp()));
    }

    // Forget pieces that haven't been seen recently (picked up, or knocked away)
    double now = Timer.getTimestamp();
    pieces.removeIf(piece -> now - piece.lastSeenTimestamp > PIECE_TIMEOUT_SECONDS);

    Pose3d[] logged = new Pose3d[pieces.size()];
    for (int i = 0; i < logged.length; i++) {
      Translation2d p = pieces.get(i).position;
      logged[i] = new Pose3d(p.getX(), p.getY(), GAME_PIECE_CENTER_HEIGHT, Rotation3d.ZERO);
    }
    Logger.recordOutput("ObjectDetection/TrackedPieces", logged);
  }

  /**
   * Intersects the camera ray through a detection with the horizontal plane at the game piece's
   * center height. Returns empty if the ray points above the horizon.
   */
  static Optional<Translation2d> projectToFloor(Pose2d robotPose, Detection detection) {
    Pose3d cameraPose = new Pose3d(robotPose).transformBy(ROBOT_TO_CAMERA);

    // Unit ray in the camera frame (X forward, Y left, Z up). PhotonVision yaw is positive right.
    double pitch = Math.toRadians(detection.pitchDegrees());
    double yaw = -Math.toRadians(detection.yawDegrees());
    Translation3d rayCamera =
        new Translation3d(
            Math.cos(pitch) * Math.cos(yaw), Math.cos(pitch) * Math.sin(yaw), Math.sin(pitch));
    Translation3d rayField = rayCamera.rotateBy(cameraPose.getRotation());

    double heightAbovePiece = cameraPose.getZ() - GAME_PIECE_CENTER_HEIGHT;
    if (rayField.getZ() >= -1e-3 || heightAbovePiece <= 0.0) {
      return Optional.empty();
    }
    double distanceAlongRay = heightAbovePiece / -rayField.getZ();
    return Optional.of(
        new Translation2d(
            cameraPose.getX() + rayField.getX() * distanceAlongRay,
            cameraPose.getY() + rayField.getY() * distanceAlongRay));
  }

  private void addObservation(Translation2d position, double timestamp) {
    TrackedPiece closest = null;
    double closestDistance = MERGE_DISTANCE;
    for (TrackedPiece piece : pieces) {
      double distance = piece.position.getDistance(position);
      if (distance < closestDistance) {
        closest = piece;
        closestDistance = distance;
      }
    }
    if (closest == null) {
      pieces.add(new TrackedPiece(position, timestamp));
    } else {
      closest.position = closest.position.interpolate(position, POSITION_SMOOTHING);
      closest.lastSeenTimestamp = Math.max(closest.lastSeenTimestamp, timestamp);
      closest.timesSeen++;
    }
  }

  private static boolean isGamePieceClass(int classId) {
    if (GAME_PIECE_CLASS_IDS.length == 0) {
      return true;
    }
    for (int id : GAME_PIECE_CLASS_IDS) {
      if (id == classId) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the tracked game piece closest to a field position, ignoring one-frame flickers.
   *
   * @param from Field position to measure from (usually the robot or its intake)
   */
  public Optional<Translation2d> getClosestPiece(Translation2d from) {
    Translation2d best = null;
    double bestDistance = Double.POSITIVE_INFINITY;
    for (TrackedPiece piece : pieces) {
      if (piece.timesSeen < 2) {
        continue;
      }
      double distance = piece.position.getDistance(from);
      if (distance < bestDistance) {
        best = piece.position;
        bestDistance = distance;
      }
    }
    return Optional.ofNullable(best);
  }

  /** Returns true if any game piece is currently tracked. */
  public boolean hasPiece() {
    return !pieces.isEmpty();
  }
}
