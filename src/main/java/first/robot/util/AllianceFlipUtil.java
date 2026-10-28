package first.robot.util;

import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchState;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Translation2d;

/**
 * Flips blue-alliance field coordinates to the red alliance. All field positions in this project
 * are written for the blue alliance (origin at the blue driver station corner).
 */
public final class AllianceFlipUtil {
  private AllianceFlipUtil() {}

  public enum Symmetry {
    /** Red side is the blue side rotated 180 degrees around the field center. */
    ROTATIONAL,
    /** Red side is the blue side mirrored across the center line. */
    MIRRORED
  }

  public static double applyX(double x) {
    return shouldFlip() ? FieldConstants.FIELD_LENGTH - x : x;
  }

  public static double applyY(double y) {
    return shouldFlip() && FieldConstants.SYMMETRY == Symmetry.ROTATIONAL
        ? FieldConstants.FIELD_WIDTH - y
        : y;
  }

  public static Translation2d apply(Translation2d translation) {
    return shouldFlip()
        ? new Translation2d(applyX(translation.getX()), applyY(translation.getY()))
        : translation;
  }

  public static Rotation2d apply(Rotation2d rotation) {
    if (!shouldFlip()) {
      return rotation;
    }
    return FieldConstants.SYMMETRY == Symmetry.ROTATIONAL
        ? rotation.rotateBy(Rotation2d.PI)
        : new Rotation2d(-rotation.getCos(), rotation.getSin());
  }

  public static Pose2d apply(Pose2d pose) {
    return shouldFlip()
        ? new Pose2d(apply(pose.getTranslation()), apply(pose.getRotation()))
        : pose;
  }

  public static boolean shouldFlip() {
    return MatchState.getAlliance().orElse(Alliance.BLUE) == Alliance.RED;
  }
}
