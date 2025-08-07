set terminal pngcairo size 1280,960
set output "integration.png"
set xlabel "t"
set ylabel "h"
plot "/tmp/rk0.010.dat" using 1:2 with lines title "dt = 0.010", \
     "/tmp/rk0.050.dat" using 1:2 with lines title "dt = 0.050", \
     "/tmp/rk0.125.dat" using 1:2 with lines title "dt = 0.125", \
     "/tmp/rk0.250.dat" using 1:2 with lines title "dt = 0.250", \
     "/tmp/rk0.500.dat" using 1:2 with lines title "dt = 0.500", \
     "/tmp/rk1.000.dat" using 1:2 with lines title "dt = 1.000", \
     "/tmp/rk2.000.dat" using 1:2 with lines title "dt = 2.000", \
     "/tmp/rk4.000.dat" using 1:2 with lines title "dt = 4.000", \
     "/tmp/rk8.000.dat" using 1:2 with lines title "dt = 8.000", \
     "/tmp/rk16.000.dat" using 1:2 with lines title "dt = 16.000", \
     "/tmp/rk32.000.dat" using 1:2 with lines title "dt = 32.000", \
     "/tmp/rk64.000.dat" using 1:2 with lines title "dt = 64.000"
