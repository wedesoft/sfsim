.SUFFIXES: .c .h .java .class .so .o

# CLASSPATH=/usr/share/java/jogl2.jar:/usr/share/java/gluegen2-rt.jar:.
CLASSPATH=resources/jogamp-fat.jar:/usr/share/java/gluegen2-rt.jar:.

all: resources/jogamp-fat.jar HelloWorldJNI.class libnative.so HelloTriangleSimple.class

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

resources/jogamp-fat.jar:
	wget https://jogamp.org/deployment/v2.4.0-rc-20200115/fat/jogamp-fat.jar -O $@

clean:
	rm -f *.h *.o *.so *.class framework/*.class
