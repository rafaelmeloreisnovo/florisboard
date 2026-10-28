#include <jni.h>
extern jint Java_dev_patrickgold_florisboard_rmr_RmrNativeBridge_nativeSelfTest(JNIEnv*, jclass);
int main(void) {
    return (int)Java_dev_patrickgold_florisboard_rmr_RmrNativeBridge_nativeSelfTest(0, 0);
}
