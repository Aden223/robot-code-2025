// Copyright (c) 2021-2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package first.robot.subsystems.drive;

import static org.wpilib.units.Units.Volts;

import choreo.trajectory.SwerveSample;
import first.robot.Constants;
import first.robot.Constants.Mode;
import first.robot.generated.TunerConstants;
import first.robot.util.LoggedTunableNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.littletonrobotics.junction.AutoLogOutput;
import org.littletonrobotics.junction.Logger;
import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.command2.sysid.SysIdRoutine;
import org.wpilib.driverstation.RobotState;
import org.wpilib.math.controller.PIDController;
import org.wpilib.math.estimator.SwerveDrivePoseEstimator;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.geometry.Twist2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.kinematics.SwerveDriveKinematics;
import org.wpilib.math.kinematics.SwerveDriveOdometry;
import org.wpilib.math.kinematics.SwerveModulePosition;
import org.wpilib.math.kinematics.SwerveModuleVelocity;
import org.wpilib.math.linalg.Matrix;
import org.wpilib.math.linalg.VecBuilder;
import org.wpilib.math.numbers.N1;
import org.wpilib.math.numbers.N3;
import org.wpilib.util.Alert;

/**
 * Swerve drive with a high-frequency (250 Hz) odometry thread and a pose estimator that fuses wheel
 * odometry, the gyro, QuestNav and PhotonVision AprilTags.
 *
 * <p>This is the single source of truth for "where is the robot". Everything else (autos,
 * auto-align, game piece tracking) reads {@link #getPose()}.
 */
public class Drive extends SubsystemBase {
  static final Lock odometryLock = new ReentrantLock();

  // Odometry trust. Wheel odometry is excellent short-term; QuestNav and vision correct drift.
  private static final Matrix<N3, N1> ODOMETRY_STD_DEVS = VecBuilder.fill(0.02, 0.02, 0.005);

  // Choreo path following feedback gains (feedforward comes from the trajectory)
  private static final LoggedTunableNumber pathTranslationKp =
      new LoggedTunableNumber("Drive/PathFollowing/TranslationKp", 8.0);
  private static final LoggedTunableNumber pathRotationKp =
      new LoggedTunableNumber("Drive/PathFollowing/RotationKp", 6.0);

  private final GyroIO gyroIO;
  private final GyroIOInputsAutoLogged gyroInputs = new GyroIOInputsAutoLogged();
  private final Module[] modules = new Module[4]; // FL, FR, BL, BR
  private final SysIdRoutine sysId;
  private final Alert gyroDisconnectedAlert =
      new Alert(
          "Drive", "GyroDisconnected", "Disconnected gyro, using kinematics as fallback.",
          Alert.Level.HIGH);

  private final SwerveDriveKinematics kinematics =
      new SwerveDriveKinematics(DriveConstants.MODULE_TRANSLATIONS);
  private Rotation2d rawGyroRotation = Rotation2d.ZERO;
  private final SwerveModulePosition[] lastModulePositions = // For delta tracking
      new SwerveModulePosition[] {
        new SwerveModulePosition(),
        new SwerveModulePosition(),
        new SwerveModulePosition(),
        new SwerveModulePosition()
      };
  private final SwerveDrivePoseEstimator poseEstimator =
      new SwerveDrivePoseEstimator(
          kinematics,
          rawGyroRotation,
          lastModulePositions,
          Pose2d.ZERO,
          ODOMETRY_STD_DEVS,
          VecBuilder.fill(0.9, 0.9, 0.9));

  /**
   * Wheel + gyro odometry only, never corrected by QuestNav or vision. Logged for comparison, and
   * used as the "true" robot pose by the simulated Quest and cameras.
   */
  private final SwerveDriveOdometry wheelOdometry =
      new SwerveDriveOdometry(kinematics, rawGyroRotation, lastModulePositions, Pose2d.ZERO);

  private final PIDController pathXController = new PIDController(8.0, 0.0, 0.0);
  private final PIDController pathYController = new PIDController(8.0, 0.0, 0.0);
  private final PIDController pathThetaController = new PIDController(6.0, 0.0, 0.0);

  /** Called whenever the pose is hard-reset (auto start, driver reset), e.g. to re-seed QuestNav. */
  private final List<Consumer<Pose2d>> poseResetListeners = new ArrayList<>();

  private static final SwerveModuleVelocity[] EMPTY_STATES = new SwerveModuleVelocity[] {};

