---
title: Frequently Asked Questions
layout: page
permalink: /faq
---

### When will the game be released?

I hope to release a demo in 2025, but for the full game there is no fixed release date yet since there is still some work left to do.
You can [wishlist it on Steam][1] to get notified of the release.

### What operating systems are supported?

Currently GNU/Linux and Microsoft Windows are supported.
Steam Deck has not been tested, but the graphics are likely to be too demanding for it.

### What are the system requirements?

You need at least a graphics card with passmark 10000 such as the Nvidia GeForce GTX 1060.
The hardware requirements might still change, but the main bottleneck are the volumetric clouds.

Also ideally you will use a joystick or a game controller for controlling the space craft.

### What features will the full game have?

Planned features are vertical launch, space station docking, moon landing, atmospheric reentry, and landing on a runway.
The space craft will need flight instruments to support this.

### Is the game free software?

Yes, the game is free and open source software ([EPL 1.0][3]).
The source code is [available on Github][2].
It makes use of [Clojure][4], [LWJGL][6], [Fastmath][5], and [Jolt Physics][9].
The celestical positions are implemented according to [Skyfield][8].
Note that building the project assets takes several days on a modern computer.

### Who made the music?

The free music is made by [Andrewkn][7]!
Andrewkn's main styles are ambient, сhillout, drone, and cinematic.

### Will there be support for virtual reality headsets?

At the moment, VR support is not planned.

### Where is the Earth data from?

The game uses data from [NASA Blue Marble][10], [NASA Night Lights][11], and [NOAA elevation data][12].

### Do you use realistic positions of Sun, Moon, and Earth?

Yes, the game uses [ephemeris data from NASA JPL][13].
The relevant interpolation software was translated from [Skyfield][14].

### Where is the spacecraft model from?

The spacecraft model was created using [Blender][15] and is inspired by the real-life Venturestar single-stage-to-orbit project which was never launched.

### When did development of this game start?

The first commit to the [sfsim repository][16] happened in September 2020.
There also is prior work on model rendering in 2017.
Also I made two attempts at implementing a physics engine using sequential impulses in 2017 and 2020.

[1]: https://store.steampowered.com/app/3687560/sfsim/
[2]: https://github.com/wedesoft/sfsim
[3]: https://www.eclipse.org/legal/epl/epl-v10.html
[4]: https://clojure.org/
[5]: https://github.com/generateme/fastmath
[6]: https://www.lwjgl.org/
[7]: https://freesound.org/people/Andrewkn/
[8]: https://rhodesmill.org/skyfield/
[9]: https://jrouwe.github.io/JoltPhysics/
[10]: https://visibleearth.nasa.gov/collection/1484/blue-marble
[11]: https://earthobservatory.nasa.gov/features/NightLights/page3.php
[12]: https://www.ngdc.noaa.gov/mgg/topo/gltiles.html
[13]: https://ssd.jpl.nasa.gov/ftp/eph/planets/bsp/
[14]: https://rhodesmill.org/skyfield/
[15]: https://www.blender.org/
[16]: https://github.com/wedesoft/sfsim
