---
layout: post
title:  Navball MFD
date:   2026-06-12 00:21:00 +0100
categories: interface
---

Hi,

I have released version 0.21-1 of [sfsim][1].

![Navball MFD](/sfsim/pics/navball-mfd.jpg)

The new version of *sfsim* shows a navball in the bottom right corner of the window.
At the moment I configured it to show pitch and roll angle relative to the Earth's horizon and yaw angle relative to the orbital plane.
This should make it useful for orbital flight.
I might change it to let the user select the yaw angle instead of aligning it to the orbital plane which changes during launch.
I am also thinking of introducing a separate navball mode showing cardinal directions.
Furthermore I would like to introduce attitude error needles once I get some guidance code working.

Creating the navball was quite a bit of work.
I used one of the textures from the [NavBall Texture Changer][5] for [KSP][6] as a reference.
I recreated the texture because *sfsim* uses the [B612 aeronautic font][7] for increased readability.

![Navball Texture](/sfsim/pics/navball-tex.jpg)

The proper name of the navball is Flight Director Attitude Indicator (FDAI).
It was actuated using Inertial Measurement Unit (IMU) gyroscopes.
Furthermore it was possible to use the Orbit Rate Display Earth and Lunar (ORDEAL) system to keep the horizon aligned with the Earth's or Moon's horizon.
Here is a picture showing the navball in the Apollo 11 Lunar Module cockpit (source: [NASA][8]).

![Apollo 11 Lunar Module](/sfsim/pics/apollo-11-lm.jpg)

Let me know in the [sfsim playtest discussion forum][2] if you have any thoughts.
Don't forget to [wishlist sfsim][3]!

Enjoy!

[1]: https://wedesoft.github.io/sfsim/
[2]: https://steamcommunity.com/app/3847320/discussions/
[3]: https://store.steampowered.com/app/3687560/sfsim/
[4]: https://www.artstation.com/artwork/lVl2gJ
[5]: https://spacedock.info/mod/2654/NavBall%20Texture%20Changer%20Updated
[6]: https://store.steampowered.com/app/220200/Kerbal_Space_Program/
[7]: https://lii.enac.fr/projects/definition-and-validation-of-an-aeronautical-font/
[8]: https://www.nasa.gov/history/55-years-ago-one-month-until-the-moon-landing/
