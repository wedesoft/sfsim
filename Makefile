all: bluemarble heightfield

check:
	lein test

repl:
	lein repl

# See https://neo.sci.gsfc.nasa.gov/archive/bluemarble/bmng/world_500m/
# Also see https://visibleearth.nasa.gov/collection/1484/blue-marble (downloads keep failing)
world.200404.3x21600x21600.A1.png:
	$(WGET) https://neo.sci.gsfc.nasa.gov/archive/bluemarble/bmng/world_500m/world.200404.3x21600x21600.A1.png

world.200404.3x21600x21600.A2.png:
	$(WGET) https://neo.sci.gsfc.nasa.gov/archive/bluemarble/bmng/world_500m/world.200404.3x21600x21600.A2.png

world.200404.3x21600x21600.B1.png:
	$(WGET) https://neo.sci.gsfc.nasa.gov/archive/bluemarble/bmng/world_500m/world.200404.3x21600x21600.B1.png

world.200404.3x21600x21600.B2.png:
	$(WGET) https://neo.sci.gsfc.nasa.gov/archive/bluemarble/bmng/world_500m/world.200404.3x21600x21600.B2.png

world.200404.3x21600x21600.C1.png:
	$(WGET) https://neo.sci.gsfc.nasa.gov/archive/bluemarble/bmng/world_500m/world.200404.3x21600x21600.C1.png

world.200404.3x21600x21600.C2.png:
	$(WGET) https://neo.sci.gsfc.nasa.gov/archive/bluemarble/bmng/world_500m/world.200404.3x21600x21600.C2.png

world.200404.3x21600x21600.D1.png:
	$(WGET) https://neo.sci.gsfc.nasa.gov/archive/bluemarble/bmng/world_500m/world.200404.3x21600x21600.D1.png

world.200404.3x21600x21600.D2.png:
	$(WGET) https://neo.sci.gsfc.nasa.gov/archive/bluemarble/bmng/world_500m/world.200404.3x21600x21600.D2.png

all10: all10g.tgz
	tar -xvf $<
	touch $@

elevation.A1.raw: all10
	cat all10/a10g all10/e10g > $@

elevation.B1.raw: all10
	cat all10/b10g all10/f10g > $@

elevation.C1.raw: all10
	cat all10/c10g all10/g10g > $@

elevation.D1.raw: all10
	cat all10/d10g all10/h10g > $@

elevation.A2.raw: all10
	cat all10/i10g all10/m10g > $@

elevation.B2.raw: all10
	cat all10/j10g all10/n10g > $@

elevation.C2.raw: all10
	cat all10/k10g all10/o10g > $@

elevation.D2.raw: all10
	cat all10/l10g all10/p10g > $@

elevation/0/0/0.raw: elevation.A1.raw
	sh ./multireselevation.sh $< elevation 0 0

elevation/0/0/1.raw: elevation.A2.raw
	sh ./multireselevation.sh $< elevation 1 0

elevation/0/1/0.raw: elevation.B1.raw
	sh ./multireselevation.sh $< elevation 0 1

elevation/0/1/1.raw: elevation.B2.raw
	sh ./multireselevation.sh $< elevation 1 1

elevation/0/2/0.raw: elevation.C1.raw
	sh ./multireselevation.sh $< elevation 0 2

elevation/0/2/1.raw: elevation.C2.raw
	sh ./multireselevation.sh $< elevation 1 2

elevation/0/3/0.raw: elevation.D1.raw
	sh ./multireselevation.sh $< elevation 0 3

elevation/0/3/1.raw: elevation.D2.raw
	sh ./multireselevation.sh $< elevation 1 3

heightfield: elevation/0/0/0.raw elevation/0/0/1.raw elevation/0/1/0.raw elevation/0/1/1.raw \
	elevation/0/2/0.raw elevation/0/2/1.raw elevation/0/3/0.raw elevation/0/3/1.raw

# See https://www.ngdc.noaa.gov/mgg/topo/gltiles.html
all10g.tgz:
	$(WGET) https://www.ngdc.noaa.gov/mgg/topo/DATATILES/elev/all10g.tgz
