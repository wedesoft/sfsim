set terminal x11

set title "lift, drag, and lift to drag ratio"

set xlabel "alpha"
set ylabel "amount"

set angles degrees
set samples 10000

glide(alpha) = alpha < 13 ? alpha * 0.8 / 13 : alpha <= 25 ? 0.5 * (1 - sqrt(1 - ((25 - alpha) / (25 - 13)) ** 2)) : 0.0
tail(alpha) = abs(alpha - 180) < 8 ? (alpha - 180) * 0.5 / 8 : abs(alpha - 180) < 20 ? 0.5 * (alpha > 180 ? 200 - alpha : 160 - alpha) / (20 - 8) : 0.0
bumps(alpha) = abs(alpha - 180) < 20 ? 0.02 * (1 - cos((alpha - 180) * 360 / 20)) : 0.0

CD(alpha) = 0.1 + 0.5 * 1.9 * (1 - cos(2 * alpha)) + bumps(alpha)
CL(alpha) = 1.1 * sin(2 * alpha) + glide(alpha) - glide(360 - alpha) + tail(alpha)

CLmirror(alpha) = alpha < 180 ? CL(180 - alpha) : CL(540 - alpha)

CLmix(alpha, beta) = 0.5 * (CL(alpha) * (1 + cos(beta)) - CLmirror(alpha) * (1 - cos(beta)))

CDmix(alpha, beta) = 0.1 * 0.5 * (1 + cos(2 * alpha)) + 2.0 * 0.5 * (1 - cos(2 * alpha))

# use 3D angle to wind instead of beta?

# plot [alpha=0:360] CL(alpha), CD(alpha), CL(alpha) / CD(alpha)
# plot [alpha=0:360] CLmix(alpha, 0), CLmix(alpha, 45), CLmix(alpha, 90), CLmix(alpha, 135), CLmix(alpha, 180), CLmix(alpha, 225), CLmix(alpha, 270), CLmix(alpha, 315)
plot [alpha=0:360] CDmix(alpha, 0)
# plot [beta=-180:180] CDmix(0, beta)

# plot [alpha=-180:180] "etc/cl.dat" with lines, "etc/cd.dat" with lines

pause -1
