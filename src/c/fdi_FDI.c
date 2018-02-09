// APPLICATION
//
// fdi - Find Duplicates Images
//
// FILE
//
// fdi_FDI.c
//
// DESCRIPTION
//
// Native bridge to ImageMagick functions required for generating image
// fingerprints.
//
// COPYRIGHT
//
// Copyright (C) 2014 Daniel Woods
//
// LICENSE
//
// GNU General Public License, version 3 (http://opensource.org/licenses/GPL-3.0)

#include <jni.h>
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


JNIEXPORT void JNICALL Java_fdi_FDI_init(JNIEnv *env, jclass type)
{
  MagickWandGenesis();
}

static void throw_ioexception(JNIEnv *env, const char *fmt, ...)
{
  char *message = NULL;
  char *message_default = "Out of memory";
  jclass ioException = (*env)->FindClass(env, "java/io/IOException");
  if (!ioException)
  {
    fprintf(stderr, "Couldn't find java.io.IOException.  JVM is borked.  Terminating\n");
    exit(EXIT_FAILURE);
  }
  va_list args;
  va_start(args, fmt);
  if (vasprintf(&message, fmt, args) == -1) // vasprintf failed
  {
    (*env)->ThrowNew(env, ioException, message_default); // hard-coded string is the best we can do
  }
  else
  {
    (*env)->ThrowNew(env, ioException, message);
    free(message);
  }
  va_end(args);
}

JNIEXPORT jbyteArray JNICALL Java_fdi_FDI_fingerprint(JNIEnv *env, jclass type, jstring jfilename)
{
  jbyteArray fingerprint = NULL;
  const char *filename = (*env)->GetStringUTFChars(env, jfilename, NULL);
  MagickWand *wand = NewMagickWand();

  if (MagickReadImage(wand, filename) == MagickFalse)
  {
    throw_ioexception(env, "failed to open %s: %s\n", filename, strerror(errno));
    goto cleanup;
  }

  MagickResetIterator(wand);
  if (MagickNextImage(wand) == MagickFalse)
  {
    throw_ioexception(env, "failed to find image in %s\n", filename);
    goto cleanup;
  }

  MagickNormalizeImage(wand);
  MagickScaleImage(wand, 4, 4);
  MagickSetImageFormat(wand, "rgb");
  size_t length;
  unsigned char *blob = MagickGetImageBlob(wand, &length);
  fingerprint = (*env)->NewByteArray(env, length);
  (*env)->SetByteArrayRegion(env, fingerprint, 0, length, (jbyte*)blob);
  MagickRelinquishMemory(blob);

cleanup:
  (*env)->ReleaseStringUTFChars(env, jfilename, filename);
  if ( wand ) DestroyMagickWand(wand);

  return fingerprint;
}
