set terminal x11

set title "aerodynamic coefficients"

set xlabel "alpha"
set ylabel "amount"

set angles degrees
set samples 10000

# alpha: angle of attack
# beta: sideslip angle
# Cm: coefficient of pitch moment

maximum(a, b) = a > b ? a : b
flip(alpha) = alpha < 10 ? alpha / 10 : alpha <= 25 ? 1 - sin(90 * (alpha - 10) / (25 - 10)) : 0.0

Cm(alpha) = -0.6 * sin(alpha) + 0.2 * (flip(maximum(alpha - 180, 0)) - flip(maximum(180 - alpha, 0)))

plot [alpha=0:360] Cm(alpha)
pause -1
