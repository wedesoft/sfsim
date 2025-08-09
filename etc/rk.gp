set terminal pngcairo size 640,480
set output "rk-height.png"
set title "Runge-Kutta double precision method orbit deviations"
set xlabel "t"
set ylabel "h"
plot "/tmp/rk0.031.dat" using 1:2 with lines title "dt = 0.031", \
     "/tmp/rk0.102.dat" using 1:2 with lines title "dt = 0.102", \
     "/tmp/rk0.500.dat" using 1:2 with lines title "dt = 0.500", \
     "/tmp/rk1.000.dat" using 1:2 with lines title "dt = 1.000", \
     "/tmp/rk2.000.dat" using 1:2 with lines title "dt = 2.000", \
     "/tmp/rk4.000.dat" using 1:2 with lines title "dt = 4.000", \
     "/tmp/rk8.000.dat" using 1:2 with lines title "dt = 8.000", \
     "/tmp/rk16.000.dat" using 1:2 with lines title "dt = 16.000"
