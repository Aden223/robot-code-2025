package first.robot.autos;

import choreo.auto.AutoFactory;
import choreo.auto.AutoRoutine;
import choreo.auto.AutoTrajectory;
import first.robot.commands.DriveToPose;
import first.robot.subsystems.drive.Drive;
import java.util.Set;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Transform2d;

/**
 * Autonomous routines. Paths are made in the Choreo app (https://choreo.autos) and saved to
 * src/main/deploy/choreo. Trajectories are blue-alliance paths; Choreo flips them for red.
 *
 * <p>BIOCORE is revealed January 9, 2027, so the real autos are written after kickoff. The routines
 * here show the patterns: run a path, run something at an event marker, chain paths, and use the
 * fused pose for a final precise alignment.
 */
public class AutoRoutines {
  private final AutoFactory factory;
  private final Drive drive;

  public AutoRoutines(AutoFactory factory, Drive drive) {
    this.factory = factory;
    this.drive = drive;
  }

  /** Does nothing (the safe default). */
  public Command doNothing() {
    return Commands.none().withName("Do Nothing");
  }

  /**
   * Drives 1.5 m forward from wherever the robot is, using the fused pose. Needs no Choreo file, so
   * it's the first auto to test on a new robot: if QuestNav and vision are healthy the robot stops
   * within a couple of centimeters of the target.
   */
  public Command localizationTest() {
    return Commands.defer(
            () -> {
              Pose2d start = drive.getPose();
              Pose2d goal = start.transformBy(new Transform2d(1.5, 0.0, Rotation2d.ZERO));
              return new DriveToPose(drive, () -> goal).withTimeout(5.0);
            },
            Set.of(drive))
        .withName("Localization Test (1.5 m forward)");
  }

  /**
   * Example Choreo routine. Create a path named "ExamplePath" in Choreo (with an event marker named
   * "intake" if you like) to try it.
   */
  public AutoRoutine examplePath() {
    AutoRoutine routine = factory.newRoutine("Example Path");
    AutoTrajectory path = routine.trajectory("ExamplePath");

    // When the routine starts: reset odometry to the path start (if needed), then follow it
    routine.active().onTrue(Commands.sequence(path.resetOdometry(), path.cmd()));

    // Run commands at event markers placed in Choreo, e.g. start the intake
    path.atTime("intake").onTrue(Commands.print("Intake event marker reached"));

    // Precise final alignment with the fused pose once the path is done. Deferred so the final
    // pose is flipped for the alliance known at run time, not at build time.
    path.done()
        .onTrue(
            Commands.defer(
                () ->
                    path.getFinalPose()
                        .<Command>map(goal -> new DriveToPose(drive, () -> goal).withTimeout(1.0))
                        .orElse(Commands.none()),
                Set.of(drive)));
    return routine;
  }
}
