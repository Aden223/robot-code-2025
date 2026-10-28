package first.robot;

import choreo.auto.AutoFactory;
import first.robot.autos.AutoRoutines;
import first.robot.commands.DriveCommands;
import first.robot.commands.DriveToPose;
import first.robot.generated.TunerConstants;
import first.robot.subsystems.drive.Drive;
import first.robot.subsystems.drive.GyroIO;
import first.robot.subsystems.drive.GyroIOPigeon2;
import first.robot.subsystems.drive.ModuleIO;
import first.robot.subsystems.drive.ModuleIOSim;
import first.robot.subsystems.drive.ModuleIOTalonFX;
import first.robot.subsystems.objectdetection.GamePieceTracker;
import first.robot.subsystems.objectdetection.ObjectDetectionConstants;
import first.robot.subsystems.objectdetection.ObjectDetectionIO;
import first.robot.subsystems.objectdetection.ObjectDetectionIOPhotonVision;
import first.robot.subsystems.objectdetection.ObjectDetectionIOPhotonVisionSim;
import first.robot.subsystems.questnav.QuestNavIO;
import first.robot.subsystems.questnav.QuestNavIOReal;
import first.robot.subsystems.questnav.QuestNavIOSim;
import first.robot.subsystems.questnav.QuestNavSubsystem;
import first.robot.subsystems.vision.Vision;
import first.robot.subsystems.vision.VisionConstants;
import first.robot.subsystems.vision.VisionIO;
import first.robot.subsystems.vision.VisionIOPhotonVision;
import first.robot.subsystems.vision.VisionIOPhotonVisionSim;
import first.robot.util.AllianceFlipUtil;
import java.util.Optional;
import java.util.function.Supplier;
import org.littletonrobotics.junction.Logger;
import org.littletonrobotics.junction.networktables.LoggedNetworkChooser;
import org.wpilib.command2.Command;
import org.wpilib.command2.CommandScheduler;
import org.wpilib.command2.Commands;
import org.wpilib.command2.button.CommandXboxController;
import org.wpilib.command2.button.RobotModeTriggers;
import org.wpilib.command2.button.Trigger;
import org.wpilib.command2.sysid.SysIdRoutine;
import org.wpilib.driverstation.GenericHID.RumbleType;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Translation2d;

/**
 * This class is where the bulk of the robot should be declared: subsystems, controller bindings
 * and autos.
 */
public class RobotContainer {
  /** Distance from robot center to the front of the intake, for driving onto game pieces. */
  private static final double INTAKE_OFFSET_METERS = 0.45;

  /** If the robot is within this distance of a path's start, don't reset odometry for the auto. */
  private static final double AUTO_RESET_TOLERANCE_METERS = 0.75;

  // Subsystems
  private final Drive drive;
  private final QuestNavSubsystem questNav;
  private final Vision vision;
  private final GamePieceTracker gamePieceTracker;

  // Controllers
  private final CommandXboxController driver = new CommandXboxController(0);
  private final CommandXboxController operator = new CommandXboxController(1);

  // Autos
  private final AutoFactory autoFactory;
  private final AutoRoutines autoRoutines;
  private final LoggedNetworkChooser<Supplier<Command>> autoChooser;
  private Command preparedAuto = null;

