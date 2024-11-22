CC = g++
JOLTFLAGS = -DJPH_OBJECT_STREAM -DJPH_DOUBLE_PRECISION
CCFLAGS = -g -fPIC -Wall -Werror -DNDEBUG $(JOLTFLAGS) -Isrc/c
LDFLAGS = -lJolt

all: src/c/sfsim/libjolt.so

src/c/sfsim/libjolt.so: src/c/sfsim/jolt.o
	$(CC) -shared -flto=auto -o $@ $^ $(LDFLAGS)

.cc.o:
	$(CC) $(CCFLAGS) -c $< -o $@

src/c/sfsim/jolt.o: src/c/sfsim/jolt.cc src/c/sfsim/jolt.hh

clean:
	rm -f src/c/sfsim/libjolt.so src/c/sfsim/*.o
