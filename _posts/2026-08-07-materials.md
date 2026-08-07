---
layout: post
title:  Metallic and roughness factor
date:   2026-08-07 23:49:00 +0100
categories: graphics
---

I have released version 0.29-1 of [sfsim][5].

![Metallic material with roughness](/sfsim/pics/materials.jpg)

The [Open Asset Import Library (assimp)][3] which is shipped with the [LWJGL3 library][2] has been updated some time ago.
I am now able to access the metallic value and the roughness value from the glTF exported by [Blender][4].
The metallic and roughness value are now used in the model shader programs so that the spacecraft has a more metallic surface.

![Blender view of spacecraft](/sfsim/pics/blender.jpg)

In the meantime I am still working on implementing deferred shading to facilitate rendering of a runway and localized light sources.

Let me know any feedback and comments in the [sfsim playtest discussion forum][6].

Don't forget to [wishlist sfsim][1]!

[1]: https://store.steampowered.com/app/3687560/sfsim/
[2]: https://www.lwjgl.org/
[3]: https://github.com/assimp/assimp
[4]: https://www.blender.org/
[5]: https://wedesoft.github.io/sfsim/
[6]: https://steamcommunity.com/app/3847320/discussions/
