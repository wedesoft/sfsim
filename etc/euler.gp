set terminal pngcairo size 640,480
set output "integration.png"
set xlabel "t"
set ylabel "h"
plot "/tmp/euler.dat" using 1:2 with lines title "Euler", "/tmp/rk.dat" using 1:2 with lines title "Runge-Kutta"
