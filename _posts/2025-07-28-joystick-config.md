---
layout: post
title:  Joystick configuration dialog released
date:   2025-07-28 13:08:00 +0100
categories: input
---

![joystick-config](/sfsim/pics/joystick-config.jpg)

Hi,

I finally got the first version of the joystick configuration dialog working.
One can configure axes and buttons in the dialog.
There also is a common dead-zone setting for all joystick axes.
The configuration can be saved using the save button.
The config gets saved in the `~/.config/sfsim` directory under GNU/Linux.
Under windows the configuration directory is `%APPDATA%/sfsim`.

Here are the updated key bindings for testing. Sorry for breaking the camera bindings in some earlier releases:
* Esc: toggle the menu
* Alt-Return: toggle fullscreen
* P: pause/unpause
* G: gear down/up
* B: wheel brakes
* A, S, D, W: flight stick
* Q, E: rudder
* R, F: thrust
* numerical keypad: change camera position
* "," and ".": increase/decrease camera distance
* H, J, K, L: move the camera around
* PgUp, PgDn: move forward/backward in pause mode

Enjoy!
