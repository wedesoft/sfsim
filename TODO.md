# TODO
* generate cubemap for global cloud cover
  * function to compute curl vector on sphere given a 3D function
  * create octaves of 3D Worley noise for clouds and curl (difference of two times sinus profile?)
  * determine 3D curl vectors on sphere surface by rendering 6 textures of cube map
  * perform lookups in octaves of cloud noise to generate cube map of cloud cover
  * write article
* use strings instead of keywords for uniform-setting methods?
* Github actions
* try to install and use LWJGL3 from Maven
* increase lod in noise-octaves?
* Perlin noise?
* try whether EJML has better performance https://ejml.org/
* [combined Beers and powder function](https://www.youtube.com/watch?v=8OrvIQUFptA)
* article:
  * [coding adventure](https://www.youtube.com/watch?v=4QOcCGI6xOU)
  * [how big aaa studios render clouds](https://www.youtube.com/watch?v=Qj_tK_mdRcA)
  * https://www.guerrilla-games.com/read/the-real-time-volumetric-cloudscapes-of-horizon-zero-dawn
  * cloud probability map: https://neo.gsfc.nasa.gov/view.php?datasetId=MODAL2_M_CLD_FR&date=2022-12-01
  * example cloud cover: https://earthobservatory.nasa.gov/ContentFeature/BlueMarble/Images/cloud_combined_2048.jpg
* step in atmosphere when passing cloud-top
* limit sampled interval depending on start of interval
* better computation of lod, how is cloud\_max\_step (cms) still used?
* render to texture with alpha channel
* horizon still bright even under dark clouds (attenuation\_track needs to take into account cloudiness)
* use shadow map of planet in cloud\_shadow?
* integrate with planetary prototype etc/planet.clj
* seapare transmittance\_outer call from cloud\_shadow
* does opacity fragment shader need to limit offsets to bounding box?
* prototype planetary cloud rendering using cascaded deep opacity maps
* integration test for cascaded deep opacity map
* use shadow map for terrain and clouds
* convert\_1d\_index
* does lookup\_3d need to use textureLod?
* shadow lookup: use convert 2d index
* test opacity offsets with constant density cloud, use clip\_shell\_intersections
* use maximum possible cloud self-shadow length?
* clouds: blue noise offsets for opacity map? use extra shadow map?
* use rgb for scattering and a for transmittance when creating low-resolution cloud image?
* write article about cloud rendering
* use compute shaders? in parallel with graphics?
  GL\_COMPUTE\_SHADER type, glDispatchCompute 1024x1024x64 items (product at least 1024)
* make is-image checker less strict (threshold relative sum of difference?) or add new checker roughly-image
* integration test planet shader with non-trivial lookup tables? convert prototype to tested code
* clouds
  * add clouds to atmospheric and planetary shader
  * ozone absorption? s2016-pbs-frostbite-sky-clouds-new.pdf page 20
  * attenuation of far clouds, transmittance-weighted average cloud distance -> correct atmospheric scattering
  * exponentially reduce and or limit samples with distance or when viewing from space and do level of detail
  * determine and increment level of detail index for mipmaps in cloud\_track
  * increase stepsize between clouds (also only sample low-resolution noise)
  * multiple levels of Worley and Perlin noise in channels of 3D texture
  * offscreen render clouds with low resolution (and atmosphere in front) with alpha channel, use blue noise z-offsets and blur
  * apply cloud texture to higher resolution picture (upscale and alpha-blend)
  * add flat cirrus clouds
  * move different levels of noise to create moving and shape-changing clouds
  * global cloud map (skybox?)
  * article about atmosphere rendering with clouds
* appearance of sun? s2016-pbs-frostbite-sky-clouds-new.pdf page 28
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
* use Earth explorer data: https://earthexplorer.usgs.gov/
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
