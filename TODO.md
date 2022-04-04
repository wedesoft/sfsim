# TODO
* ground\_radiance assumes sun brightness of one -> use one everywhere?
* rename sky\_or\_ground to is\_above\_horizon
* change shader functions to use sky boolean
* use different normal for ground\_radiance? needs to be parallel to radius vector?
* shader to check whether point-direction is in the sky or on the ground
* is an above-horizon boolean needed for the light-direction when computing ray-scatter and ray-scatter-forward?
* elevation\_to\_index should use elevation value between -pi/2 and pi/2
* only render sun glare when sun is above horizon
* add keyboard motion commands
* extract functions from prototype
* remove update-tree-parents code
* indices for planet patches and atmosphere projection plane should be the same
* put parameters like max-height, power, specular, radius, polar-radius in a configuration file
* \_ -> - in shader file names
* tile-size -> tilesize
* light -> light-direction
* radius1, radius2 -> radius, polar-radius
* use Earth explorer data: https://earthexplorer.usgs.gov/
* find water land mask data: https://lpdaac.usgs.gov/products/mod44wv006/
* night-time textures
* improve rendering of sun
* how to render clouds
* how to render shadows on planet surface
* how to render shadows in atmosphere
* how to render waves
* how to render stars
* render moonlight and moon
* organize fixtures using subdirectories
* is all planet rendering represented as a quad-tree?
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
