UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Darwin)
	DLEXT=dylib
  GCCDL=-dynamiclib
else
	DLEXT=so
  GCCDL=-shared -Wl,-soname,libfdi.so
endif

all: libfdi.$(DLEXT)
	mv c/libfdi.$(DLEXT) ../resources

libfdi.$(DLEXT): c/fdi_FDI.h c/fdi_FDI.c
	cd c && gcc $(GCCDL) -fPIC -Wall fdi_FDI.c -o $@ `pkg-config --libs --cflags MagickWand` -g -D_GNU_SOURCE

c/fdi_FDI.h: fdi/FDI.class
	cd java && javah -d ../c fdi.FDI

fdi/FDI.class: java/fdi/FDI.java
	cd java && javac fdi/FDI.java

install: libfdi.$(DLEXT)
	cp ../resources/libfdi.$(DLEXT) ${HOME}/sfw/lib

clean:
	rm -f java/fdi/FDI.class fdi/libfdi.$(DLEXT) c/fdi/fdi_FDI.h

