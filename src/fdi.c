// LIBRARY
//
// ifp - image fingerprint
//
// FILE
//
// ifp.c
//
// DESCRIPTION
//
// Simple fingerprinting of an image, and determination of similarity
//
// COPYRIGHT
//
// Copyright (C) 2018 Daniel Woods
//
// LICENSE
//
// GNU General Public License, version 3 (http://opensource.org/licenses/GPL-3.0)

#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <MagickWand/MagickWand.h>


void fdi_init(void)
{
  MagickWandGenesis();
}

int fdi_fingerprint(const char *filename, unsigned char *buffer)
{
  MagickWand *wand = NewMagickWand();
  int return_code;

  if (MagickReadImage(wand, filename) == MagickFalse)
  {
    return_code = -1;
    goto cleanup;
  }

  MagickResetIterator(wand);
  if (MagickNextImage(wand) == MagickFalse)
  {
    return_code = -2;
    goto cleanup;
  }

  MagickNormalizeImage(wand);
  MagickScaleImage(wand, 4, 4);
  MagickSetImageFormat(wand, "rgb");
  size_t required;
  unsigned char *blob = MagickGetImageBlob(wand, &required);
  memcpy(buffer, blob, required);
  MagickRelinquishMemory(blob);
  
  return_code = (int)required;

cleanup:
  if ( wand ) DestroyMagickWand(wand);

  return return_code;
}
