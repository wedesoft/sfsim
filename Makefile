all: bluemarble

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

world/0/0/0.png: world.200404.3x21600x21600.A1.png
	sh ./multiresmap.sh $< world 0 0

world/0/0/1.png: world.200404.3x21600x21600.A2.png
	sh ./multiresmap.sh $< world 1 0

world/0/1/0.png: world.200404.3x21600x21600.B1.png
	sh ./multiresmap.sh $< world 0 1

world/0/1/1.png: world.200404.3x21600x21600.B2.png
	sh ./multiresmap.sh $< world 1 1

world/0/2/0.png: world.200404.3x21600x21600.C1.png
	sh ./multiresmap.sh $< world 0 2

world/0/2/1.png: world.200404.3x21600x21600.C2.png
	sh ./multiresmap.sh $< world 1 2

world/0/3/0.png: world.200404.3x21600x21600.D1.png
	sh ./multiresmap.sh $< world 0 3

world/0/3/1.png: world.200404.3x21600x21600.D2.png
	sh ./multiresmap.sh $< world 1 3

bluemarble: world/0/0/0.png world/0/0/1.png world/0/1/0.png world/0/1/1.png \
	world/0/2/0.png world/0/2/1.png world/0/3/0.png world/0/3/1.png
