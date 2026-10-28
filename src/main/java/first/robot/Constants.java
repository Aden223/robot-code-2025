// Copyright (c) 2021-2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package first.robot;

import org.wpilib.framework.RobotBase;

/**
 * Robot-wide constants. Subsystem-specific constants live next to their subsystem (for example
 * {@code DriveConstants}, {@code VisionConstants}, {@code QuestNavConstants}).
 */
public final class Constants {
  private Constants() {}

  /**
   * The runtime mode used by AdvantageKit. The mode is always "real" when running on a Systemcore.
   * Change the value of "simMode" to switch between "sim" (physics sim) and "replay" (log replay
   * from a file).
   */
  public static final Mode simMode = Mode.SIM;

  public static final Mode currentMode = RobotBase.isReal() ? Mode.REAL : simMode;

  /**
   * Enables tuning mode, which publishes {@link first.robot.util.LoggedTunableNumber} values to
   * NetworkTables so they can be changed live. Must be false at competition.
   */
  public static final boolean tuningMode = true;

  /** Main robot loop period in seconds. */
  public static final double loopPeriodSecs = 0.02;

  public static enum Mode {
    /** Running on a real robot. */
    REAL,

    /** Running a physics simulator. */
    SIM,

    /** Replaying from a log file. */
    REPLAY
  }
}
