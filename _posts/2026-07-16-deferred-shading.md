---
layout: post
title:  Deferred shading
date:   2026-07-16 22:46:00 +0100
categories: graphics
---

![Localized lights using deferred shading](/sfsim/pics/deferred.jpg)

Currently I am working on deferred shading.
This requires me to separate rendering into two passes:

* **Geometry pass**: render data to a point buffer, normal buffer, and a color buffer, etc.
* **Lighting pass**: use the geometry buffers to compute lighting including shadows and volumetric clouds.

This will facilitate rendering of runway textures which are at the same height as the rest of the scene but have a higher texture resolution.
Simply adding textured polygons to render a runway does not work, because extra geometry placed at the same elevation as the ground causes [z-fighting][2].
Z-fighting is a rendering artifact where two surfaces are so close together that the depth buffer can’t reliably tell which one is in front.

Another nice feature of deferred shading is that it allows for multiple localized lights to be rendered at once.
You can render the backfaces of a small cube around the light source and use the geometry buffers to light the scene additively, factoring in both the incident angle and the distance from the light source.

I.e., deferred shading enables detailed nighttime runway rendering with dense, spatially localized illumination, while keeping performance manageable compared to forward rendering with many dynamic lights.

Don't forget to [wishlist sfsim][1]!

Enjoy!

[1]: https://store.steampowered.com/app/3687560/sfsim/
[2]: https://en.wikipedia.org/wiki/Z-fighting
