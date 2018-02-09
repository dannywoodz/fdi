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
#include <openssl/sha.h>
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


int ifp_is_similar(const char *fp1, size_t fp1len, const char *fp2, size_t fp2len, int tolerance)
{
  int return_value = 0;
  
  if ( fp1len != fp2len ) {
    return 0;
  }

  if ( tolerance == 0 )
  {
    const char *fp = fp1, *sp = fp2;
    while ( fp < &fp1[fp1len] ) if (*fp++ != *sp++) return 0;
    return_value = 1;
  }
  else
  {
    int error = 0;
    int cutoff = fp1len * tolerance;
    const char *fp = fp1, *sp = fp2;
    while ( fp < &fp1[fp1len] && error < cutoff ) error += abs(*fp++ - *sp++);
    return_value = error < cutoff;
  }
  return return_value;
}
