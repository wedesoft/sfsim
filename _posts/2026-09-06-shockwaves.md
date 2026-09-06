---
layout: post
title:  Shockwaves
date:   2026-09-06 23:45:23 +0100
categories: graphics
---

I am excited to share early results of shockwave rendering with the community!

I would like to add visualization of a glowing shockwave during atmospheric reentry in the [sfsim][1] spaceflight simulator.
I explored possible approaches by discussing the problem with Google Gemini.
Popular approaches seem to use meshes with animated semi-transparent noise textures.
However this still leaves the problem of determining the shape of the mesh.

Also the shape of the single-stage-to-orbit spacecraft is complex and the shockwave will look different depending on the orientation of the spacecraft.
![windtunnel image of spaceship](/sfsim/pics/windtunnel.jpg)

In the end, Google Gemini pointed out the [Jump Flooding Algorithm][2] (JFA), published in 2006.
This algorithm essentially enables a real-time distance transform on the GPU.
In this case, it can instead be adapted to fit a shockwave to each wind-facing surface patch and determine, for each pixel, which shockwave candidate lies farthest upstream in the wind direction.

Here is a depth image as seen from the direction the wind is coming from.
![depth image from wind direction](/sfsim/pics/depth.png)

JFA then expands this image into a depth image of the shock front.
Billig's formula is used (with a fixed nose curvature radius for this prototype) to create a shockwave emitter at each surface pixel.
![visualization of depth of shockwave](/sfsim/pics/shockfront.png)

The following image visualizes the seed pixel coordinates for the image.
JFA has to track seed pixel coordinates in order to properly propagate the dominant/nearest shockwave.
![visualization of seed pixel coordinates](/sfsim/pics/seedpixels.png)

The remaining step is to use this depth image for volumetric rendering of the shockwave.
The next image shows a volumetric box used for rendering the shockwave.
![box for volumetric shader](/sfsim/pics/fogbox.jpg)

By limiting the fog to pixels behind the shockwave, one obtains the following image.
![rendering the shock volume](/sfsim/pics/shockvolume.jpg)

One can instead apply exponential fall-off to create a more realistic image of a glowing shock front.
![rendering the shock wave](/sfsim/pics/shockwave.jpg)

Future work includes taking into account the surface slope and curvature to get more realistic shockwaves.

Let me know any feedback and comments in the [sfsim playtest discussion forum][3] or one of the social channels below.

[1]: https://wedesoft.github.io/sfsim/
[2]: https://en.wikipedia.org/wiki/Jump_flooding_algorithm
[3]: https://steamcommunity.com/app/3847320/discussions/
