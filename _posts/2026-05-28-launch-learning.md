---
layout: post
title:  Reinforcement Learning of Rocket Launch
date:   2026-05-28 23:14:52 +0100
categories: simulation
---

Hi,

I have released version 0.19-1 of [sfsim][1].

This version introduces scaling of the graphical user interface.
All the user interface elements are scaled up when switching to fullscreen or back taking into account the monitor resolution.
This should make it easier to start implementing multi function displays (MFDs).

![User interface scaling](/sfsim/pics/scaling.jpg)

Furthermore I am working on launch trajectory optimization.
It is not optimal yet as the rocket should first go up to leave the atmosphere more quickly.
I am using proximal policy optimization (PPO) which is a reinforcement learning method which has become popular.
Hopefully PPO will be able to also solve the more difficult problem of reentry.
If you want to know more about this reinforcement learning method, I posted an [article about PPO at ClojureCivitas][3].

![Launch Trajectory](/sfsim/pics/launch.png)

Let me know any feedback and comments in the [sfsim playtest discussion forum][2].

Enjoy!

[1]: https://wedesoft.github.io/sfsim/
[2]: https://steamcommunity.com/app/3847320/discussions/
[3]: https://clojurecivitas.org/ppo/main.html
