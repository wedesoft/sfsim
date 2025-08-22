set terminal pngcairo size 640,480
set output "errors.png"
set title "Runge Kutta with single precision moving coordinate system"
set logscale x 2
set logscale y 2
set xlabel "t"
set ylabel "h"
plot "/tmp/errors.dat" using 1:2 with lines title "max error"
