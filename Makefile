CC = g++
STRIP = strip
JOLTFLAGS = -DJPH_OBJECT_STREAM -DJPH_DOUBLE_PRECISION
CCFLAGS = -g -fPIC -Wall -Werror -DNDEBUG -I/usr/local/include $(JOLTFLAGS) -Isrc/c
LDFLAGS = -L/usr/local/lib -lJolt

all: src/c/sfsim/libjolt.dll

src/c/sfsim/libjolt.dll: src/c/sfsim/jolt.o
	$(CC) -shared -flto=auto -o $@ $^ $(LDFLAGS)
	$(STRIP) $@

.cc.o:
	$(CC) $(CCFLAGS) -c $< -o $@

src/c/sfsim/jolt.o: src/c/sfsim/jolt.cc src/c/sfsim/jolt.hh

clean:
	rm -f src/c/sfsim/libjolt.dll src/c/sfsim/*.o
