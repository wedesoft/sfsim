---
layout: post
title:  "Landing Gear Physics"
date:   2025-05-30 21:06:00 +0100
categories: simulation
---

![gear](/sfsim/pics/underbelly.jpg)

I have created landing gear animations with Blender, baked them, and exported the result to glTF.
"Inverse kinematics", "Damped track", and "Child of" constraints where used to ensure that torque link, resistance rod, and piston are animated correctly.
Animations affecting multiple objects are merged using NLA track names when exporting to glTF.
I didn't find a way to create multiple animations affecting the same objects, so I had to sequence steering, suspension, and gear deployment in the same animation.
Wheel rotation however was kept separate.
The following screen recording shows Blender playing back the sequence of steering, suspension, and gear deployment in parallel with the wheel rotation.

{% include youtube.html id="A_yCNBr2qdU" %}

The model including the animations are imported using the [Assimp][4] bindings of [LWJGL][3].
The suspension and wheel rotation then can be driven using the wheeled vehicle constraint of the [Jolt Physics][2] engine.
The planet surface was simulated by extracting 3x3 tiles from the planetary cube map and converting them to a static Jolt mesh.
By simulating the wheels as moving surfaces, the numerical instabilities of simulating a rotating cylinder with constraints directly are avoided.
The result is a beautiful interaction of the landing gear with the ground as shown in the following video (music: "Dune" by Andrewkn).

{% include youtube.html id="GiBjMYRKbfU" %}

If you are interested in a realistic space flight simulator, please consider [wishlisting sfsim on Steam][1].

[1]: https://store.steampowered.com/app/3687560/sfsim/
[2]: https://jrouwe.github.io/JoltPhysics/
[3]: https://www.lwjgl.org/
[4]: https://assimp.org/
