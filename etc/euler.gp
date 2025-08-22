set terminal pngcairo size 640,480
set output "euler-height.png"
set title "Euler method orbit deviations"
set xlabel "t"
set ylabel "h"
plot "/tmp/euler0.031.dat" using 1:2 with lines title "dt = 0.031", \
     "/tmp/euler0.102.dat" using 1:2 with lines title "dt = 0.102", \
     "/tmp/euler0.500.dat" using 1:2 with lines title "dt = 0.500", \
     "/tmp/euler1.000.dat" using 1:2 with lines title "dt = 1.000", \
     "/tmp/euler2.000.dat" using 1:2 with lines title "dt = 2.000", \
     "/tmp/euler4.000.dat" using 1:2 with lines title "dt = 4.000", \
     "/tmp/euler8.000.dat" using 1:2 with lines title "dt = 8.000", \
     "/tmp/euler16.000.dat" using 1:2 with lines title "dt = 16.000"
