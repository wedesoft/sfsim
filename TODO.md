# TODO
* use 2d blue noise for sampling offsets
* determine when atmosphere ray leaves Earth's shadow
* problem: clouds still disappear when going to space
* offscreen render clouds (with atmosphere in front) with alpha channel
* render planet without clouds
* apply cloud texture to higher resolution picture (upscale and alpha-blend)
* horizon still bright even under dark clouds (attenuation\_track needs to take into account cloudiness)
* offset cloud sampling with blue noise
* Fractal Worley noise (octaves added together?)
  https://www.shadertoy.com/view/XdGyRc
  https://gamedev.stackexchange.com/questions/197861/how-to-handle-octave-frequency-in-the-perlin-noise-algorithm
* global cloud map (skybox?)
* add cloud shadow computation to ground radiance function
* 3D procedural noise: simplex noise with domain warping, Google search "opengl procedural volumetric clouds"
  https://piraxus.com/2021/07/28/rendering-planetwide-volumetric-clouds-in-skybolt/
  https://github.com/Piraxus/Skybolt/
  https://lup.lub.lu.se/student-papers/record/8893256/file/8893258.pdf    description of noise functions
  https://www.researchgate.net/publication/224688956_Generating_and_Rendering_Procedural_Clouds_in_Real_Time_on_Programmable_3D_Graphics_Hardware    describes octaves of noise
  https://www.mdpi.com/2073-8994/10/4/125/pdf-vor
* problem: white artifacts when sun is in zenith
* transmittance-weighted average cloud distance -> correct atmospheric scattering
* blue noise dithering
* in branch: change ray\_scatter\_\* and transmittance\_\* to accept direction and scale factors?
* determine and increment level of detail index for mipmaps in cloud\_track and cloud\_track\_base
* keyboard shortcuts for cloud parameters
* http://advances.realtimerendering.com/s2015/index.html
  * increase stepsize between clouds (also only sample low-resolution noise)
  * multiple levels of Worley and Perlin noise in channels of 3D texture
  * use lower-resolution cloud rendering
  * render clouds with low resolution, sample with different z-offsets and blur
  * offset sample start using blue noise
  * compute shadows on ground with quarter resolution
  * add flat cirrus clouds
  * move different levels of noise to create moving and shape-changing clouds
* remove lowp, mediump, highp
* plan work
* make is-image checker less strict (threshold relative sum of difference?) or add new checker roughly-image
* does ray\_scatter\_atmosphere need to be multiplied with transmittance\_cloud?
* add clouds to atmospheric and planetary shader
* cloud density function using 3D textures and vertical threshold curves
* article about TDD and OpenGL (rendering offscreen image and using probe shaders)
* article about clouds
* 3D Worley noise 256^3 (using random points, using cells, different frequencies) and Simplex noise
* how to render shadows on planet surface and in atmosphere; shadow maps or shadow volumes (bruneton chapter 5)?
* how to render clouds and cloud shadows?
* how to render waves
* how to render stars
* ground\_radiance assumes sun brightness of one -> use one everywhere?
* use different normal for ground\_radiance? needs to be parallel to radius vector?
* is an above-horizon boolean needed for the light-direction when computing ray-scatter and ray-scatter-forward?
* elevation\_to\_index should use elevation value between -pi/2 and pi/2
* only render sun glare when sun is above horizon, use single (normalised?) color from transmittance
* extract functions from prototype
* indices for planet patches and atmosphere projection plane should be the same
* put parameters like max-height, power, specular, radius, polar-radius in a configuration file
* \_ -> - in shader file names
* tile-size -> tilesize
* light -> light-direction
* radius1, radius2 -> radius, polar-radius
* use Earth explorer data: https://earthexplorer.usgs.gov/
* use GMTED2010 data: https://topotools.cr.usgs.gov/gmted\_viewer/viewer.htm
* find water land mask data: https://lpdaac.usgs.gov/products/mod44wv006/
* night-time textures
* improve rendering of sun
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
