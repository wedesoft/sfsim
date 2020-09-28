#!/bin/sh
if [ -z $4 ]; then
  echo "Syntax: $0 [input image] [output prefix] [y offset] [x offset]" >&2
  exit 1
fi
# Set MAGICK_TMPDIR to file system location with plenty of space!
lein run-scale-image $1 map2.png
lein run-scale-image map2.png map3.png
lein run-scale-image map3.png map4.png
lein run-scale-image map4.png map5.png
lein run-scale-image map5.png map6.png
lein run-map-tiles $1 675 5 $2 $3 $4
lein run-map-tiles map2.png 675 4 $2 $3 $4
lein run-map-tiles map3.png 675 3 $2 $3 $4
lein run-map-tiles map4.png 675 2 $2 $3 $4
lein run-map-tiles map5.png 675 1 $2 $3 $4
lein run-map-tiles map6.png 675 0 $2 $3 $4
