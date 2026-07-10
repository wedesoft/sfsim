---
title: FAQ
layout: page
permalink: /faq/
---

### When will the game be released?

I hope to release a demo in ~~2025~~ 2026, but for the full game there is no fixed release date yet since there is still some work left to do.
You can [wishlist it on Steam][1] to get notified when it gets released.
This also helps me reach more players by boosting visibility on Steam.
Note that a playtest build is [available on Steam][1].

### What features will the full game have?

Planned features are vertical launch, space station docking, moon landing, atmospheric reentry, and landing on a runway.
It is also planned to have flight instruments to support the different stages of flight.

### What operating systems are supported?

GNU/Linux and Microsoft Windows are supported.
You can also install the game on Steam Deck, but current compatibility on Steam Deck has not been tested recently.
Currently there is no plan to support macOS.

### Will there be multiplayer?

Sorry, there is no plan to support multiplayer.

### Is this game realistic or arcade?

The game aims to provide a realistic simulation of orbital mechanics, atmospheric flight, and celestial positions.

### What are the system requirements?

The hardware requirements might still change, but the main bottleneck is rendering of the volumetric clouds.
Here are a few test results:

* Steam Deck achieved 25 fps (1280x800 display, SteamOS)
* AMD Radeon RX 6700 XT achieved 50 fps (3440x1440 display, Debian)
* AMD Radeon RX 7600 achieved 50 fps (2560x1440 display, Debian)

I recommend a quad-core CPU with more than 2GHz and at least 8 GB of RAM.

The game requires 10 GB of disk space.

### What input devices are supported?

You need mouse control to navigate the menus.
The spacecraft can be controlled using keyboard or a game controller (or joystick).
The game also supports using multiple game controllers (e.g. joystick and throttle).
Force feedback is not supported.

### Will there be support for virtual reality headsets?

At the moment, VR support is not planned.

### What languages are supported?

Currently the game is available in English only.

### Can I play offline?

Yes, the game does not require an internet connection after installation.

### Is the game free software?

Yes, the game is free and open source software ([EPL-1.0][3]).
The source code is [available on GitHub][2].
Note that building the project assets takes several days on a modern computer.
However you can get the data folder from the latest Steam install instead.

### Will the game cost money?

No, currently it is planned to be free of charge.

### Who made the music?

The free ([CC-BY 4.0][17]) music is made by [Andrewkn][7]!
Andrewkn's main styles are ambient, сhillout, drone, and cinematic.

### What game engine does sfsim use?

*sfsim* does not use a game engine, but it does rely on several libraries.
It makes use of [Clojure][4], [LWJGL][6], [Fastmath][5], [Jolt Physics][9], and other libraries.
The celestial positions are implemented according to [Skyfield][8].

### Can I use the source code in my own project?

Yes, provided you comply with the terms of the [EPL-1.0][3].

The Eclipse Public License 1.0 is a weak copyleft open-source license. It permits you to modify and distribute the code, requires that changes to EPL-licensed code remain under the same license, and still allows proprietary use in larger combined works.

If your goal is only to examine the source code, no license is required. In other words, simply studying the code and independently reimplementing its ideas in your own project does not bind you to the license.

### Where is the Earth data from?

The game uses accurate data from [NASA Blue Marble][10], [NASA Night Lights][11], and [NOAA elevation data][12].

### Do you use realistic positions of Sun, Moon, and Earth?

Yes, the game uses [ephemeris data from NASA JPL][13].
The relevant interpolation software was translated from [Skyfield][14].

### Where is the spacecraft model from?

The spacecraft model was created using [Blender][15] and is inspired by the real-life Venturestar single-stage-to-orbit project which was never launched.

### When did development of this game start?

Development is fairly slow because it is a hobby.
The first commit to the [sfsim repository][16] happened in September 2020.
There is also prior work on model rendering in 2017.
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
[17]: https://creativecommons.org/licenses/by/4.0/
