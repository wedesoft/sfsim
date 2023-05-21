# TODO
* modules to port to fastmath:
  test/sfsim25/t_atmosphere.clj
  test/sfsim25/t_clouds.clj
  test/sfsim25/t_ellipsoid.clj
  test/sfsim25/t_planet.clj
  deps.edn
* add origin at z-near to vertex shader
* lod of shadow
* ray scatter with cloud shadows
* amplify glare? appearance of sun? s2016-pbs-frostbite-sky-clouds-new.pdf page 28
* add separate tests for surface radiance shader
* use mocks in tests for ground radiance shader
* add z-near offset to origin?
* separate cloud\_shadow and transmittance\_outer
* add conditions to shortcut lookup in some noise textures
* how is cloud\_max\_step (cms) still used?
* is cloud-scatter-amount and density redundant?
* non-linear steps?
* increase stepsize between clouds (also only sample low-resolution noise)
* render just atmosphere with empty cloud layer to check that there is no step
* no shading of atmosphere above 25km
* add atmospheric transmittance and scattering
* limit opacity mapping and cloud sampling
* clouds
  * test cloud shadows on ellipsoidical planet
  * add clouds to atmospheric and planetary shader
  * add shadows of mountains, use shadow map of planet in cloud\_shadow?
  * ozone absorption? s2016-pbs-frostbite-sky-clouds-new.pdf page 20
  * attenuation of far clouds, transmittance-weighted average cloud distance -> correct atmospheric scattering
  * exponentially reduce and or limit samples with distance or when viewing from space and do level of detail
  * offscreen render clouds with low resolution (and atmosphere in front) with alpha channel, use blue noise z-offsets and blur
  * render 1 of 4x4 pixels per frame?
  * apply cloud texture to higher resolution picture (upscale and alpha-blend)
  * add flat cirrus clouds
  * move different levels of noise to create moving and shape-changing clouds
