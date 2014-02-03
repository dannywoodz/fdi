CFLAGS ?=
CC=gcc -Wall -Werror -pedantic -ansi -std=c99 -g -D_GNU_SOURCE
UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Darwin)
	DLEXT=dylib
else
	DLEXT=so
endif

ARCH_BITS ?= $(shell getconf LONG_BIT)
LIBNAME = libfdi$(ARCH_BITS).$(DLEXT)

ifeq ($(UNAME_S),Darwin)
	  GCCDL=-dynamiclib
else
	GCCDL=-shared -Wl,-soname,$(LIBNAME)
endif

all: $(LIBNAME)
	mv c/$(LIBNAME) ../resources

$(LIBNAME): c/fdi_FDI.h c/fdi_FDI.c
	cd c && $(CC) $(CFLAGS) $(GCCDL) -m$(ARCH_BITS) -fPIC fdi_FDI.c -o $@ `pkg-config --libs --cflags MagickWand` 

c/fdi_FDI.h: fdi/FDI.class
	cd java && javah -d ../c fdi.FDI

fdi/FDI.class: java/fdi/FDI.java
	cd java && javac fdi/FDI.java

clean:
	rm -f java/fdi/FDI.class fdi/$(LIBNAME) c/fdi/fdi_FDI.h

