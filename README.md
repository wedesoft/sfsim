# [sfsim][1] [![EPL-1.0](https://img.shields.io/badge/License-EPL_1.0-orange)](https://www.eclipse.org/legal/epl/epl-v10.html) [![Build Status](https://github.com/wedesoft/sfsim/actions/workflows/sfsim.yml/badge.svg)](https://github.com/wedesoft/sfsim/actions/workflows/sfsim.yml) [![Donate](https://img.shields.io/badge/Donate-The%20Water%20Project-green)](https://thewaterproject.org/)

This is a work in progress.
Aim is to simulate take off, space station docking, and moon landing with a futuristic space plane.
Requires OpenGL 4.5 (glClipControl for reversed-z rendering)
See [sfsim homepage][1] for more details.

[![Aerodynamic prototype](https://i.ytimg.com/vi/38FGT7SWVh0/hqdefault.jpg)](https://www.youtube.com/watch?v=38FGT7SWVh0)

# Installation

* Tested on Debian 13 and Windows 11
* Install [JDK 25 Deb for Linux](https://www.oracle.com/uk/java/technologies/downloads/) or [JDK 25 MSI for Windows](https://adoptium.net/temurin/releases)
* [Install Clojure 1.12](https://clojure.org/guides/install_clojure)
* Download [Packr](https://github.com/libgdx/packr) Jar file for creating Windows executable
* Install [NSIS](https://nsis.sourceforge.io/) for building Windows installer

# Get Code for GNU/Linux

```Shell
git clone https://github.com/wedesoft/sfsim.git
cd sfsim
git checkout main
```

## Get Code for Windows

```Shell
git clone https://github.com/wedesoft/sfsim.git
cd sfsim
git checkout windows
```

## Install JoltPhysics

Get [JoltPhysics](https://github.com/jrouwe/JoltPhysics) 5.5.0 and build it as follows.
Note you might have to install glslc if you already have Vulkan installed.

### GCC/Linux

```Shell
cd Build
./cmake_linux_clang_gcc.sh Release g++ -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DDOUBLE_PRECISION=ON -DDEBUG_RENDERER_IN_DEBUG_AND_RELEASE=OFF -DPROFILER_IN_DEBUG_AND_RELEASE=OFF -DUSE_AVX2=OFF -DUSE_LZCNT=OFF -DUSE_TZCNT=OFF -DUSE_F16C=OFF -DUSE_FMADD=OFF
cd Linux_Release
make -j `nproc`
sudo make install
cd ../..
```

### MinGW/Windows

```Shell
cd Build
./cmake_windows_mingw.sh Release -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DDOUBLE_PRECISION=ON -DDEBUG_RENDERER_IN_DEBUG_AND_RELEASE=OFF -DPROFILER_IN_DEBUG_AND_RELEASE=OFF -DINTERPROCEDURAL_OPTIMIZATION=OFF -DUSE_AVX2=OFF -DUSE_LZCNT=OFF -DUSE_TZCNT=OFF -DUSE_F16C=OFF -DUSE_FMADD=OFF
cmake --build MinGW_Release -j `nproc`
cmake --install MinGW_Release --prefix /usr/local
cd ..
```

# Build

* Build Jolt wrapper library (tested with Linux and Windows/MinGW): `make jolt`
* Download space ship model: `clj -T:build download-spaceship`
* Create content of data folder (Note that building everything takes days! You can take the data folder of a Steam playtest build instead unless latest software requires a future data release on Steam):
  * Volumetric clouds
    * Build Worley noise: `clj -T:build worley`
    * Build Perlin noise: `clj -T:build perlin`
    * Build blue noise: `clj -T:build bluenoise`
    * Build cloud cover: `clj -T:build cloud-cover`
  * Celestial motion data
    * Planetary motions: `clj -T:build download-ephemeris`
    * Lunar reference frame: `clj -T:build download-reference-frames`
    * Lunar motion: `clj -T:build download-lunar-pck-file`
  * Earth cube map tiles (this takes days to build)
    * Download NASA Bluemarble data: `clj -T:build download-bluemarble`
    * Download NASA Blackmarble data: `clj -T:build download-Blackmarble`
    * Download NOAA elevation data: `clj -T:build download-elevation`
    * Download NASA JPL ephemeris data: `clj -T:build download-ephemeris`
    * Extract elevation data: `clj -T:build extract-elevation`
    * Convert day map sectors into pyramid of tiles: `clj -T:build map-sectors-day`
    * Convert night map sectors into pyramid of tiles: `clj -T:build map-sectors-night`
    * Convert elevation sectors into pyramid of tiles: `clj -T:build elevation-sectors`
    * Convert tile pyramids into pyramid of cube maps: `clj -T:build cube-maps`
  * Lunar data (not used yet)
    * Download Moon color images: `clj -T:build download-lunar-color`
    * Download Moon elevation: `clj -T:build download-lunar-elevation`
  * Atmospheric lookup tables (this takes hours to compute)
    * Build atmosphere lookup tables: `clj -T:build atmosphere-lut`
    * Perform all build steps above: `clj -T:build all`
* Enable integration tests (requiring results of above build steps): `touch .integration`

## Further Build Steps under Windows

* Update version number in `src/clj/sfsim/version.clj`
* make sure Jolt wrapper library was built: `make jolt`
* Delete target and out-windows directories (do not omit this step otherwise it can generate a broken build)
* Build JAR file: `clj -T:build uber`
* Create Windows executable: `java -jar packr-all-4.0.0.jar scripts/packr-config-windows.json` (delete out-windows folder first)
* Upload to Steam: `sdk\tools\ContentBuilder\builder\steamcmd.exe +login <account_name> <password> +run_app_build C:\Users\....\sfsim\scripts\sfsim_playtest_windows.vdf +quit`

## Further Build Steps under GNU/Linux

* Update version number in `src/clj/sfsim/version.clj`
* make sure Jolt wrapper library was built: `make jolt`
* Delete target and out-linux directories (do not omit this step, otherwise it can generate a broken build)
* Build JAR file: `clj -T:build uber`
* Create Linux executable: `java -jar packr-all-4.0.0.jar scripts/packr-config-linux.json` (delete out-linux folder first)
* Upload to Steam: `sdk/tools/ContentBuilder/builder_linux/steamcmd.sh +login <account_name> <password> +run_app_build /home/..../sfsim/scripts/sfsim_playtest_linux.vdf +quit`

# Lint

* [Install clj-kondo](https://github.com/clj-kondo/clj-kondo/blob/master/doc/install.md)
* Run `clj-kondo --lint src/clj/sfsim`

# Run

* Run tests (recommended to use xvfb-run): `xvfb-run clj -M:test`
* Run test for specific module (rendering for example): `xvfb-run clj -M:test sfsim.t-render`
* Run the global cloud cover prototype: `clj -M etc/cover.clj`
* Run main program: `clj -M:run`

# Converting Blender file to GLB

If you want to modify the [spacecraft model](https://github.com/wedesoft/blender/tree/master/venturestar), you need to edit the Blender model and convert it to GLB again.
The Blender model can be converted to GLB as follows:

* apply all modifiers in Blender (first to last)
* delete lights and camera
* delete reference images
* delete invisible objects used for object differences
* export to GLB with baking animations enabled (animation mode "NLA track"!)
* exit Blender without overwriting model

# External Links

* Simulators
  * [Orbiter 2016](https://github.com/mschweiger/orbiter)
  * [Open Orbiter Sim](https://openorbiter.space/)
  * [Reentry](https://reentrygame.com/)
  * [Kerbal Space Program](https://www.kerbalspaceprogram.com/)
  * [Kitten Space Agency](https://kittenspaceagency.wiki.gg/)
  * [Flight of Nova](https://flight-of-nova.com/)
  * [Lunar Flight](http://www.shovsoft.com/lunarflight/)
  * [Eagle Lander 3D](http://eaglelander3d.com/)
  * [Tungsten Moon](https://tungstenmoon.com/)
  * [Rogue System](http://imagespaceinc.com/rogsys/)
  * [Endless Abyss](https://smoothiegames.itch.io/endless-abyss)
  * [UniVoyager](https://www.univoyager.com/)
  * [Final Orbit](https://store.steampowered.com/app/4319800/Final_Orbit/)
  * [Space Nerds in Space](https://smcameron.github.io/space-nerds-in-space/)
  * [Alliance Space Guard](https://alliancespaceguard.com/)
  * [Pioneer Space Sim](https://pioneerspacesim.net/)
* Engines
  * [Jolt Physics](https://github.com/jrouwe/JoltPhysics)
  * [Project Chrono physics engine](https://projectchrono.org/)
  * [JSBSim](https://github.com/JSBSim-Team/jsbsim)
  * [Skybolt Engine](https://github.com/Piraxus/Skybolt/) ([article](https://piraxus.com/2021/07/28/rendering-planetwide-volumetric-clouds-in-skybolt/))
  * [Oreon Engine](https://github.com/fynnfluegge/oreon-engine)
  * [Emulating double precision in Godot](https://godotengine.org/article/emulating-double-precision-gpu-render-large-worlds)
  * [Open Space Program](https://github.com/TheOpenSpaceProgram/osp-magnum)
* LWJGL
  * [LWJGL](https://www.lwjgl.org/)
  * [LWJGL Wiki](https://github.com/LWJGL/lwjgl3-wiki/wiki)
  * [LWJGL key table](https://gist.github.com/Mumfrey/5cfc3b7e14fef91b6fa56470dc05218a)
* Publications
  * [Bruneton: Precomputed Atmospheric Scattering](https://hal.inria.fr/inria-00288758/document)
  * [Yuksel et al.: Deep Opacity Maps](http://www.cemyuksel.com/research/deepopacity/)
  * [Bouthors: Interactive multiple anisotropic scattering in clouds](https://hal.inria.fr/file/index/docid/333007/filename/clouds.pdf)
  * [Pettersson: Real-time rendering and dynamics of sparse voxel octree clouds](https://lup.lub.lu.se/luur/download?func=downloadFile&recordOId=9024774&fileOId=9024775)
  * [Nubis: Authoring Real-Time Volumetric Cloudscapes with the Decima Engine](https://www.guerrilla-games.com/read/nubis-authoring-real-time-volumetric-cloudscapes-with-the-decima-engine)
  * [Hull: Fundamentals of Airplane Flight Mechanics](https://aerostarsolutions.wordpress.com/wp-content/uploads/2011/10/fundmentals_of_airplane_flight_mechanics.pdf)
* Webpages
  * [NVidia article on atmospheric scattering](https://developer.nvidia.com/gpugems/gpugems2/part-ii-shading-lighting-and-shadows/chapter-16-accurate-atmospheric-scattering)
  * [Cascaded Shadow Maps](https://web.archive.org/web/20220526080455/https://dev.theomader.com/cascaded-shadow-mapping-1/)
  * [Shadow mapping improvements](http://www.opengl-tutorial.org/intermediate-tutorials/tutorial-16-shadow-mapping/)
  * [improved Perlin noise](https://adrianb.io/2014/08/09/perlinnoise.html)
  * [Normal vectors of ellipsoid](https://math.stackexchange.com/questions/2931909/normal-of-a-point-on-the-surface-of-an-ellipsoid/2931931)
  * [Clojure performance flame graphs](https://github.com/clojure-goes-fast/clj-async-profiler)
  * [How to calculate your horizon distance](https://darkskydiary.wordpress.com/2015/05/25/how-to-calculate-your-horizon-distance/)
  * [Java native interface example](https://www.baeldung.com/jni)
  * [Earth explorer data](https://earthexplorer.usgs.gov/)
  * [Simple Physics-based Flight Simulation with C++](https://www.jakobmaier.at/posts/flight-simulation/)
  * [Wedesoft blog](https://www.wedesoft.de/)
* Videos
  * [Coding Adventure: Atmosphere](https://www.youtube.com/watch?v=DxfEbulyFcY)
  * [Bake normal maps](https://www.youtube.com/watch?v=dPbrhqqrZck)
* Clojure
  * [cljstyle](https://github.com/greglook/cljstyle) (style checking)
  * [clj-kondo](https://github.com/clj-kondo/clj-kondo) (static code analysis)
  * [clj-async-profiler](https://github.com/clojure-goes-fast/clj-async-profiler) (flame graph profiler)
  * [antq](https://github.com/liquidz/antq) (detect available updates for dependencies)
  * [nREPL](https://nrepl.org/) server and [REPL-y](https://github.com/trptcolin/reply) client
  * [rebel-readline](https://github.com/bhauman/rebel-readline) REPL with colors :)
* Data
  * [NASA CGI moon toolkit](https://svs.gsfc.nasa.gov/4720/)

[1]: https://wedesoft.github.io/sfsim/
