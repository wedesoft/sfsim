CC = g++
STRIP = strip
JOLTFLAGS = -DJPH_DOUBLE_PRECISION -DJPH_OBJECT_STREAM -DJPH_USE_AVX -DJPH_USE_SSE4_1 -DJPH_USE_SSE4_2 -fno-rtti -fno-exceptions -Wno-stringop-overflow -ffp-contract=off -flto=auto -fno-fat-lto-objects -mavx -mpopcnt -mfpmath=sse
CCFLAGS = -g -O3 -fPIC -Wall -Werror -DNDEBUG $(JOLTFLAGS) -pthread -I/usr/local/include -Isrc/c
LDFLAGS = -L/usr/local/lib -lJolt -pthread

all: src/c/sfsim/libjolt.dll

src/c/sfsim/libjolt.dll: src/c/sfsim/jolt.o
	$(CC) -shared -flto=auto -o $@ $^ $(LDFLAGS)
	$(STRIP) $@

.cc.o:
	$(CC) $(CCFLAGS) -c $< -o $@

src/c/sfsim/jolt.o: src/c/sfsim/jolt.cc src/c/sfsim/jolt.hh

clean:
	rm -f src/c/sfsim/libjolt.dll src/c/sfsim/*.o
