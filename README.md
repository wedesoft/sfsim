# [SFsim25][1] [![GPL-3.0](https://img.shields.io/github/license/wedesoft/sfsim25)](https://www.gnu.org/copyleft/gpl.html) [![tipping jar](https://img.shields.io/badge/tipping%20jar-wedesoft%40getalby.com-yellow)](https://getalby.com/wedesoft)

This is a work in progress.
Aim is to simulate take off, space station docking, and moon landing with a futuristic space plane.
Requires OpenGL 4.5.

[![Rendering of volumetric clouds](https://i1.ytimg.com/vi/XTRftiO9tEQ/hqdefault.jpg)](https://www.youtube.com/watch?v=XTRftiO9tEQ)

[![Atmospheric scattering and planet level-of-detail rendering](https://i.ytimg.com/vi/Ce3oWQflYOY/hqdefault.jpg)](https://www.youtube.com/watch?v=Ce3oWQflYOY)

# Installation
* Only tested on Debian 11
* Install Java, LWJGL2, and ImageJ: `sudo apt-get install openjdk-17-jre liblwjgl-java libij-java`
* [Install Clojure 1.11](https://clojure.org/guides/install_clojure)

# Build
* Build Worley noise: `clj -T:build worley`
* Build Perlin noise: `clj -T:build perlin`
* Build blue noise: `clj -T:build bluenoise`
* Build cloud cover: `clj -T:build cloud-cover`
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
* Run the global cloud cover prototype: `clj -M etc/cover.clj`
* Run the planet prototype: `clj -M etc/planet.clj`
* Run main program displaying black window: `clj -M -m sfsim25.core`

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
  * [Yuksel et al.: Deep Opacity Maps](http://www.cemyuksel.com/research/deepopacity/)
  * [Bouthors: Interactive multiple anisotropic scattering in clouds](https://hal.inria.fr/file/index/docid/333007/filename/clouds.pdf)
  * [Pettersson: Real-time rendering and dynamics of sparse voxel octree clouds](https://lup.lub.lu.se/luur/download?func=downloadFile&recordOId=9024774&fileOId=9024775)
  * [Nubis: Authoring Real-Time Volumetric Cloudscapes with the Decima Engine](https://www.guerrilla-games.com/read/nubis-authoring-real-time-volumetric-cloudscapes-with-the-decima-engine)
* Webpages
  * [NVidia article on atmospheric scattering](https://developer.nvidia.com/gpugems/gpugems2/part-ii-shading-lighting-and-shadows/chapter-16-accurate-atmospheric-scattering)
  * [Cascaded Shadow Maps](https://web.archive.org/web/20220526080455/https://dev.theomader.com/cascaded-shadow-mapping-1/)
  * [improved Perlin noise](https://adrianb.io/2014/08/09/perlinnoise.html)
  * [Normal vectors of ellipsoid](https://math.stackexchange.com/questions/2931909/normal-of-a-point-on-the-surface-of-an-ellipsoid/2931931)
  * [Clojure performance flame graphs](https://github.com/clojure-goes-fast/clj-async-profiler)
  * [How to calculate your horizon distance](https://darkskydiary.wordpress.com/2015/05/25/how-to-calculate-your-horizon-distance/)
  * [Java native interface example](https://www.baeldung.com/jni)
  * [Earth explorer data](https://earthexplorer.usgs.gov/)
  * [Wedesoft blog](https://www.wedesoft.de/)
* Videos
  * [Coding Adventure: Atmosphere](https://www.youtube.com/watch?v=DxfEbulyFcY)

  [1]: https://github.com/wedesoft/sfsim25
