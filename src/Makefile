all: libfileid.so

libfileid.so: fdi/fdi_FileID.h fdi/fdi_FileID.c
	cd fdi && gcc -shared -fPIC -Wl,-soname,$@ -Wall fdi_FileID.c -o $@ `pkg-config --libs --cflags MagickWand` -g -D_GNU_SOURCE

fdi/fdi_FileID.h: fdi/FileID.class
	javah -d fdi fdi.FileID

%.class: %.java
	javac $<

install: libfileid.so
	cp fdi/libfileid.so /home/danny/sfw/lib

clean:
	rm -f fdi/FileID.class fdi/libfileid.so fdi/fdi_FileID.h

