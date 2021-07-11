# TODO

* separate accumulation of Mie and Rayleigh scattering
* compute shadow in atmosphere
* use lookup texture to optimise atmospheric rendering
* compute color on ground
* use highp for position, lowp for color, mediump for texture coordinates
* redesign floating point math of height maps
* adapt z-range depending on distance to globe
* improve performance of quaternions (see clojure.core.matrix implementation)
* Get scale-image to work on large images
* Skydome: counter-clockwise front face (GL11/glFrontFace GL11/GL\_CCW) (configuration object)
* Skydome scaled to ZFAR * 0.5
* use short integers for normal vector textures?

[OpenGL question](https://gamedev.stackexchange.com/questions/192358/opengl-height-map-accuracy-for-planetary-rendering)
