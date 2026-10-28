# QuestNav robot library (vendored)

This folder is a copy of the QuestNav Java robot library (MIT License, see `LICENSE-QuestNav.txt`)
taken from the `Andy/WPILib2027` branch of https://github.com/QuestNav/QuestNav (commit `823d3f9`).

QuestNav has not published a WPILib 2027 (Systemcore) vendordep yet, so the source is included here.
`protos/generated/` was generated from QuestNav's `protos/*.proto` with `protoc` + `protoc-gen-quickbuf 1.4`,
exactly like QuestNav's own CI does.

**When QuestNav releases a 2027 vendordep:** delete this whole `gg/questnav` folder and install the
vendordep (WPILib: Manage Vendor Libraries -> Install new libraries (online)). No other code changes are
needed because the robot code only talks to QuestNav through `first.robot.subsystems.questnav.QuestNavIOReal`.
