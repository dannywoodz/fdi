UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Linux)
	DLEXT=so
else
	DLEXT=dylib
endif

all: libfileid.$(DLEXT)
	mv native/libfileid.$(DLEXT) ../resources

libfileid.$(DLEXT): native/fdi_FileID.h native/fdi_FileID.c
	cd native && gcc -dynamiclib -fPIC -Wall fdi_FileID.c -o $@ `pkg-config --libs --cflags MagickWand` -g -D_GNU_SOURCE

native/fdi_FileID.h: fdi/FileID.class
	cd java && javah -d ../native fdi.FileID

fdi/FileID.class: java/fdi/FileID.java
	cd java && javac fdi/FileID.java

install: libfileid.$(DLEXT)
	cp fdi/libfileid.$(DLEXT) ${HOME}/sfw/lib

clean:
	rm -f java/fdi/FileID.class fdi/libfileid.$(DLEXT) native/fdi/fdi_FileID.h

