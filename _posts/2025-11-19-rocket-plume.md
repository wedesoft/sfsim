---
layout: post
title:  Rocket plume for aerospike engine
date:   2025-11-19 21:54:00 +0000
categories: graphics
---

![following camera](/sfsim/pics/plume.jpg)

Hi,

I finally managed to release version 0.5-1 of [sfsim][1].
*sfsim* is a realistic 3D space flight simulator with an advanced single-stage-to-orbit space craft.

The new version comes with rocket plume graphics.
The plume was implemented using OpenGL shaders and uses volumetric rendering.
I used the [Shadertoy][3] website to get ideas from other users and to develop the shader.

The rocket plume shows Mach diamonds when the atmospheric pressure is high enough.
It then expands with decreasing atmospheric pressure.
If you want to, you can try out the [Mach diamonds shader][4] in your browser.

The difficult part was figuring out the plume behaviour for different atmospheric pressure values and also the integration with the volumetric cloud shader.
There is still some work left to be done for *sfsim*, but most difficult problems are solved now I think.

Let me know any feedback and comments in the [sfsim playtest discussion forum][2].

Enjoy!

[1]: https://wedesoft.github.io/sfsim/
[2]: https://steamcommunity.com/app/3847320/discussions/
[3]: https://www.shadertoy.com/
[4]: https://www.shadertoy.com/view/t3XyzB
