---
layout: post
title:  Deferred Rendering
date:   2026-08-27 19:39:31 +0100
categories: graphics
---

I have released version 0.30-1 of [sfsim][1].

This release introduces a major overhaul of the rendering code as well as a runway for the spacecraft.
The rendering system is implemented using the [Clojure][5] programming language as well as [GLSL][6].
[LWJGL3][4] is used to access the graphics card’s OpenGL bindings.

The new rendering code uses [deferred shading][7], which first computes and stores per-pixel geometry data in a geometry buffer (G-buffer), then calculates each pixel’s final lighting in a separate shader pass.
Deferred shading makes it easy to render [decals][8], which are essentially textures projected onto scene geometry.
It also enables efficient real-time rendering of [many localized light sources][9].

Deferred rendering splits rendering into stages so geometry is processed first, lighting later.

Step by step from the images:

### Geometry Pass

#### Projecting Geometry

![Wireframe of model triangles](/sfsim/pics/wireframe.png)

The scene is made of triangles.
A vertex shader is used to project the triangles which are passed to the rasterizer.
For the planet mesh, the geometry is further refined through tessellation and geometry shaders.
The geometry pass draws all visible geometry and stores per-pixel surface data into multiple buffers (textures) as shown below instead of computing final lighting immediately.

Although the runway could be rendered as a decal in a separate pass, it was chosen to render it within the same shader as the planet.
This will make it easier to later adopt [runtime virtual texturing][12], for example to render airport ground surfaces with markings.

#### Diffuse buffer

![Diffuse color of materials](/sfsim/pics/diffuse.jpg)

The diffuse buffer stores the base RGB color of the material at each pixel.
There is also an emissive buffer for light-emitting surfaces which is not shown here.

#### Material property buffer

![Metallic constant of materials](/sfsim/pics/metallic.png)

The metallic buffer stores a scalar indicating how reflective the material at each pixel is.
The buffer with specular strength (inverse roughness) is not shown here.

#### Normal buffer

![Normal vectors](/sfsim/pics/normals.jpg)

The normal buffer stores the surface normal vector at each pixel in the camera coordinate system.
Normals are useful lateron in order to determine the incident angle of incoming light.


#### View-space Position buffer

![View-space position](/sfsim/pics/points.jpg)

The view-space position buffer stores the 3D position of the surface visible at each pixel.
This data is useful for computing atmospheric scattering and the distance to local light sources during the subsequent lighting pass.

### Lighting pass

![Result of Phong shading](/sfsim/pics/phong.jpg)

During the lighting pass, the G-buffer and a shadow map are used to calculate per-pixel lighting.
Blending can also be used in this pass to update the lighting of only part of the scene, without evaluating every light source at every pixel.
This image shows the shaded result produced with a Phong model.

#### Atmospheric effects

![Atmospheric scattering](/sfsim/pics/atmosphere.jpg)

Phong shading computes the surface color after lighting.
The lighting shader also accounts for [atmospheric scattering][11].
Atmospheric scattering both adds in-scattered light and removes part of the incoming light through scattering.

#### Final compositing

![Cloud overlay](/sfsim/pics/clouds.jpg)

The volumetric clouds are rendered separately using a lower resolution.
The lighting pass then incorporates the cloud layer using depth-aware upsampling.

---

Let me know any feedback and comments in the [sfsim playtest discussion forum][3].

Don't forget to [wishlist sfsim][2]!

[1]: https://wedesoft.github.io/sfsim/
[2]: https://store.steampowered.com/app/3687560/sfsim/
[3]: https://steamcommunity.com/app/3847320/discussions/
[4]: https://www.lwjgl.org/
[5]: https://clojure.org/
[6]: https://en.wikipedia.org/wiki/OpenGL_Shading_Language
[7]: https://en.wikipedia.org/wiki/Deferred_shading
[8]: https://mtnphil.wordpress.com/2014/05/24/decals-deferred-rendering/
[9]: https://learnopengl.com/Advanced-Lighting/Deferred-Shading
[10]: https://en.wikipedia.org/wiki/Phong_shading
[11]: https://ebruneton.github.io/precomputed_atmospheric_scattering/
[12]: https://www.shlom.dev/articles/how-virtual-textures-really-work/
