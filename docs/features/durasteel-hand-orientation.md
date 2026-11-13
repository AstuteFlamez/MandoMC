# Feature: Durasteel helmet hand orientation
Status: implementation; client verification pending
Branch: bilal/durasteel-hand-orientation

## Player experience
Holding a Durasteel helmet uses the same hand pose as the working Beskar and
Stormtrooper helmets, including first-person offhand rendering.

## Rules and smallest playable version
Copy the existing firstperson_righthand and thirdperson_righthand display
transforms from Beskar. Keep the same omitted-left-hand fallback configuration
as both working references. Preserve geometry, textures, GUI, worn and dropped
poses. No gameplay, ownership, costs or item identity changes.

## Ownership and parameters
MandoMC owns the Durasteel item model in the required resource pack. Its first
person rotation is [-180, 67, -180], translation [-0.5, 3.75, -0.5] and scale
[0.8, 0.8, 0.8]. Third person rotation is [68, 60, 0] and scale [0.5, 0.5, 0.5].
Apply through the normal resource-pack publication/startup workflow, then reload
the pack in the client.

## Verification and pause note
Resource-pack build, eight existing model checks and ./scripts/build.sh pass.
Compared all 1178 built ZIP entries to the branch baseline: only the Durasteel
model differs, and removing the two added display contexts restores the exact
original parsed model. Publication and client verification remain pending.
Next action: apply the tested pack to development and compare Durasteel with
Beskar/Stormtrooper in both hands in first and third person.
