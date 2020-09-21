.SUFFIXES: .c .h .java .so .o

all: libnative.so

run: all
	env LD_LIBRARY_PATH=$(PWD) clojure -cp . test.clj
	
libnative.so: HelloWorldJNI.o
	gcc -shared -fPIC -o $@ $^

HelloWorldJNI.h: HelloWorldJNI.java
	javac -h . $<

.c.o:
	gcc -c -fPIC -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux $< -o $@

HelloWorldJNI.o: HelloWorldJNI.h

clean:
	rm -f *.h *.o *.so *.class
