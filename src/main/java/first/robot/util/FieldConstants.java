package first.robot.util;

import org.wpilib.fields.Field;
import org.wpilib.fields.Fields;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Translation2d;

/**
 * Field geometry shared by vision, QuestNav and autos.
 *
 * <p>BIOCORE (2027) is revealed on January 9, 2027. Until WPILib ships the 2027 field, this uses
 * the 2026 field so vision and simulation work for off-season practice. After kickoff, change
 * {@link #FIELD_TYPE} (or load the official JSON from the deploy directory with {@code
 * Field.loadFromFile}).
 */
public final class FieldConstants {
  private FieldConstants() {}

  /** The field whose AprilTag layout is used by every vision system on the robot. */
  public static final Fields FIELD_TYPE = Fields.FRC_2026_REBUILT_WELDED;

  /** AprilTag layout and field dimensions. Loaded once; do not mutate. */
  public static final Field FIELD = Field.loadField(FIELD_TYPE);

  public static final double FIELD_LENGTH = FIELD.getFieldLength();
  public static final double FIELD_WIDTH = FIELD.getFieldWidth();

  /** Whether the field is rotationally symmetric (2025/2026) or mirrored across the center line. */
  public static final AllianceFlipUtil.Symmetry SYMMETRY = AllianceFlipUtil.Symmetry.ROTATIONAL;

  /** Returns true if the translation is on the field, with an optional margin in meters. */
  public static boolean isOnField(Translation2d translation, double marginMeters) {
    return translation.getX() >= -marginMeters
        && translation.getX() <= FIELD_LENGTH + marginMeters
        && translation.getY() >= -marginMeters
        && translation.getY() <= FIELD_WIDTH + marginMeters;
  }

  /** Returns true if the pose is on the field, with an optional margin in meters. */
  public static boolean isOnField(Pose2d pose, double marginMeters) {
    return isOnField(pose.getTranslation(), marginMeters);
  }
}
