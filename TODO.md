# TODO
* in-scattering on a track inside the atmosphere
* water reflection
* create sub directories for shaders and fixtures
* test planet quad appearance using transmittance\_track and in-scattering
* remove heightcoord from tail of shader chain?
* how to render sun in atmosphere, space, and on top of planet
* how to render stars
* planet is an ellipsoid (not a sphere)
* combine atmospheric shader with planetary surface rendering, albedo should be 0.9 (snow)
* organize fixtures using subdirectories
* decompose atmospheric lookup table into background and glare around sun (or use higher-resolution lookup table?)
* is all planet rendering represented as a quad-tree?
* do not use precomputed first order scattering, refer to Bruneton's paper for a solution, subtract/add radial component?
* rendering of sunlight and moonlight
* separate atmospheric lookup into elevation/heading-dependent part and radial symmetric part around the sun?
* compute shadow in atmosphere
* use Earth explorer data: https://earthexplorer.usgs.gov/
* find water land mask data: https://lpdaac.usgs.gov/products/mod44wv006/
* night-time textures
* volumetric clouds or billboard clouds
* glTF/GLB file format, Assimp library, Java bindings https://github.com/kotlin-graphics/assimp, see https://poly.pizza/
* normal maps
* text rendering using bitmap fonts
* use data-centric APIs
* use glTexSubImage2D?
* use ZGC (short pause garbage collector for Java)
* redesign floating point math of height maps
* improve performance of quaternions (see clojure.core.matrix implementation)
* Get scale-image to work on large images
* Skydome: counter-clockwise front face (GL11/glFrontFace GL11/GL\_CCW) (configuration object)
* Skydome scaled to ZFAR * 0.5
* use short integers for normal vector textures?
* soft-dock, hard-dock
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
* 3D cockpit
* 3D moon rendering
* elevators, trains
* cables, pipes
* garbage
* advertising
* airport-like departure tables
* airlocks, walkways
* fans, fog, lighting
* bump maps
* render articulated objects with configuration
* suspension using spring damper elements
* mass matrix
* JIT compilation to speed it up
* compiled sfsim.scm not being found
* 3D model: Dream Chaser, Soyuz, PTK NP, https://www.thingiverse.com/thing:2565361
* music player, mp3 player
* video communication with screen in 3D cockpit
* autopilot programs: baseland, helicopter, hover/autoland, launch/deorbit, aerocapture/aerobrake, airspeed hold, attitude hold, altitude hold, heading alignment cylinder, VOR/ILS, eject, capture, base sync, station sync, dock
* on board displays, cameras, MFDs
* no need to adjust MFDs during critical parts of the mission
* https://github.com/HappyEnte/DreamChaser
* shadows (mask color pipeline output)
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
* create windows using blending
* thrusters
* proper lift/drag ratio for high Reynolds numbers
* draw points, lines, triangle strips, mfd for reentry and orbital mechanics
* detailed onboard systems (e.g. airlock, life support, auxiliary power units)
* threads for rendering, simulation, and loading of data
* event-based radio (triggers as in Operation Flashpoint)
* missions and high scores
* atmospheric rendering, clouds, water, elevation maps, fractal micro textures and maps
* beep-beep sound, paraglider audio?
