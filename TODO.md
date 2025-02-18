# TODO
* define wheel positions in Blender
* gear/terrain physics with stationary Earth (ground collisions)
* good position of wheels for takeoff (with low fuel) and landing
* animate wheel rotation and suspension, bake gear animation and name actions the same
* sound effects
* takeoff physics
  * atmospheric flight with stationary atmosphere in rotating coordinate system
  * rotating Earth coordinate system (centrifugal and coriolis force),
  * space flight physics with resting coordinate system
  * implement two-step Runge-Kutta wrapper alignment
  * docking physics
  * moon landing physics
* Check out poliastro and hapsira
* use components and core.async for physics and loading of data, rendering main thread as component?
* fix problem with resolution of neighbouring tiles in planetary cubemap
* hover thruster locations
* shift and update opacity and planet shadow maps for improved performance
* visualise prototype vehicle and wheels
* quads with runway have to be flat (no saddle, same height for corner points)? increase all tessellation counts?
  deferred decals for rendering runway, runway lights, https://aerosavvy.com/airport-lights
* introduce variation to cloud height
* [lunar elevation map](https://wms.lroc.asu.edu/lroc/view_rdr/WAC_CSHADE)
* .jpg -> .day.jpg
* GLL parsing https://pure.uva.nl/ws/files/36086100/Thesis.pdf
* cloud shadow flickering at large distance?
* animate flaps
* space station model, station lights
* launch complex, station lights
* compute earth barycenter and sun in separate thread (use future)
* microtexture for normal map, microtextures, bump maps
* shadows and opacity maps are set up in three places (search :sfsim.opacity/shadows)
* pack more textures into one and then try one object casting shadow on another (pack object shadow maps into one?)
* separate atmosphere from environmental shadow code, setup-shadow-matrices support for no environmental shadow,
  overall-shading with object shadows only, aggregate shadow-vars with scene-shadows?
* integrate object shadows into direct light shader and maybe make template function for shadows which can be composed,
  use multiplication of local shadow map and planet+cloud shadows?
* https://lup.lub.lu.se/student-papers/search/publication/8893256
  Scattering approximation function
* add object radius to object?
* dted elevation data: https://gdal.org/drivers/raster/dted.html
  gmted maybe: https://topotools.cr.usgs.gov/gmted_viewer/viewer.htm
* read lwjgl book: https://lwjglgamedev.gitbooks.io/3d-game-development-with-lwjgl/content/
* adapt shadow map size to object distance
* add amplifier for ambient phong lighting which seems to be too dark, add earth light
* concept for bundling shaders with uniform setting methods?
* shadow and opacity map with different resolution for spaceship
* use add-watch?
* render cockpit and stencil/mask, then render planet, https://open.gl/depthstencils
* cockpit and scene rendering looking downward so that horizon is in upper half of screen
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
* cockpit
  * top:
    warnings (status display)
    autopilot on/off, autothrottle (autopilot speed), angle of attack and bank hold (including reverse), roll reversal button
    autopilot kill rotation, prograde, retrograde, orbit normal +/-, radial in/out
    rcs mode (off, rotation, translation)
    aircraft flight control surfaces: off/pitch/on
  * main:
    cabin light, panel light
    2 mfds
    apu on/off
    airlock doors (three state), chamber pressure, bay door (open, close, stop)
    light switch: nav, beacon, strobe
    engine, thrust (main/hover), rcs
    undock
    fuel display (main, rcs, apu), oxygen display
    hover doors switch + status
    gear up/down switch + status
    spoilers
    radiator deploy + status
    coolant temperature
    no flaps
  * mfd:
    yaw, bank, pitch acceleration and velocity indicators
    horizon hsi, height, heading, variometer, speed, ils height, nominal speed
    vor (use heading)
    nav frequencies, dock, vtol freq
    dock (angles, offset to path, distance, approach speed, x/y speed)
    camera
    map of earth/moon
    hull temperature
    align orbit plane
    orbit
    transfer: encounter, translunar orbit, insertion
    ascent profile
    reentry profile
  * engine thrust lever (main, hover)
    hover door
  * bottom
    flightstick (yaw/pitch/roll), trim wheel
    fuel lines open/close (lox, main), external pressure online
    life support
* https://blog.kuula.co/virtual-tour-space-shuttle
* how to render with shadow and cloud textures
* make cockpit with Blender
* test for render-triangles
* create windows using blending
  https://www.reddit.com/r/opengl/comments/10rwgy7/what\_is\_currently\_the\_best\_method\_to\_render\_roads/
* use 1-channel png for water?
* constant texel size across opacity cascade to prevent step in opacity?
* make cloud prototype more modular, separate cloud\_shadow and transmittance\_outer,
* amplify glare? appearance of sun? s2016-pbs-frostbite-sky-clouds-new.pdf page 28
* powder sugar effect https://progmdong.github.io/2019-03-04/Volumetric\_Rendering/
  [combined Beers and powder function](https://www.youtube.com/watch?v=8OrvIQUFptA)
  https://www.youtube.com/watch?v=Qj\_tK\_mdRcA
* add exceptions for all OpenGL stuff
* hot spots for map
* use Earth explorer data: https://earthexplorer.usgs.gov/
* use GMTED2010 or STRM90 elevation data:
  * https://topotools.cr.usgs.gov/gmted\_viewer/viewer.htm
  * https://www.eorc.jaxa.jp/ALOS/en/dataset/aw3d\_e.htm
  * https://www.eorc.jaxa.jp/ALOS/en/dataset/aw3d30/aw3d30\_e.htm
* how to render water, waves, https://www.shadertoy.com/view/Ms2SD1
* render stars
  * Skydome: counter-clockwise front face (GL11/glFrontFace GL11/GL\_CCW) (configuration object)
  * Skydome scaled to ZFAR * 0.5
  * no skydome and just stars as pixels?
* glTextureStorage2D levels correct for mipmaps?
* when building maps put intermediate files into a common subdirectory (tmp?)
* uniform random offsets for Worley noises to generate different cloud cover for each game
* ground\_radiance assumes sun brightness of one -> use one everywhere?
* use different normal for ground\_radiance? needs to be parallel to radius vector?
* only render sun glare when sun is above horizon, use single (normalised?) color from transmittance
* extract functions from prototype
* render building on top of ground
* put parameters like max-height, power, specular, radius in a configuration (edn?) file
* find water land mask data: https://lpdaac.usgs.gov/products/mod44wv006/
* night-time textures
* improve rendering of sun
* render moonlight and moon
* organize fixtures using subdirectories
* is all planet rendering represented as a quad-tree?
* normal maps
* You need to ensure that the ByteBuffer passed to stbtt\_InitFont is not garbage collected
* use data-centric APIs
* use glTexSubImage2D?
* use ZGC (short pause garbage collector for Java)
* improve performance of quaternions (see fastmath implementation)
* Get scale-image to work on large images
* use short integers for normal vector textures?
* NASA docking system, soft-dock, hard-dock, https://www.youtube.com/watch?v=dWYpVfhvsak
* EF2000 like cockpit controls (quick access views) ctrl+a,b,c,...? ctrl+arrow?
* EF2000 like gear suspension
* planet, moon position (check Orbiter 2016 code)
* blinking beacon/position lights
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
* no need to adjust MFDs during critical parts of the mission
* https://github.com/HappyEnte/DreamChaser
* HDR bloom (separable convolution)
  https://learnopengl.com/Advanced-Lighting/Bloom
  https://learnopengl.com/Guest-Articles/2022/Phys.-Based-Bloom
  http://blog.chrismdp.com/2015/06/how-to-quickly-add-bloom-to-your-engine/
  HDR rendering
  Tone mapping L/(1+L)
  Add blurred overflow
* multisampling
* windows (blending, reflections), greyscale cameras, MFDs
* frame buffer objects for non-blocking data transfer
* point sprites for stars, atmospheric flicker using sprite arrays
* point light sources
* view matrix, model matrix stack, light position
* thrusters
* proper lift/drag ratio for high Reynolds numbers
* threads for rendering, simulation, and loading of data
* event-based radio (triggers as in Operation Flashpoint)
* missions and high scores
* beep-beep sound, paraglider audio?
* uniform distribution on sphere http://marc-b-reynolds.github.io/distribution/2016/11/28/Uniform.html
* fluid dynamics on GPU: https://developer.nvidia.com/gpugems/gpugems/part-vi-beyond-triangles/chapter-38-fast-fluid-dynamics-simulation-gpu
* introduction to fluid dynamics: https://shahriyarshahrabi.medium.com/gentle-introduction-to-fluid-simulation-for-programmers-and-technical-artists-7c0045c40bac
* fluid dynamics tutorial: http://graphics.cs.cmu.edu/nsp/course/15-464/Fall09/papers/StamFluidforGames.pdf
* bake normal maps: https://www.youtube.com/watch?v=dPbrhqqrZck
