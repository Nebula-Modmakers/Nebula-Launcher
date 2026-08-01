// Copyright (c) 2026 XtraCube
#ifndef FUSIONCORE_JAVA_H
#define FUSIONCORE_JAVA_H

#define GET_JAVA_STRING(env, javaString, assignment) \
    do { \
        const char *chars = (env)->GetStringUTFChars(javaString, nullptr); \
        assignment = std::string(chars); \
        (env)->ReleaseStringUTFChars(javaString, chars); \
    } while(0)

#endif //FUSIONCORE_JAVA_H