  public Drive(
      GyroIO gyroIO,
      ModuleIO flModuleIO,
      ModuleIO frModuleIO,
      ModuleIO blModuleIO,
      ModuleIO brModuleIO) {
    this.gyroIO = gyroIO;
    modules[0] = new Module(flModuleIO, 0, TunerConstants.FrontLeft);
    modules[1] = new Module(frModuleIO, 1, TunerConstants.FrontRight);
    modules[2] = new Module(blModuleIO, 2, TunerConstants.BackLeft);
    modules[3] = new Module(brModuleIO, 3, TunerConstants.BackRight);

    pathThetaController.enableContinuousInput(-Math.PI, Math.PI);

    // Start odometry thread
    PhoenixOdometryThread.getInstance().start();

    // Configure SysId
    sysId =
        new SysIdRoutine(
            new SysIdRoutine.Config(
                null,
                null,
                null,
                (state) -> Logger.recordOutput("Drive/SysIdState", state.toString())),
            new SysIdRoutine.Mechanism(
                (voltage) -> runCharacterization(voltage.in(Volts)), null, this));
  }

  @Override
  public void periodic() {
    odometryLock.lock(); // Prevents odometry updates while reading data
    try {
      gyroIO.updateInputs(gyroInputs);
      Logger.processInputs("Drive/Gyro", gyroInputs);
      for (var module : modules) {
        module.periodic();
      }
    } finally {
      odometryLock.unlock();
    }

    if (RobotState.isDisabled()) {
      // Stop moving when disabled
      for (var module : modules) {
        module.stop();
      }

      // Log empty setpoint states when disabled
      Logger.recordOutput("SwerveStates/Setpoints", EMPTY_STATES);
      Logger.recordOutput("SwerveStates/SetpointsOptimized", EMPTY_STATES);
    }

    // Update odometry
    double[] sampleTimestamps =
        modules[0].getOdometryTimestamps(); // All signals are sampled together
    int sampleCount = sampleTimestamps.length;
    for (int i = 0; i < sampleCount; i++) {
      // Read wheel positions and deltas from each module
      SwerveModulePosition[] modulePositions = new SwerveModulePosition[4];
      SwerveModulePosition[] moduleDeltas = new SwerveModulePosition[4];
      for (int moduleIndex = 0; moduleIndex < 4; moduleIndex++) {
        modulePositions[moduleIndex] = modules[moduleIndex].getOdometryPositions()[i];
        moduleDeltas[moduleIndex] =
            new SwerveModulePosition(
                modulePositions[moduleIndex].distance
                    - lastModulePositions[moduleIndex].distance,
                modulePositions[moduleIndex].angle);
        lastModulePositions[moduleIndex] = modulePositions[moduleIndex];
      }

      // Update gyro angle
      if (gyroInputs.connected && i < gyroInputs.odometryYawPositions.length) {
        // Use the real gyro angle
        rawGyroRotation = gyroInputs.odometryYawPositions[i];
      } else {
        // Use the angle delta from the kinematics and module deltas
        Twist2d twist = kinematics.toTwist2d(moduleDeltas);
        rawGyroRotation = rawGyroRotation.plus(new Rotation2d(twist.dtheta));
      }

      // Apply update
      poseEstimator.updateWithTime(sampleTimestamps[i], rawGyroRotation, modulePositions);
      wheelOdometry.update(rawGyroRotation, modulePositions);
    }

    // Update path following gains
    LoggedTunableNumber.ifChanged(
        hashCode(),
        values -> {
          pathXController.setP(values[0]);
          pathYController.setP(values[0]);
          pathThetaController.setP(values[1]);
        },
        pathTranslationKp,
        pathRotationKp);

    // Update gyro alert
    gyroDisconnectedAlert.set(!gyroInputs.connected && Constants.currentMode != Mode.SIM);
  }

  /**
   * Runs the drive at the desired robot-relative velocity.
   *
   * @param velocities Robot-relative velocities in meters/sec and radians/sec
   */
  public void runVelocity(ChassisVelocities velocities) {
    runVelocity(velocities, null, null);
  }

