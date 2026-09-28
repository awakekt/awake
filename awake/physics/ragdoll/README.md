# `awake:physics:ragdoll`

Ragdolls: limbs as physics bodies joined by constraints, and the drive that makes a skinned
character wear them.

- `humanoidRagdoll` builds an eleven-limb humanoid from a height; `RagdollRig` shapes one to a
  character's bind-pose `Skeleton`, one capsule per named bone.
- `Ragdoll` owns the bodies and constraints in a `PhysicsWorld`. Knees and elbows are limited
  hinges, because they bend one way.
- `RagdollSkeleton` writes the limbs back into an `AnimationPose`, so a ragdoll moves the mesh
  instead of eleven invisible capsules. A bone no limb drives keeps its animated pose.

It depends on `physics:api` only; the application chooses the backend. The tests simulate on Jolt,
because whether a ragdoll holds together is a question only a real solver answers.
