package first.robot.subsystems.drive;

import static org.wpilib.units.Units.MetersPerSecond;

import first.robot.generated.TunerConstants;
import org.wpilib.math.geometry.Translation2d;

/** Drive constants that TunerConstants doesn't provide. */
public final class DriveConstants {
  private DriveConstants() {}

  /**
   * Odometry sample rate. Both the CANivore and the Systemcore CAN ports are CAN FD, so all drive
   * signals are sampled synchronously at 250 Hz by {@link PhoenixOdometryThread}.
   */
  public static final double ODOMETRY_FREQUENCY = 250.0;

  public static final Translation2d[] MODULE_TRANSLATIONS =
      new Translation2d[] {
        new Translation2d(TunerConstants.FrontLeft.LocationX, TunerConstants.FrontLeft.LocationY),
        new Translation2d(TunerConstants.FrontRight.LocationX, TunerConstants.FrontRight.LocationY),
        new Translation2d(TunerConstants.BackLeft.LocationX, TunerConstants.BackLeft.LocationY),
        new Translation2d(TunerConstants.BackRight.LocationX, TunerConstants.BackRight.LocationY)
      };

  public static final double DRIVE_BASE_RADIUS =
      Math.max(
          Math.max(MODULE_TRANSLATIONS[0].getNorm(), MODULE_TRANSLATIONS[1].getNorm()),
          Math.max(MODULE_TRANSLATIONS[2].getNorm(), MODULE_TRANSLATIONS[3].getNorm()));

  public static final double MAX_LINEAR_SPEED = TunerConstants.kSpeedAt12Volts.in(MetersPerSecond);
  public static final double MAX_ANGULAR_SPEED = MAX_LINEAR_SPEED / DRIVE_BASE_RADIUS;

  /**
   * Robot mass with bumpers and battery, in kg. Weigh the robot and update; used by Choreo (set the
   * same value in the Choreo GUI robot config) and the driver-assist acceleration limits.
   */
  public static final double ROBOT_MASS_KG = 60.0;

  /** Wheel coefficient of friction (1.2 for typical treaded swerve wheels on carpet). */
  public static final double WHEEL_COF = 1.2;
}
