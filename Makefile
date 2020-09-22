.SUFFIXES: .c .h .java .class .so .o

# CLASSPATH=/usr/share/java/jogl2.jar:/usr/share/java/gluegen2-rt.jar:.
CLASSPATH=jogamp-fat.jar:/usr/share/java/gluegen2-rt.jar:.

all: HelloWorldJNI.class libnative.so HelloTriangleSimple.class

clojure-native: all
	env LD_LIBRARY_PATH=$(PWD) clojure -classpath $(CLASSPATH) test.clj

java-native: all
	env LD_LIBRARY_PATH=$(PWD) java -classpath $(CLASSPATH) HelloWorldJNI

javagl3: all
	java -classpath $(CLASSPATH) HelloTriangleSimple

repl: all
	env LD_LIBRARY_PATH=$(PWD) clojure -classpath $(CLASSPATH)
	
libnative.so: HelloWorldJNI.o
	gcc -shared -fPIC -o $@ $^

HelloWorldJNI.h: HelloWorldJNI.java
	javac -h . $<

.java.class:
	javac -classpath $(CLASSPATH) $<

.c.o:
	gcc -c -fPIC -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux $< -o $@

HelloWorldJNI.o: HelloWorldJNI.h

clean:
	rm -f *.h *.o *.so *.class framework/*.class