  /**
   * Runs the drive at the desired robot-relative velocity, with optional per-module force
   * feedforwards from a Choreo trajectory.
   *
   * @param velocities Robot-relative velocities in meters/sec and radians/sec
   * @param robotForcesX Robot-relative X force on each module in Newtons, or null
   * @param robotForcesY Robot-relative Y force on each module in Newtons, or null
   */
  public void runVelocity(
      ChassisVelocities velocities, double[] robotForcesX, double[] robotForcesY) {
    // Calculate module setpoints
    ChassisVelocities discreteVelocities = velocities.discretize(Constants.loopPeriodSecs);
    SwerveModuleVelocity[] setpointStates =
        SwerveDriveKinematics.desaturateWheelVelocities(
            kinematics.toSwerveModuleVelocities(discreteVelocities),
            DriveConstants.MAX_LINEAR_SPEED);

    // Log unoptimized setpoints and setpoint velocities
    Logger.recordOutput("SwerveStates/Setpoints", setpointStates);
    Logger.recordOutput("SwerveChassisVelocities/Setpoints", discreteVelocities);

    // Send setpoints to modules
    SwerveModuleVelocity[] optimizedStates = new SwerveModuleVelocity[4];
    for (int i = 0; i < 4; i++) {
      double wheelForce = 0.0;
      if (robotForcesX != null && robotForcesY != null) {
        // Project the module force onto the wheel's commanded direction
        wheelForce =
            robotForcesX[i] * setpointStates[i].angle.getCos()
                + robotForcesY[i] * setpointStates[i].angle.getSin();
      }
      optimizedStates[i] = modules[i].runSetpoint(setpointStates[i], wheelForce);
    }

    Logger.recordOutput("SwerveStates/SetpointsOptimized", optimizedStates);
  }

  /**
   * Follows a Choreo trajectory sample: trajectory feedforward (velocity + module forces) plus PID
   * feedback on the fused field pose.
   */
  public void followTrajectory(SwerveSample sample) {
    Pose2d pose = getPose();
    Pose2d target = sample.getPose();
    Logger.recordOutput("Odometry/TrajectorySetpoint", target);

    ChassisVelocities fieldVelocities = sample.getChassisSpeeds();
    ChassisVelocities corrected =
        new ChassisVelocities(
            fieldVelocities.vx + pathXController.calculate(pose.getX(), target.getX()),
            fieldVelocities.vy + pathYController.calculate(pose.getY(), target.getY()),
            fieldVelocities.omega
                + pathThetaController.calculate(
                    pose.getRotation().getRadians(), target.getRotation().getRadians()));

    // Rotate the field-relative module forces into the robot frame
    double[] fieldFx = sample.moduleForcesX();
    double[] fieldFy = sample.moduleForcesY();
    double cos = pose.getRotation().getCos();
    double sin = pose.getRotation().getSin();
    double[] robotFx = new double[4];
    double[] robotFy = new double[4];
    for (int i = 0; i < 4 && i < fieldFx.length; i++) {
      robotFx[i] = fieldFx[i] * cos + fieldFy[i] * sin;
      robotFy[i] = -fieldFx[i] * sin + fieldFy[i] * cos;
    }

    runVelocity(corrected.toRobotRelative(pose.getRotation()), robotFx, robotFy);
  }

  /** Runs the drive in a straight line with the specified drive output. */
  public void runCharacterization(double output) {
    for (int i = 0; i < 4; i++) {
      modules[i].runCharacterization(output);
    }
  }

  /** Stops the drive. */
  public void stop() {
    runVelocity(new ChassisVelocities());
  }

  /**
   * Stops the drive and turns the modules to an X arrangement to resist movement. The modules will
   * return to their normal orientations the next time a nonzero velocity is requested.
   */
  public void stopWithX() {
    Rotation2d[] headings = new Rotation2d[4];
    for (int i = 0; i < 4; i++) {
      headings[i] = DriveConstants.MODULE_TRANSLATIONS[i].getAngle().orElse(Rotation2d.ZERO);
    }
    kinematics.resetHeadings(headings);
    stop();
  }

  /** Returns a command to run a quasistatic test in the specified direction. */
  public Command sysIdQuasistatic(SysIdRoutine.Direction direction) {
    return run(() -> runCharacterization(0.0))
        .withTimeout(1.0)
        .andThen(sysId.quasistatic(direction));
  }

  /** Returns a command to run a dynamic test in the specified direction. */
  public Command sysIdDynamic(SysIdRoutine.Direction direction) {
    return run(() -> runCharacterization(0.0)).withTimeout(1.0).andThen(sysId.dynamic(direction));
  }

  /** Returns the module states (turn angles and drive velocities) for all of the modules. */
  @AutoLogOutput(key = "SwerveStates/Measured")
  private SwerveModuleVelocity[] getModuleStates() {
    SwerveModuleVelocity[] states = new SwerveModuleVelocity[4];
    for (int i = 0; i < 4; i++) {
      states[i] = modules[i].getState();
    }
    return states;
  }

