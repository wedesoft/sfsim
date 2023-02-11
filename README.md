# Space Flight Simulator 2025

This is a work in progress. Requires OpenGL 4.5.

# Installation

* Install Java, LWJGL2, and ImageJ: `sudo apt-get install openjdk-17-jre liblwjgl-java libij-java`
* [Install Clojure](https://clojure.org/guides/install_clojure)

# Run

* Run tests: `clj -M:test`
* Build Worley noise: `clj -T:build worley`
* Build blue noise: `clj -T:build bluenoise`
* Build atmosphere lookup tables: `clj -T:build atmosphere-lut`
* Download NASA Bluemarble data: `clj -T:build download-bluemarble`
* Convert map sectors into pyramid of tiles: `clj -T:build map-sectors`
* Convert elevation sectors into pyramid of tiles: `clj -T:build elevation-sectors`
* Convert tile pyramids into pyramid of cube maps: `clj -T:build cube-maps`
* Run the cloud prototype: `clj -M etc/cloudy.clj`

# External Links

* [Orbiter 2016](https://github.com/mschweiger/orbiter)
* [Rogue System](http://imagespaceinc.com/rogsys/)
* [Flight of Nova](https://flight-of-nova.com/)
* [Kerbal Space Program](https://www.kerbalspaceprogram.com/)
* [Java native interface example](https://www.baeldung.com/jni)
* [Skybolt Engine](https://github.com/Piraxus/Skybolt/) ([article](https://piraxus.com/2021/07/28/rendering-planetwide-volumetric-clouds-in-skybolt/))
* [Oreon Engine](https://github.com/fynnfluegge/oreon-engine)
* [LWJGL 2](http://legacy.lwjgl.org/)
* [LWJGL Wiki](http://wiki.lwjgl.org/)
* [LWJGL key table](https://gist.github.com/Mumfrey/5cfc3b7e14fef91b6fa56470dc05218a)
* [OpenGL projection matrix](https://www.scratchapixel.com/lessons/3d-basic-rendering/perspective-and-orthographic-projection-matrix/opengl-perspective-projection-matrix)
* [NVidia article on atmospheric scattering](https://developer.nvidia.com/gpugems/gpugems2/part-ii-shading-lighting-and-shadows/chapter-16-accurate-atmospheric-scattering)
* [Coding Adventure: Atmosphere](https://www.youtube.com/watch?v=DxfEbulyFcY)
* [Normal vectors of ellipsoid](https://math.stackexchange.com/questions/2931909/normal-of-a-point-on-the-surface-of-an-ellipsoid/2931931)
* [Clojure performance flame graphs](https://github.com/clojure-goes-fast/clj-async-profiler)
* [How to calculate your horizon distance](https://darkskydiary.wordpress.com/2015/05/25/how-to-calculate-your-horizon-distance/)
* [Reversed-z rendering in OpenGL](https://nlguillemot.wordpress.com/2016/12/07/reversed-z-in-opengl/)
* [Reversed-z rendering precision](https://developer.nvidia.com/content/depth-precision-visualized)
* [Incanter interpolation API](https://incanter.github.io/incanter/interpolation-api.html)
* [Bruneton: Precomputed Atmospheric Scattering](https://hal.inria.fr/inria-00288758/document)
* [Bouthors: Interactive multiple anisotropic scattering in clouds](https://hal.inria.fr/file/index/docid/333007/filename/clouds.pdf)
* [Cascaded Shadow Maps](https://dev.theomader.com/cascaded-shadow-mapping-1/)
* [Emulating double precision in Godot](https://godotengine.org/article/emulating-double-precision-gpu-render-large-worlds)
