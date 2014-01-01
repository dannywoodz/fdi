#include <jni.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <wand/MagickWand.h>


JNIEXPORT void JNICALL Java_fdi_FileID_init(JNIEnv *env, jclass type)
{
  MagickWandGenesis();
}

static void throw_ioexception(JNIEnv *env, const char *fmt, ...)
{
  char *message = NULL;
  jclass ioException = (*env)->FindClass(env, "java/io/IOException");
  if (!ioException)
  {
    fprintf(stderr, "Couldn't find java.io.IOException.  JVM is borked.  Terminating\n");
    exit(EXIT_FAILURE);
  }
  va_list args;
  va_start(args, fmt);
  vasprintf(&message, fmt, args);
  va_end(args);
  (*env)->ThrowNew(env, ioException, message);
  free(message);
}

JNIEXPORT jlongArray JNICALL Java_fdi_FileID_id(JNIEnv *env, jclass type, jstring jfilename)
{
  const char *filename = (*env)->GetStringUTFChars(env, jfilename, NULL);
  struct stat file_info;

  if ((stat(filename, &file_info)) == 0)
  {
    (*env)->ReleaseStringUTFChars(env, jfilename, filename);
  }
  else
  {
    throw_ioexception(env, "failed to open %s: %s\n", filename, strerror(errno));
    (*env)->ReleaseStringUTFChars(env, jfilename, filename);
    return NULL;
  }

  jlong nativeFields[3] = {
    file_info.st_dev,
    file_info.st_ino,
    file_info.st_mtime
  };

  jlongArray fields = (*env)->NewLongArray(env, 3);
  (*env)->SetLongArrayRegion(env, fields, 0, 3, nativeFields);

  return fields;
}

JNIEXPORT jbyteArray JNICALL Java_fdi_FileID_fingerprint(JNIEnv *env, jclass type, jstring jfilename)
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
