---
layout: post
title: Adaptive tessellation
date: 2026-11-13 01:00:37 +0000
categories: graphics
---

I have released version 0.39-1 of [sfsim][1].

![flight control display](/sfsim/pics/tessellation.jpg)

This release includes a significant update to the [tessellation][4] control implementation.
Tessellation is the process of dividing a 3D surface into smaller polygons, allowing it to display finer detail of the terrain.
Improved control over this process has substantially reduced the polygon count, boosting rendering performance.

For the time being, I’ve also reduced the quality of the volumetric clouds.

Let me know any feedback and comments in the [sfsim playtest discussion forum][2].

Don't forget to [wishlist sfsim][3] and enjoy!

[1]: https://wedesoft.github.io/sfsim/
[2]: https://steamcommunity.com/app/3847320/discussions/
[3]: https://store.steampowered.com/app/3687560/sfsim/
[4]: https://en.wikipedia.org/wiki/Tessellation_(computer_graphics)
