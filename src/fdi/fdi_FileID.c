#include <jni.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>

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
    char errorMessage[1024];
    const char *fmt = "error opening %s";
    snprintf(errorMessage, sizeof(errorMessage) - strlen(fmt), fmt, filename);
    jclass ioException = (*env)->FindClass(env, "java/io/IOException");
    if(!ioException)
    {
      fprintf(stderr, "Fatal error.  Unable to locate java.io.IOException.  Terminating");
      exit(EXIT_FAILURE);
    }
    (*env)->ThrowNew(env, ioException, errorMessage);
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
