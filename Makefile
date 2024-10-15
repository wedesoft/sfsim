CC = g++
JOLTFLAGS = -DJPH_PROFILE_ENABLED -DJPH_DEBUG_RENDERER -DJPH_OBJECT_STREAM -DJPH_DOUBLE_PRECISION
CCFLAGS = -g -fPIC -Wall -Werror -DNDEBUG $(JOLTFLAGS) -Isrc/c
LDFLAGS = -lJolt

src/c/sfsim/libjolt.so: src/c/sfsim/jolt.o
	$(CC) -shared -o $@ $^ $(LDFLAGS)

.cc.o:
	$(CC) $(CCFLAGS) -c $< -o $@
