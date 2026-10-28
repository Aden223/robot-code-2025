// Copyright (c) 2021-2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package first.robot.subsystems.drive;

import com.ctre.phoenix6.configs.CANcoderConfiguration;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.swerve.SwerveModuleConstants;
import org.littletonrobotics.junction.Logger;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.SwerveModulePosition;
import org.wpilib.math.kinematics.SwerveModuleVelocity;
import org.wpilib.math.system.DCMotor;
import org.wpilib.math.util.Units;
import org.wpilib.util.Alert;

public class Module {
  private static final DCMotor DRIVE_MOTOR = DCMotor.getKrakenX60(1);

  private final ModuleIO io;
  private final ModuleIOInputsAutoLogged inputs = new ModuleIOInputsAutoLogged();
  private final String logKey;
  private final SwerveModuleConstants<
          TalonFXConfiguration, TalonFXConfiguration, CANcoderConfiguration>
      constants;

  private final Alert driveDisconnectedAlert;
  private final Alert turnDisconnectedAlert;
  private final Alert turnEncoderDisconnectedAlert;
  private SwerveModulePosition[] odometryPositions = new SwerveModulePosition[] {};

  public Module(
      ModuleIO io,
      int index,
      SwerveModuleConstants<TalonFXConfiguration, TalonFXConfiguration, CANcoderConfiguration>
          constants) {
    this.io = io;
    this.logKey = "Drive/Module" + index;
    this.constants = constants;
    driveDisconnectedAlert =
        new Alert(
            "Drive", "Module" + index + "DriveDisconnected",
            "Disconnected drive motor on module " + index + ".",
            Alert.Level.HIGH);
    turnDisconnectedAlert =
        new Alert(
            "Drive", "Module" + index + "TurnDisconnected",
            "Disconnected turn motor on module " + index + ".",
            Alert.Level.HIGH);
    turnEncoderDisconnectedAlert =
        new Alert(
            "Drive", "Module" + index + "EncoderDisconnected",
            "Disconnected turn encoder on module " + index + ".",
            Alert.Level.HIGH);
  }

  public void periodic() {
    io.updateInputs(inputs);
    Logger.processInputs(logKey, inputs);

    // Calculate positions for odometry
    int sampleCount = inputs.odometryTimestamps.length; // All signals are sampled together
    odometryPositions = new SwerveModulePosition[sampleCount];
    for (int i = 0; i < sampleCount; i++) {
      double positionMeters = inputs.odometryDrivePositionsRad[i] * constants.WheelRadius;
      Rotation2d angle = inputs.odometryTurnPositions[i];
      odometryPositions[i] = new SwerveModulePosition(positionMeters, angle);
    }

    // Update alerts
    driveDisconnectedAlert.set(!inputs.driveConnected);
    turnDisconnectedAlert.set(!inputs.turnConnected);
    turnEncoderDisconnectedAlert.set(!inputs.turnEncoderConnected);
  }

  /**
   * Runs the module with the specified setpoint state.
   *
   * @param state Wheel velocity (m/s) and angle setpoint
   * @param wheelForceNewtons Force the wheel should apply along the setpoint direction (from a
   *     Choreo trajectory), converted to a drive motor voltage feedforward. Pass 0 when unknown.
   * @return The optimized setpoint that was actually applied (for logging)
   */
  public SwerveModuleVelocity runSetpoint(SwerveModuleVelocity state, double wheelForceNewtons) {
    // Optimize velocity setpoint (2027 returns a new object). A flipped module flips its force.
    SwerveModuleVelocity optimized = state.optimize(getAngle());
    if (Math.signum(optimized.velocity) != Math.signum(state.velocity)) {
      wheelForceNewtons = -wheelForceNewtons;
    }
    // Scale down the velocity while the module is still turning toward the setpoint
    SwerveModuleVelocity scaled = optimized.cosineScale(inputs.turnPosition);

    // Wheel force -> motor torque -> voltage (no back-EMF term; velocity feedforward covers it)
    double motorTorqueNm = wheelForceNewtons * constants.WheelRadius / constants.DriveMotorGearRatio;
    double feedforwardVolts = DRIVE_MOTOR.getVoltage(motorTorqueNm, 0.0);

    // Apply setpoints
    io.setDriveVelocity(scaled.velocity / constants.WheelRadius, feedforwardVolts);
    io.setTurnPosition(scaled.angle);
    return scaled;
  }

  /** Runs the module with the specified output while controlling to zero degrees. */
  public void runCharacterization(double output) {
    io.setDriveOpenLoop(output);
    io.setTurnPosition(Rotation2d.ZERO);
  }

  /** Disables all outputs to motors. */
  public void stop() {
    io.setDriveOpenLoop(0.0);
    io.setTurnOpenLoop(0.0);
  }

  /** Returns the current turn angle of the module. */
  public Rotation2d getAngle() {
    return inputs.turnPosition;
  }

  /** Returns the current drive position of the module in meters. */
  public double getPositionMeters() {
    return inputs.drivePositionRad * constants.WheelRadius;
  }

  /** Returns the current drive velocity of the module in meters per second. */
  public double getVelocityMetersPerSec() {
    return inputs.driveVelocityRadPerSec * constants.WheelRadius;
  }

  /** Returns the module position (turn angle and drive position). */
  public SwerveModulePosition getPosition() {
    return new SwerveModulePosition(getPositionMeters(), getAngle());
  }

  /** Returns the module state (turn angle and drive velocity). */
  public SwerveModuleVelocity getState() {
    return new SwerveModuleVelocity(getVelocityMetersPerSec(), getAngle());
  }

  /** Returns the module positions received this cycle. */
  public SwerveModulePosition[] getOdometryPositions() {
    return odometryPositions;
  }

  /** Returns the timestamps of the samples received this cycle. */
  public double[] getOdometryTimestamps() {
    return inputs.odometryTimestamps;
  }

  /** Returns the module position in radians. */
  public double getWheelRadiusCharacterizationPosition() {
    return inputs.drivePositionRad;
  }

  /** Returns the module velocity in rotations/sec (Phoenix native units). */
  public double getFFCharacterizationVelocity() {
    return Units.radiansToRotations(inputs.driveVelocityRadPerSec);
  }

  /** Returns the drive motor stator current in amps (used for collision/slip detection). */
  public double getDriveCurrentAmps() {
    return inputs.driveCurrentAmps;
  }
}
