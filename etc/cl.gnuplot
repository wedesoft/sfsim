set terminal x11 size 1024, 768
# set terminal png font arial 14 size 800,600

set ylabel "amount"
set grid
set xtics 45

set angles degrees
set samples 10000

# alpha: angle of attack
# beta: sideslip angle
# Cl: coefficient of lift
# Cd: coefficient of drag
# Cy: coefficient of side force

glide(alpha) = alpha < 13 ? alpha * 0.8 / 13 : alpha <= 25 ? 0.5 * (1 - sqrt(1 - ((25 - alpha) / (25 - 13)) ** 2)) : 0.0
tail(alpha) = abs(alpha - 180) < 8 ? (alpha - 180) * 0.5 / 8 : abs(alpha - 180) < 20 ? 0.5 * (alpha > 180 ? 200 - alpha : 160 - alpha) / (20 - 8) : 0.0
bumps(alpha) = abs(alpha - 180) < 20 ? 0.02 * (1 - cos((alpha - 180) * 360 / 20)) : 0.0

mix(a, b, angle) = 0.5 * (a * (1 + cos(angle)) + b * (1 - cos(angle)))
mirror(alpha) = alpha < 180 ? 180 - alpha : 540 - alpha

CL(alpha) = 1.1 * sin(2 * alpha) + glide(alpha) - glide(360 - alpha) + tail(alpha)
CD(alpha) = mix(0.1, 2.0, 2 * alpha) + bumps(alpha)
CY(beta) = -0.4 * sin(2 * beta)

CLmix(alpha, beta) = (0.5 * CL(alpha) * (1 + cos(beta)) - 0.5 * CL(mirror(alpha)) * (1 - cos(beta))) * cos(beta)

CDmix(alpha, beta) = mix(mix(0.1, 2.0, 2 * alpha), 0.5, 2 * beta)

CYmix(alpha, beta) = -0.4 * sin(2 * beta) * cos(alpha) + 2.0 * sin(2 * beta) * sin(alpha)

# set output "1-lift-to-drag.png"
set title "lift, drag, and lift to drag ratio"
set xlabel "alpha"
plot [alpha=0:360] CL(alpha), CD(alpha), CL(alpha) / CD(alpha)
pause -1

# set output "2-coefficient-of-lift.png"
set title "coefficient of lift"
set xlabel "alpha"
plot [alpha=0:360] CLmix(alpha, 0), CLmix(alpha, 45), CLmix(alpha, 90), CLmix(alpha, 135), CLmix(alpha, 180), CLmix(alpha, 225), CLmix(alpha, 270), CLmix(alpha, 315)
pause -1

# set output "3-coefficient-of-drag.png"
set title "coefficient of drag"
set xlabel "alpha"
plot [alpha=0:360] CDmix(alpha, 0), CDmix(alpha, 45), CDmix(alpha, 90), CDmix(alpha, 135), CDmix(alpha, 180), CDmix(alpha, 225), CDmix(alpha, 270), CDmix(alpha, 315)
pause -1

# set output "4-coefficient-of-side-force.png"
set title "coefficient of side force"
set xlabel "beta"
plot [beta=0:360] CYmix(-90, beta), CYmix(-60, beta), CYmix(-30, beta), CYmix(0, beta), CYmix(30, beta), CYmix(60, beta), CYmix(90, beta)
pause -1

# plot [alpha=-180:180] "etc/cl.dat" with lines, "etc/cd.dat" with lines
