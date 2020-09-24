#include "HelloWorldJNI.h"

JNIEXPORT void JNICALL Java_HelloWorldJNI_sayHello(JNIEnv *env, jobject thisObject)
{
  printf("Hello world from C!\n");
}