  /** Returns the module positions (turn angles and drive positions) for all of the modules. */
  private SwerveModulePosition[] getModulePositions() {
    SwerveModulePosition[] states = new SwerveModulePosition[4];
    for (int i = 0; i < 4; i++) {
      states[i] = modules[i].getPosition();
    }
    return states;
  }

  /** Returns the measured robot-relative chassis velocities. */
  @AutoLogOutput(key = "SwerveChassisVelocities/Measured")
  public ChassisVelocities getChassisVelocities() {
    return kinematics.toChassisVelocities(getModuleStates());
  }

  /** Returns the measured field-relative chassis velocities. */
  public ChassisVelocities getFieldVelocities() {
    return getChassisVelocities().toFieldRelative(getRotation());
  }

  /** Returns the position of each module in radians. */
  public double[] getWheelRadiusCharacterizationPositions() {
    double[] values = new double[4];
    for (int i = 0; i < 4; i++) {
      values[i] = modules[i].getWheelRadiusCharacterizationPosition();
    }
    return values;
  }

  /** Returns the average velocity of the modules in rotations/sec (Phoenix native units). */
  public double getFFCharacterizationVelocity() {
    double output = 0.0;
    for (int i = 0; i < 4; i++) {
      output += modules[i].getFFCharacterizationVelocity() / 4.0;
    }
    return output;
  }

  /** Returns the raw gyro yaw (not field-relative). */
  public Rotation2d getGyroRotation() {
    return rawGyroRotation;
  }

  /** Returns the gyro yaw rate in radians/sec. */
  public double getYawVelocityRadPerSec() {
    return gyroInputs.yawVelocityRadPerSec;
  }

  /** Returns the current fused field pose. */
  @AutoLogOutput(key = "Odometry/Robot")
  public Pose2d getPose() {
    return poseEstimator.getEstimatedPosition();
  }

  /** Returns the wheel-and-gyro-only pose (no QuestNav or vision corrections). */
  @AutoLogOutput(key = "Odometry/WheelOnly")
  public Pose2d getWheelOdometryPose() {
    return wheelOdometry.getPose();
  }

  /** Returns the fused pose at a past timestamp (for latency-compensated vision math). */
  public Optional<Pose2d> getPoseAt(double timestampSeconds) {
    return poseEstimator.sampleAt(timestampSeconds);
  }

  /** Returns the current fused field rotation. */
  public Rotation2d getRotation() {
    return getPose().getRotation();
  }

  /** Hard-resets the pose (auto start or driver reset) and notifies listeners like QuestNav. */
  public void setPose(Pose2d pose) {
    SwerveModulePosition[] positions = getModulePositions();
    poseEstimator.resetPosition(rawGyroRotation, positions, pose);
    // In simulation this also "teleports" the simulated robot, since wheel odometry is the truth
    wheelOdometry.resetPosition(rawGyroRotation, positions, pose);
    for (var listener : poseResetListeners) {
      listener.accept(pose);
    }
  }

  /** Resets only the heading, keeping the current translation. */
  public void setRotation(Rotation2d rotation) {
    setPose(new Pose2d(getPose().getTranslation(), rotation));
  }

  /** Registers a callback that runs whenever {@link #setPose} hard-resets the pose. */
  public void addPoseResetListener(Consumer<Pose2d> listener) {
    poseResetListeners.add(listener);
  }

  /** Adds a new timestamped vision (QuestNav or AprilTag) measurement. */
  public void addVisionMeasurement(
      Pose2d visionRobotPoseMeters,
      double timestampSeconds,
      Matrix<N3, N1> visionMeasurementStdDevs) {
    poseEstimator.addVisionMeasurement(
        visionRobotPoseMeters, timestampSeconds, visionMeasurementStdDevs);
  }

  /** Returns the maximum linear speed in meters per sec. */
  public double getMaxLinearSpeedMetersPerSec() {
    return DriveConstants.MAX_LINEAR_SPEED;
  }

  /** Returns the maximum angular speed in radians per sec. */
  public double getMaxAngularSpeedRadPerSec() {
    return DriveConstants.MAX_ANGULAR_SPEED;
  }

  /** Returns an array of module translations. */
  public static Translation2d[] getModuleTranslations() {
    return DriveConstants.MODULE_TRANSLATIONS;
  }
}
