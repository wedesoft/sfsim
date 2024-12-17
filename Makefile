CC = g++
STRIP = strip
JOLTFLAGS = -DJPH_DOUBLE_PRECISION -DJPH_OBJECT_STREAM -mf16c
CCFLAGS = -g -O3 -fPIC -Wall -Werror -DNDEBUG $(JOLTFLAGS) -pthread -I/usr/local/include -Isrc/c
LDFLAGS = -L/usr/local/lib -lJolt -pthread

all: src/c/sfsim/libjolt.so

src/c/sfsim/libjolt.so: src/c/sfsim/jolt.o
	$(CC) -shared -flto=auto -o $@ $^ $(LDFLAGS)
	$(STRIP) $@

.cc.o:
	$(CC) $(CCFLAGS) -c $< -o $@

src/c/sfsim/jolt.o: src/c/sfsim/jolt.cc src/c/sfsim/jolt.hh

clean:
	rm -f src/c/sfsim/libjolt.so src/c/sfsim/*.o
