package first.robot.commands;

import first.robot.subsystems.drive.Drive;
import first.robot.util.LoggedTunableNumber;
import java.util.function.Supplier;
import org.littletonrobotics.junction.Logger;
import org.wpilib.command2.Command;
import org.wpilib.math.controller.ProfiledPIDController;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.trajectory.TrapezoidProfile;
import org.wpilib.math.util.MathUtil;

/**
 * Drives straight to a field pose using the fused (QuestNav + vision + odometry) pose. Used for
 * auto-align to scoring locations and for driving to game pieces.
 *
 * <p>Translation is a motion-profiled controller on the distance to the goal (so the robot drives
 * in a straight line and decelerates smoothly), and rotation is a separate profiled controller.
 * The profile starts from the robot's current velocity, so engaging it while driving is smooth.
 */
public class DriveToPose extends Command {
  private static final LoggedTunableNumber driveKp =
      new LoggedTunableNumber("DriveToPose/DriveKp", 3.0);
  private static final LoggedTunableNumber driveMaxVelocity =
      new LoggedTunableNumber("DriveToPose/DriveMaxVelocity", 3.5);
  private static final LoggedTunableNumber driveMaxAcceleration =
      new LoggedTunableNumber("DriveToPose/DriveMaxAcceleration", 4.0);
  private static final LoggedTunableNumber thetaKp =
      new LoggedTunableNumber("DriveToPose/ThetaKp", 4.0);
  private static final LoggedTunableNumber thetaMaxVelocity =
      new LoggedTunableNumber("DriveToPose/ThetaMaxVelocity", 6.0);
  private static final LoggedTunableNumber thetaMaxAcceleration =
      new LoggedTunableNumber("DriveToPose/ThetaMaxAcceleration", 12.0);
  private static final LoggedTunableNumber driveTolerance =
      new LoggedTunableNumber("DriveToPose/DriveToleranceMeters", 0.02);
  private static final LoggedTunableNumber thetaTolerance =
      new LoggedTunableNumber("DriveToPose/ThetaToleranceRadians", Math.toRadians(1.5));

  private final Drive drive;
  private final Supplier<Pose2d> goalSupplier;

  private final ProfiledPIDController driveController =
      new ProfiledPIDController(0.0, 0.0, 0.0, new TrapezoidProfile.Constraints(0.0, 0.0));
  private final ProfiledPIDController thetaController =
      new ProfiledPIDController(0.0, 0.0, 0.0, new TrapezoidProfile.Constraints(0.0, 0.0));

  private Translation2d lastSetpointTranslation = Translation2d.ZERO;
  private double driveErrorAbs = Double.POSITIVE_INFINITY;
  private double thetaErrorAbs = Double.POSITIVE_INFINITY;

  /**
   * @param drive The drive subsystem
   * @param goalSupplier Field pose to drive to (blue-alliance coordinates already flipped if needed).
   *     Re-evaluated every loop, so it can follow a moving target.
   */
  public DriveToPose(Drive drive, Supplier<Pose2d> goalSupplier) {
    this.drive = drive;
    this.goalSupplier = goalSupplier;
    thetaController.enableContinuousInput(-Math.PI, Math.PI);
    addRequirements(drive);
  }

  @Override
  public void initialize() {
    updateConstants();
    Pose2d currentPose = drive.getPose();
    Pose2d goal = goalSupplier.get();
    ChassisVelocities fieldVelocity = drive.getFieldVelocities();
    Translation2d toGoal = goal.getTranslation().minus(currentPose.getTranslation());
    double distance = toGoal.getNorm();

    // Velocity toward the goal, so the profile starts from the robot's current motion
    double velocityTowardGoal =
        distance > 1e-6
            ? -(fieldVelocity.vx * toGoal.getX() + fieldVelocity.vy * toGoal.getY()) / distance
            : 0.0;
    driveController.reset(distance, Math.min(0.0, velocityTowardGoal));
    thetaController.reset(currentPose.getRotation().getRadians(), fieldVelocity.omega);
    lastSetpointTranslation = currentPose.getTranslation();
  }

  private void updateConstants() {
    driveController.setP(driveKp.get());
    driveController.setConstraints(
        new TrapezoidProfile.Constraints(driveMaxVelocity.get(), driveMaxAcceleration.get()));
    driveController.setTolerance(driveTolerance.get());
    thetaController.setP(thetaKp.get());
    thetaController.setConstraints(
        new TrapezoidProfile.Constraints(thetaMaxVelocity.get(), thetaMaxAcceleration.get()));
    thetaController.setTolerance(thetaTolerance.get());
  }

  @Override
  public void execute() {
    Pose2d currentPose = drive.getPose();
    Pose2d goal = goalSupplier.get();

    // Distance controller: drive the distance to the goal to zero along the straight line
    double distance = currentPose.getTranslation().getDistance(goal.getTranslation());
    driveErrorAbs = distance;
    double driveVelocityScalar =
        driveController.getSetpoint().velocity + driveController.calculate(distance, 0.0);
    if (distance < driveController.getPositionTolerance()) {
      driveVelocityScalar = 0.0;
    }

    Translation2d direction =
        distance > 1e-6
            ? currentPose.getTranslation().minus(goal.getTranslation()).div(distance)
            : Translation2d.ZERO;
    Translation2d driveVelocity = direction.times(driveVelocityScalar);
    lastSetpointTranslation =
        goal.getTranslation().plus(direction.times(driveController.getSetpoint().position));

    // Rotation controller
    double thetaVelocity =
        thetaController.getSetpoint().velocity
            + thetaController.calculate(
                currentPose.getRotation().getRadians(), goal.getRotation().getRadians());
    thetaErrorAbs =
        Math.abs(MathUtil.angleModulus(currentPose.getRotation().minus(goal.getRotation()).getRadians()));
    if (thetaErrorAbs < thetaController.getPositionTolerance()) {
      thetaVelocity = 0.0;
    }

    drive.runVelocity(
        new ChassisVelocities(driveVelocity.getX(), driveVelocity.getY(), thetaVelocity)
            .toRobotRelative(currentPose.getRotation()));

    Logger.recordOutput("DriveToPose/Goal", goal);
    Logger.recordOutput(
        "DriveToPose/Setpoint",
        new Pose2d(lastSetpointTranslation, new Rotation2d(thetaController.getSetpoint().position)));
    Logger.recordOutput("DriveToPose/DistanceError", driveErrorAbs);
    Logger.recordOutput("DriveToPose/ThetaError", thetaErrorAbs);
  }

  /** Returns true once the robot is within tolerance of the goal. */
  public boolean atGoal() {
    return driveErrorAbs < driveController.getPositionTolerance()
        && thetaErrorAbs < thetaController.getPositionTolerance();
  }

  @Override
  public void end(boolean interrupted) {
    drive.stop();
    driveErrorAbs = Double.POSITIVE_INFINITY;
    thetaErrorAbs = Double.POSITIVE_INFINITY;
  }
}
