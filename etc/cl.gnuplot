set terminal x11

set title "lift, drag, and lift to drag ratio"

set xlabel "alpha"
set ylabel "amount"

set angles degrees
CD(alpha) = 0.1 + 0.5 * 1.9 * (1 - cos(2 * alpha))
CL(alpha) = 1.1 * sin(2 * alpha)

plot [alpha=0:360] CL(alpha), CD(alpha), CL(alpha) / CD(alpha)

pause -1
