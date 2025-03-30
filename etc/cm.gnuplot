set terminal x11 size 1024, 768

set ylabel "amount"
set grid
set xtics 45

set angles degrees
set samples 10000

# alpha: angle of attack
# beta: sideslip angle
# Cm: coefficient of pitch moment
# Cn: coefficient of yaw moment
# Cl: coefficient of roll moment

maximum(a, b) = a > b ? a : b
spike(alpha) = alpha < 10 ? alpha / 10 : alpha <= 25 ? 1 - sin(90 * (alpha - 10) / (25 - 10)) : 0.0

# airplane nose vector in wind system
dx(alpha, beta) = cos(alpha) * cos(beta)
dy(alpha, beta) = sin(beta)
dz(alpha, beta) = sin(alpha) * cos(beta)

angle(y, x) = atan2(y, x) >= 0 ? atan2(y, x) : atan2(y, x) + 360
length(y, x) = sqrt(y * y + x * x)
mirror(alpha) = alpha < 180 ? 180 - alpha : 540 - alpha

Cm(alpha) = -0.6 * sin(alpha) + 0.2 * (spike(maximum(alpha - 180, 0)) - spike(maximum(180 - alpha, 0)))
Cmmix(alpha, beta) = (0.5 * Cm(alpha) * (1 + cos(beta)) + 0.5 * Cm(mirror(alpha)) * (1 - cos(beta))) * cos(beta)

Cn(beta) = -2.0 * sin(beta)
Cnmix(alpha, beta) = Cn(beta)

Cl(beta) = -0.5 * sin(beta)
Clmix(alpha, beta) = Cl(beta)

set title "coefficient of pitch moment"
set xlabel "alpha"
plot [alpha=0:360] Cmmix(alpha, 0), Cmmix(alpha, 30), Cmmix(alpha, 60), Cmmix(alpha, 90), Cmmix(alpha, 120), Cmmix(alpha, 150), Cmmix(alpha, 180), Cmmix(alpha, 210), Cmmix(alpha, 240), Cmmix(alpha, 270), Cmmix(alpha, 300), Cmmix(alpha, 330)
pause -1

set title "coefficient of yaw moment"
set xlabel "beta"
plot [beta=0:360] Cnmix(-90, beta), Cnmix(-60, beta), Cnmix(-30, beta), Cnmix(0, beta), Cnmix(30, beta), Cnmix(60, beta), Cnmix(90, beta)
pause -1

set title "coefficient of roll moment"
set xlabel "beta"
plot [beta=0:360] Clmix(-90, beta), Clmix(-60, beta), Clmix(-30, beta), Clmix(0, beta), Clmix(30, beta), Clmix(60, beta), Clmix(90, beta)
pause -1