  public RobotContainer() {
    switch (Constants.currentMode) {
      case REAL -> {
        // Real robot, instantiate hardware IO implementations
        drive =
            new Drive(
                new GyroIOPigeon2(),
                new ModuleIOTalonFX(TunerConstants.FrontLeft),
                new ModuleIOTalonFX(TunerConstants.FrontRight),
                new ModuleIOTalonFX(TunerConstants.BackLeft),
                new ModuleIOTalonFX(TunerConstants.BackRight));
        questNav = new QuestNavSubsystem(new QuestNavIOReal(), drive);
        VisionIO[] cameras = new VisionIO[VisionConstants.CAMERAS.length];
        for (int i = 0; i < cameras.length; i++) {
          cameras[i] =
              new VisionIOPhotonVision(
                  VisionConstants.CAMERAS[i].name(), VisionConstants.CAMERAS[i].robotToCamera());
        }
        vision = new Vision(drive::addVisionMeasurement, questNav::onAprilTagObservation, cameras);
        gamePieceTracker =
            new GamePieceTracker(
                new ObjectDetectionIOPhotonVision(ObjectDetectionConstants.CAMERA_NAME),
                this::poseAt);
      }

      case SIM -> {
        // Sim robot, instantiate physics sim IO implementations. Wheel odometry is the "truth".
        drive =
            new Drive(
                new GyroIO() {},
                new ModuleIOSim(TunerConstants.FrontLeft),
                new ModuleIOSim(TunerConstants.FrontRight),
                new ModuleIOSim(TunerConstants.BackLeft),
                new ModuleIOSim(TunerConstants.BackRight));
        Supplier<Pose2d> truth = drive::getWheelOdometryPose;
        questNav = new QuestNavSubsystem(new QuestNavIOSim(truth), drive);
        VisionIO[] cameras = new VisionIO[VisionConstants.CAMERAS.length];
        for (int i = 0; i < cameras.length; i++) {
          cameras[i] =
              new VisionIOPhotonVisionSim(
                  VisionConstants.CAMERAS[i].name(),
                  VisionConstants.CAMERAS[i].robotToCamera(),
                  truth);
        }
        vision = new Vision(drive::addVisionMeasurement, questNav::onAprilTagObservation, cameras);
        gamePieceTracker =
            new GamePieceTracker(
                new ObjectDetectionIOPhotonVisionSim(
                    ObjectDetectionConstants.CAMERA_NAME,
                    ObjectDetectionConstants.ROBOT_TO_CAMERA,
                    truth),
                this::poseAt);
      }

      default -> {
        // Replayed robot, disable IO implementations
        drive =
            new Drive(
                new GyroIO() {},
                new ModuleIO() {},
                new ModuleIO() {},
                new ModuleIO() {},
                new ModuleIO() {});
        questNav = new QuestNavSubsystem(new QuestNavIO() {}, drive);
        VisionIO[] cameras = new VisionIO[VisionConstants.CAMERAS.length];
        for (int i = 0; i < cameras.length; i++) {
          cameras[i] = new VisionIO() {};
        }
        vision = new Vision(drive::addVisionMeasurement, questNav::onAprilTagObservation, cameras);
        gamePieceTracker = new GamePieceTracker(new ObjectDetectionIO() {}, this::poseAt);
      }
    }

    // Choreo
    autoFactory =
        new AutoFactory(
            drive::getPose,
            this::resetPoseForAuto,
            drive::followTrajectory,
            true,
            drive,
            (trajectory, isStart) ->
                Logger.recordOutput(
                    "Auto/Trajectory", isStart ? trajectory.getPoses() : new Pose2d[0]));
    autoRoutines = new AutoRoutines(autoFactory, drive);

    // Auto chooser (logged, so the selected auto is part of every log and replays correctly)
    autoChooser = new LoggedNetworkChooser<>("Auto Choices");
    autoChooser.addDefault("Do Nothing", autoRoutines::doNothing);
    autoChooser.add("Localization Test", autoRoutines::localizationTest);
    autoChooser.add("Example Choreo Path", () -> autoRoutines.examplePath().cmd());

    // Characterization routines (run these in autonomous with the robot on the floor)
    autoChooser.add(
        "Drive Wheel Radius Characterization",
        () -> DriveCommands.wheelRadiusCharacterization(drive));
    autoChooser.add(
        "Drive Simple FF Characterization", () -> DriveCommands.feedforwardCharacterization(drive));
    autoChooser.add(
        "Drive SysId (Quasistatic Forward)",
        () -> drive.sysIdQuasistatic(SysIdRoutine.Direction.FORWARD));
    autoChooser.add(
        "Drive SysId (Quasistatic Reverse)",
        () -> drive.sysIdQuasistatic(SysIdRoutine.Direction.REVERSE));
    autoChooser.add(
        "Drive SysId (Dynamic Forward)", () -> drive.sysIdDynamic(SysIdRoutine.Direction.FORWARD));
    autoChooser.add(
        "Drive SysId (Dynamic Reverse)", () -> drive.sysIdDynamic(SysIdRoutine.Direction.REVERSE));

    // Build the selected auto as soon as it's picked, so loading paths doesn't delay auto start
    autoChooser.onChange(selected -> preparedAuto = selected.get());

    configureButtonBindings();

    // Load Choreo's code paths once at startup to avoid a Java warm-up pause in auto
    CommandScheduler.getInstance().schedule(autoFactory.warmupCmd());
  }

  private Pose2d poseAt(double timestamp) {
    return drive.getPoseAt(timestamp).orElseGet(drive::getPose);
  }

  /**
   * Choreo's odometry reset. If QuestNav is healthy and the robot is already near the path start,
   * trust the measured pose instead of snapping to where the path assumes the robot is.
   */
  private void resetPoseForAuto(Pose2d pathStart) {
    double error = drive.getPose().getTranslation().getDistance(pathStart.getTranslation());
    Logger.recordOutput("Auto/StartPoseError", error);
    if (!questNav.isHealthy() || error > AUTO_RESET_TOLERANCE_METERS) {
      drive.setPose(pathStart);
    }
  }

  /** Field pose that puts the intake on the closest tracked game piece, facing it. */
  private Optional<Pose2d> getGamePieceIntakePose() {
    Translation2d robot = drive.getPose().getTranslation();
    return gamePieceTracker
        .getClosestPiece(robot)
        .map(
            piece -> {
              Rotation2d facing = piece.minus(robot).getAngle().orElse(drive.getRotation());
              Translation2d intakeOffset = new Translation2d(INTAKE_OFFSET_METERS, facing);
              return new Pose2d(piece.minus(intakeOffset), facing);
            });
  }

