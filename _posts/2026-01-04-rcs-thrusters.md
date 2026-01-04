---
layout: post
title:  RCS thrusters and renderer rework
date:   2026-01-04 23:21:00 +0000
categories: simulation
---

![RCS thrusters](/sfsim/pics/rcs.jpg)

Hello everyone,

I released version 0.6-1 of [sfsim][1].
*sfsim* is a realistic 3D space flight simulator with an advanced single-stage-to-orbit space craft.

The graphics implementation is now using deferred rendering to improve performance and facilitate rendering of small thrusters and lights.
Using the "\\" key one can now toggle between aerofoil surfaces and RCS thrusters (please always see the [keyboard shortcut page][3] for up-to-date keyboard bindings).
This is the first release of *sfsim* with RCS thruster physics.
This means that you now can control the orientation of the spacecraft when outside the atmosphere!
It should be now possible to get into orbit.
Make sure you climb at a sufficient angle so that the atmospheric moments are weaker than the RCS thrusters when you start entering orbit.
Note that information about orbital parameters is not implemented yet.

I hope you will have as much fun with the RCS thrusters as I did.

Let me know any feedback and comments in the [sfsim playtest discussion forum][2].

Enjoy!

[1]: https://wedesoft.github.io/sfsim/
[2]: https://steamcommunity.com/app/3847320/discussions/
[3]: https://wedesoft.github.io/sfsim/keyboard/
