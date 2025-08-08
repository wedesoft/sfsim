set terminal pngcairo size 1280,960
set output "errors.png"
set xlabel "log(t)"
set ylabel "h"
plot "/tmp/errors.dat" using 1:2 with lines title "max error"
