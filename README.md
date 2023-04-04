# Space Flight Simulator 2025

This is a work in progress.
Aim is to simulate take off, space station docking, and moon landing with a futuristic space plane.
Requires OpenGL 4.5.

# Installation
* Only tested on Debian 11
* Install Java, LWJGL2, and ImageJ: `sudo apt-get install openjdk-17-jre liblwjgl-java libij-java`
* [Install Clojure 1.11](https://clojure.org/guides/install_clojure)

# Build
* Build Worley noise: `clj -T:build worley`
* Build blue noise: `clj -T:build bluenoise`
* Build atmosphere lookup tables: `clj -T:build atmosphere-lut`
* Download NASA Bluemarble data: `clj -T:build download-bluemarble`
* Download NOAA elevation data: `clj -T:build download-elevation`
* Extract elevation data: `clj -T:build extract-elevation`
* Convert map sectors into pyramid of tiles: `clj -T:build map-sectors`
* Convert elevation sectors into pyramid of tiles: `clj -T:build elevation-sectors`
* Convert tile pyramids into pyramid of cube maps: `clj -T:build cube-maps`
* Perform all build steps above: `clj -T:build`

# Run

* Run tests: `clj -M:test`
* Run the cloud cube: `clj -M etc/cloudcube.clj`
* Run the cloud prototype: `clj -M etc/cloudy.clj`
* Run the global cloud cover prototype: `clj -M etc/cover.clj`
* Run the planet prototype: `clj -M etc/planet.clj`

# External Links

* Simulators
  * [Orbiter 2016](https://github.com/mschweiger/orbiter)
  * [Rogue System](http://imagespaceinc.com/rogsys/)
  * [Flight of Nova](https://flight-of-nova.com/)
  * [Kerbal Space Program](https://www.kerbalspaceprogram.com/)
  * [Reentry](https://reentrygame.com/)
* Engines
  * [Skybolt Engine](https://github.com/Piraxus/Skybolt/) ([article](https://piraxus.com/2021/07/28/rendering-planetwide-volumetric-clouds-in-skybolt/))
  * [Oreon Engine](https://github.com/fynnfluegge/oreon-engine)
  * [Emulating double precision in Godot](https://godotengine.org/article/emulating-double-precision-gpu-render-large-worlds)
* LWJGL
  * [LWJGL 2](http://legacy.lwjgl.org/)
  * [LWJGL Wiki](http://wiki.lwjgl.org/)
  * [LWJGL key table](https://gist.github.com/Mumfrey/5cfc3b7e14fef91b6fa56470dc05218a)
* Publications
  * [Bruneton: Precomputed Atmospheric Scattering](https://hal.inria.fr/inria-00288758/document)
  * [Bouthors: Interactive multiple anisotropic scattering in clouds](https://hal.inria.fr/file/index/docid/333007/filename/clouds.pdf)
  * [Pettersson: Real-time rendering and dynamics of sparse voxel octree clouds](https://lup.lub.lu.se/luur/download?func=downloadFile&recordOId=9024774&fileOId=9024775)
  * [Nubis: Authoring Real-Time Volumetric Cloudscapes with the Decima Engine](https://www.guerrilla-games.com/read/nubis-authoring-real-time-volumetric-cloudscapes-with-the-decima-engine)
* Webpages
  * [NVidia article on atmospheric scattering](https://developer.nvidia.com/gpugems/gpugems2/part-ii-shading-lighting-and-shadows/chapter-16-accurate-atmospheric-scattering)
  * [Cascaded Shadow Maps](https://web.archive.org/web/20220526080455/https://dev.theomader.com/cascaded-shadow-mapping-1/)
  * [Normal vectors of ellipsoid](https://math.stackexchange.com/questions/2931909/normal-of-a-point-on-the-surface-of-an-ellipsoid/2931931)
  * [Clojure performance flame graphs](https://github.com/clojure-goes-fast/clj-async-profiler)
  * [How to calculate your horizon distance](https://darkskydiary.wordpress.com/2015/05/25/how-to-calculate-your-horizon-distance/)
  * [Java native interface example](https://www.baeldung.com/jni)
  * [Wedesoft blog](https://www.wedesoft.de/)
* Videos
  * [Coding Adventure: Atmosphere](https://www.youtube.com/watch?v=DxfEbulyFcY)
