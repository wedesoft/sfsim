# TODO
* special shaders for planet shadow
* test cascaded shadow map, opacity cascade
* integration test planet shader with non-trivial lookup tables? convert prototype to tested code
* put new shader stuff under test
* mipmaps for all cloud textures and octaves
* constant texel size across opacity cascade to prevent step in opacity?
* when building maps put intermediate files into a common subdirectory (tmp?)
* make cloud prototype more modular, separate cloud\_shadow and transmittance\_outer,
  integration test for cascaded deep opacity map
* increase stepsize between clouds (also only sample low-resolution noise), increase shadow depth if possible
* change cloud computation when viewing from space far away (use different lod of shadow?)
* reduce number of intermediate values to increase OpenGL performance
* amplify glare? appearance of sun? s2016-pbs-frostbite-sky-clouds-new.pdf page 28
* reuse (parts of) shadow map?
* powder sugar effect https://progmdong.github.io/2019-03-04/Volumetric\_Rendering/
  [combined Beers and powder function](https://www.youtube.com/watch?v=8OrvIQUFptA)
  https://www.youtube.com/watch?v=Qj\_tK\_mdRcA
* shadow mapping improvements http://www.opengl-tutorial.org/intermediate-tutorials/tutorial-16-shadow-mapping/
* uniform random offsets for Worley noises
* configuration (edn) file for clouds?
* ACES tone mapping: https://github.com/TheRealMJP/BakingLab/blob/master/BakingLab/ACES.hlsl
* glTextureStorage2D levels correct for mipmaps?
* hot spots for map
* use Earth explorer data: https://earthexplorer.usgs.gov/
* use GMTED2010 or STRM90 elevation data:
  * https://topotools.cr.usgs.gov/gmted\_viewer/viewer.htm
  * https://www.eorc.jaxa.jp/ALOS/en/dataset/aw3d\_e.htm
  * https://www.eorc.jaxa.jp/ALOS/en/dataset/aw3d30/aw3d30\_e.htm
* deferred decals for rendering runway
  https://www.reddit.com/r/opengl/comments/10rwgy7/what\_is\_currently\_the\_best\_method\_to\_render\_roads/
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
  * https://www.russianspaceweb.com/spiral\_orbiter\_design.html
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
* put parameters like max-height, power, specular, radius in a configuration file
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
* improve performance of quaternions (see fastmath implementation)
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