  /**
   * Converts a heading relative to the driver (0 = away from the driver station) to a field
   * heading. The driver's view is always rotated 180 degrees on red, whatever the field symmetry.
   */
  private static Rotation2d driverRelative(Rotation2d heading) {
    return AllianceFlipUtil.shouldFlip() ? heading.rotateBy(Rotation2d.PI) : heading;
  }

  private void configureButtonBindings() {
    // DriveCommands applies a circular deadband, so turn off the per-axis controller deadband
    driver.getController().setLeftXDeadband(0.0);
    driver.getController().setLeftYDeadband(0.0);
    driver.getController().setRightXDeadband(0.0);
    driver.getController().setRightYDeadband(0.0);

    // ---------------- Driver (port 0) ----------------
    // Left stick: translate (field relative, from the driver's point of view)
    // Right stick X: rotate
    // Right trigger (hold): slow / precision mode
    drive.setDefaultCommand(
        DriveCommands.joystickDrive(
            drive,
            () -> -driver.getLeftY(),
            () -> -driver.getLeftX(),
            () -> -driver.getRightX(),
            driver.rightTrigger()));

    // Left bumper (hold): keep driving, but always face the closest game piece
    driver
        .leftBumper()
        .whileTrue(
            DriveCommands.joystickDriveAtAngle(
                drive,
                () -> -driver.getLeftY(),
                () -> -driver.getLeftX(),
                () ->
                    gamePieceTracker
                        .getClosestPiece(drive.getPose().getTranslation())
                        .flatMap(piece -> piece.minus(drive.getPose().getTranslation()).getAngle())
                        .orElse(drive.getRotation())));

    // Left trigger (hold): drive onto the closest game piece (the robot drives itself to it)
    Trigger hasGamePiece = new Trigger(gamePieceTracker::hasPiece);
    driver
        .leftTrigger()
        .and(hasGamePiece)
        .whileTrue(
            new DriveToPose(
                drive, () -> getGamePieceIntakePose().orElseGet(drive::getPose)));

    // Right bumper (hold): lock wheels in an X so the robot can't be pushed
    driver.rightBumper().whileTrue(Commands.run(drive::stopWithX, drive));

    // D-pad: snap to a heading (relative to the driver) while still translating
    bindSnapHeading(driver.dpadUp(), Rotation2d.ZERO);
    bindSnapHeading(driver.dpadLeft(), Rotation2d.CCW_90DEG);
    bindSnapHeading(driver.dpadDown(), Rotation2d.PI);
    bindSnapHeading(driver.dpadRight(), Rotation2d.CW_90DEG);

    // Menu (start): reset heading so the robot faces away from the driver.
    // With QuestNav and vision this is rarely needed: it's the emergency fallback.
    driver
        .menu()
        .onTrue(
            Commands.runOnce(() -> drive.setRotation(driverRelative(Rotation2d.ZERO)))
                .ignoringDisable(true));

    // ---------------- Operator (port 1) ----------------
    // Mechanism controls go here after kickoff (BIOCORE, January 9, 2027).

    // View (back): re-seed QuestNav from the current fused pose (use after a Quest restart)
    operator
        .view()
        .onTrue(Commands.runOnce(() -> questNav.seed(drive.getPose())).ignoringDisable(true));

    // ---------------- Feedback ----------------
    // Rumble the driver controller for half a second if QuestNav stops being healthy while enabled
    new Trigger(questNav::isHealthy)
        .negate()
        .and(RobotModeTriggers.teleop())
        .onTrue(rumble(driver, 0.5));
  }

  private void bindSnapHeading(Trigger button, Rotation2d driverRelativeHeading) {
    button.whileTrue(
        DriveCommands.joystickDriveAtAngle(
            drive,
            () -> -driver.getLeftY(),
            () -> -driver.getLeftX(),
            () -> driverRelative(driverRelativeHeading)));
  }

  private static Command rumble(CommandXboxController controller, double seconds) {
    return Commands.startEnd(
            () -> controller.getController().setRumble(RumbleType.LEFT_RUMBLE, 1.0),
            () -> controller.getController().setRumble(RumbleType.LEFT_RUMBLE, 0.0))
        .withTimeout(seconds)
        .ignoringDisable(true);
  }

  /** Returns the autonomous command selected on the dashboard. */
  public Command getAutonomousCommand() {
    if (preparedAuto == null) {
      preparedAuto = autoChooser.get().get();
    }
    Command auto = preparedAuto;
    preparedAuto = null; // Commands can't be scheduled twice; rebuild for the next run
    return auto;
  }

  /** Called when the robot is disabled so the next auto is ready before it starts. */
  public void prepareAuto() {
    if (preparedAuto == null) {
      preparedAuto = autoChooser.get().get();
    }
  }
}
