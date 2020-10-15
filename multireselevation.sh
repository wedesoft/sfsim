#!/bin/sh
set -e
if [ -z $4 ]; then
  echo "Syntax: $0 [input data] [output prefix] [y offset] [x offset]" >&2
  exit 1
fi
lein run-scale-elevation $1 elevation2.raw
lein run-scale-elevation elevation2.raw elevation3.raw
lein run-scale-elevation elevation3.raw elevation4.raw
lein run-scale-elevation elevation4.raw elevation5.raw
lein run-elevation-tiles $1 675 4 $2 $3 $4
lein run-elevation-tiles elevation2.raw 675 3 $2 $3 $4
lein run-elevation-tiles elevation3.raw 675 2 $2 $3 $4
lein run-elevation-tiles elevation4.raw 675 1 $2 $3 $4
lein run-elevation-tiles elevation5.raw 675 0 $2 $3 $4
