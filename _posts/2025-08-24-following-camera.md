---
layout: post
title:  Following camera and orbital physics
date:   2025-08-24 23:14:00 +0100
categories: interface
---

![following camera](/sfsim/pics/following-camera.jpg)

Hi,

Version 0.4-1 of *sfsim* comes with a following camera and improvements to the physics engine.

The camera now follows the vehicle and the orientation is controlled using the speed over ground vector.
The numerical keypad can still be used to control the camera position.
However the keys now control the yaw, pitch, and roll position.

There also has been a significant upgrade to the physics code.
In order to get sufficient accuracy for simulating orbits, a matching scheme using 4th Runge Kutta integration is used.
Like this I am able to use the Jolt Physics engine for accurate simulations of space flight.
See my article on [orbits with Jolt physics][2] for technical details.

Let me know any feedback and comments in the [sfsim playtest discussion forum][1].

Enjoy!

[1]: https://steamcommunity.com/app/3847320/discussions/
[2]: https://www.wedesoft.de/simulation/2025/08/09/orbits-with-jolt-physics/
