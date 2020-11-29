.SUFFIXES: .c .h .java .class .so .o

CLASSPATH=.

all: HelloWorldJNI.class libnative.so

clojure-native: all
	env LD_LIBRARY_PATH=$(PWD) clojure -classpath $(CLASSPATH) test.clj

java-native: all
	env LD_LIBRARY_PATH=$(PWD) java -classpath $(CLASSPATH) HelloWorldJNI

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
	rm -f *.h *.o *.so *.class
