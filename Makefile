JAVAC=javac
JAVA=java
SRC=$(wildcard StableMulticast/*.java) AppCliente.java
CLASSDIR=.

all:
	$(JAVAC) -d $(CLASSDIR) $(SRC)

run:
	$(JAVA) AppCliente 230.0.0.0 5001

clean:
	del /Q StableMulticast\*.class *.class
