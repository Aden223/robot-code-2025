package first.robot.subsystems.objectdetection;

import static first.robot.subsystems.objectdetection.ObjectDetectionConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import first.robot.subsystems.objectdetection.ObjectDetectionIO.Detection;
import org.junit.jupiter.api.Test;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.geometry.Translation3d;

class GamePieceTrackerTest {
  private static final double EPSILON = 1e-6;

  /** Computes the yaw/pitch PhotonVision would report for a point, using the camera transform. */
  private static Detection detectionOf(Pose2d robotPose, Translation2d piece) {
    Pose3d cameraPose = new Pose3d(robotPose).transformBy(ROBOT_TO_CAMERA);
    Translation3d inCamera =
        new Translation3d(piece.getX(), piece.getY(), GAME_PIECE_CENTER_HEIGHT)
            .minus(cameraPose.getTranslation())
            .rotateBy(cameraPose.getRotation().inverse());
    double yawDegrees = -Math.toDegrees(Math.atan2(inCamera.getY(), inCamera.getX()));
    double pitchDegrees =
        Math.toDegrees(
            Math.atan2(inCamera.getZ(), Math.hypot(inCamera.getX(), inCamera.getY())));
    return new Detection(0.0, yawDegrees, pitchDegrees, 1.0, 0, 0.9);
  }

  @Test
  void projectsPieceStraightAhead() {
    Pose2d robot = new Pose2d(3.0, 4.0, Rotation2d.ZERO);
    Translation2d piece = new Translation2d(4.5, 4.0);
    var projected = GamePieceTracker.projectToFloor(robot, detectionOf(robot, piece));
    assertTrue(projected.isPresent());
    assertEquals(piece.getX(), projected.get().getX(), EPSILON);
    assertEquals(piece.getY(), projected.get().getY(), EPSILON);
  }

  @Test
  void projectsPieceToTheSideWithRotatedRobot() {
    Pose2d robot = new Pose2d(5.0, 2.0, Rotation2d.fromDegrees(135.0));
    Translation2d piece = new Translation2d(4.2, 3.1);
    var projected = GamePieceTracker.projectToFloor(robot, detectionOf(robot, piece));
    assertTrue(projected.isPresent());
    assertEquals(piece.getX(), projected.get().getX(), EPSILON);
    assertEquals(piece.getY(), projected.get().getY(), EPSILON);
  }

  @Test
  void rejectsRaysAboveTheHorizon() {
    Pose2d robot = new Pose2d(3.0, 4.0, Rotation2d.ZERO);
    var aboveHorizon = new Detection(0.0, 0.0, 30.0, 1.0, 0, 0.9);
    assertTrue(GamePieceTracker.projectToFloor(robot, aboveHorizon).isEmpty());
  }
}