* powder sugar effect https://progmdong.github.io/2019-03-04/Volumetric_Rendering/
  [combined Beers and powder function](https://www.youtube.com/watch?v=8OrvIQUFptA)
  https://www.youtube.com/watch?v=Qj_tK_mdRcA
* integration test for cascaded deep opacity map
* uniform random offsets for Worley noises
* configuration (edn) file for clouds?
* rewrite cloudy.clj prototype
* Kenny Mitchell: volumetric light scattering as a post process (iterate highlight, use to mask mie scattering)
* ACES tone mapping: https://github.com/TheRealMJP/BakingLab/blob/master/BakingLab/ACES.hlsl
* moon light only dominant when sun has set
* glTextureStorage2D levels correct for mipmaps?
* create new cloud prototype avoiding step in atmosphere and flatness of cloud boundaries
* try to install and use LWJGL3 from Maven
* implement shadow maps: https://developer.nvidia.com/gpugems/gpugems3/part-ii-light-and-shadows/chapter-10-parallel-split-shadow-maps-programmable-gpus
* use Earth explorer data: https://earthexplorer.usgs.gov/
* use GMTED2010 or STRM90 elevation data:
  * https://topotools.cr.usgs.gov/gmted_viewer/viewer.htm
  * https://www.eorc.jaxa.jp/ALOS/en/dataset/aw3d_e.htm
  * https://www.eorc.jaxa.jp/ALOS/en/dataset/aw3d30/aw3d30_e.htm
* render to texture with alpha channel
* horizon still bright even under dark clouds (attenuation\_track needs to take into account cloudiness)
* seapare transmittance\_outer call from cloud\_shadow
* does opacity fragment shader need to limit offsets to bounding box?
* convert\_1d\_index
* does lookup\_3d need to use textureLod?
* test opacity offsets with constant density cloud, use clip\_shell\_intersections
* use maximum possible cloud self-shadow length?
* clouds: blue noise offsets for opacity map? use extra shadow map?
* use rgb for scattering and a for transmittance when creating low-resolution cloud image?
* write article about cloud rendering
* use compute shaders? in parallel with graphics?
  GL\_COMPUTE\_SHADER type, glDispatchCompute 1024x1024x64 items (product at least 1024)
* make is-image checker less strict (threshold relative sum of difference?) or add new checker roughly-image
* integration test planet shader with non-trivial lookup tables? convert prototype to tested code
* deferred decals for rendering runway
  https://www.reddit.com/r/opengl/comments/10rwgy7/what_is_currently_the_best_method_to_render_roads/
* shadows
  * add atmospheric scattering taking cloud shadow into account (cascaded shadow maps)
  * add cloud shadow computation to ground radiance function, compute cloud shadows on ground with quarter resolution
  * how to render shadows on planet surface and in atmosphere; shadow maps or shadow volumes (bruneton chapter 5)?
  * polygonoffset?
* hot spots for map
* microtextures, bump maps
* how to render water, waves
* render stars
  * Skydome: counter-clockwise front face (GL11/glFrontFace GL11/GL\_CCW) (configuration object)
  * Skydome scaled to ZFAR * 0.5
  * no skydome and just stars as pixels?
* spacecraft rendering
  * glTF/GLB file format, Assimp library, Java bindings https://github.com/kotlin-graphics/assimp, see https://poly.pizza/
  * 3D model: Dream Chaser, Soyuz, PTK NP, https://www.thingiverse.com/thing:2565361
  * create windows using blending
  * http://www.ioaircraft.com/hypersonic/ranger.php
  * http://www.ioaircraft.com/hypersonic/raven.php
  * https://www.russianspaceweb.com/spiral_orbiter_design.html
* 3D cockpit
  * Open Glass Cockpit: https://opengc.sourceforge.net/screenshots.html
  * Kerbal cockpit: https://www.youtube.com/watch?v=XhudXvmnYwU
  * SpaceX cockpit: https://iss-sim.spacex.com/
  * orbit plane alignment
  * docking view
  * horizon with height and variometer
  * encounter
    * station
    * moon/base
    * earth
  * aerobrake/base roll-reversal, speed-height-distance profile
  * heading alignment cylinder
* plan work
* ground\_radiance assumes sun brightness of one -> use one everywhere?
* use different normal for ground\_radiance? needs to be parallel to radius vector?
* only render sun glare when sun is above horizon, use single (normalised?) color from transmittance
* extract functions from prototype
* indices for planet patches and atmosphere projection plane should be the same
* put parameters like max-height, power, specular, radius, polar-radius in a configuration file
* radius1, radius2 -> radius, polar-radius
* use GMTED2010 data: https://topotools.cr.usgs.gov/gmted\_viewer/viewer.htm
* find water land mask data: https://lpdaac.usgs.gov/products/mod44wv006/
* night-time textures
* improve rendering of sun
* render moonlight and moon
* organize fixtures using subdirectories
* is all planet rendering represented as a quad-tree?
* normal maps
* text rendering using bitmap fonts
* use data-centric APIs
* use glTexSubImage2D?
* use ZGC (short pause garbage collector for Java)
* redesign floating point math of height maps
* improve performance of quaternions (see clojure.core.matrix implementation)
* Get scale-image to work on large images
* use short integers for normal vector textures?
* NASA docking system, soft-dock, hard-dock, https://www.youtube.com/watch?v=dWYpVfhvsak
* EF2000 like cockpit controls (quick access views) ctrl+a,b,c,...? ctrl+arrow?
* EF2000 like gear suspension
* planet, moon position (check Orbiter 2016 code)
* blinking beacon/position lights
* determine contact points
* Euler integration
* multiple contacts
* collision of blocks
* contact constraints of blocks
* joint constraints
* Runge-Kutta integration
* convex hull extraction or definition
* collision of gear
* estimate friction impulse
* display body with gears
* quaternion, state vectors
* compute forces of zero-mass spring damper gears
* cockpit: frontal and side view of moon lander
* XBox controller
* 3D moon rendering
* airport-like departure tables
* bump maps
* render articulated objects with configuration
* suspension using spring damper elements
* mass matrix
* JIT compilation to speed it up
* compiled sfsim.scm not being found
* music player, mp3 player
* video communication with screen in 3D cockpit
* autopilot programs: baseland, helicopter, hover/autoland, launch/deorbit, aerocapture/aerobrake, airspeed hold, attitude hold, altitude hold, heading alignment cylinder, VOR/ILS, eject, capture, base sync, station sync, dock
* on board displays, cameras, MFDs (horizon with height/vario and scrolling scales)
* no need to adjust MFDs during critical parts of the mission
* https://github.com/HappyEnte/DreamChaser
* HDR bloom (separable convolution)
* multisampling
* windows (blending, reflections), greyscale cameras, MFDs
* frame buffer objects for non-blocking data transfer
* skybox for nebulas
* point sprites for stars, atmospheric flicker using sprite arrays
* point light sources
* cloud erosion
* project on sphere
* view matrix, model matrix stack, light position
* multiple rigid or flexible objects
* thrusters
* proper lift/drag ratio for high Reynolds numbers
* draw points, lines, triangle strips, mfd for reentry and orbital mechanics
* detailed onboard systems (e.g. airlock, life support, auxiliary power units)
* threads for rendering, simulation, and loading of data
* event-based radio (triggers as in Operation Flashpoint)
* missions and high scores
* beep-beep sound, paraglider audio?
* uniform distribution on sphere http://marc-b-reynolds.github.io/distribution/2016/11/28/Uniform.html
* fluid dynamics on GPU: https://developer.nvidia.com/gpugems/gpugems/part-vi-beyond-triangles/chapter-38-fast-fluid-dynamics-simulation-gpu
* introduction to fluid dynamics: https://shahriyarshahrabi.medium.com/gentle-introduction-to-fluid-simulation-for-programmers-and-technical-artists-7c0045c40bac
* fluid dynamics tutorial: http://graphics.cs.cmu.edu/nsp/course/15-464/Fall09/papers/StamFluidforGames.pdf
