#include <jni.h>
#include "com_example_capston_MainActivity.h"
#include "com_example_capston_settings.h"
#include "com_example_capston_CamService.h"
#include <opencv2/opencv.hpp>
#include <android/log.h>


using namespace cv;
using namespace std;



extern "C" {

float scale = 1.1;
int neighbor = 5, sum_x = 0;
Point eye_center = Point(0, 0);
float result_x;

Size msize = Size(100, 100);
JNIEXPORT jlong JNICALL Java_com_example_capston_MainActivity_loadCascade
        (JNIEnv *env, jobject type, jstring cascadeFileName_) {

    const char *nativeFileNameString = env->GetStringUTFChars(cascadeFileName_, 0);

    string baseDir("/storage/emulated/0/");
    baseDir.append(nativeFileNameString);
    const char *pathDir = baseDir.c_str();

    jlong ret = 0;
    ret = (jlong) new CascadeClassifier(pathDir);
    if (((CascadeClassifier *) ret)->empty()) {
        __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ",
                            "CascadeClassifier로 로딩 실패  %s", nativeFileNameString);
    } else
        __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ",
                            "CascadeClassifier로 로딩 성공 %s", nativeFileNameString);


    env->ReleaseStringUTFChars(cascadeFileName_, nativeFileNameString);

    return ret;
}
float resize(Mat img_src, Mat &img_resize, int resize_width) {

    float scale = resize_width / (float) img_src.cols;
    if (img_src.cols > resize_width) {
        int new_height = cvRound(img_src.rows * scale);
        resize(img_src, img_resize, Size(resize_width, new_height));
    } else {
        img_resize = img_src;
    }
    return scale;
}

JNIEXPORT jint JNICALL Java_com_example_capston_Settings_detect
        (JNIEnv *env, jobject type, jlong cascadeClassifier_face, jlong cascadeClassifier_side_face,
         jlong cascadeClassifier_eye, jlong matAddrInput, jlong matAddrResult) {
    Mat &img_input = *(Mat *) matAddrInput;
    Mat &img_result = *(Mat *) matAddrResult;

    img_result = img_input.clone();

    std::vector<Rect> faces;
    std::vector<Rect> eyes;

    Mat img_gray;
    cvtColor(img_input, img_gray, COLOR_BGR2GRAY);
    equalizeHist(img_gray, img_gray);
    Mat img_resize;
    float resizeRatio = resize(img_gray, img_resize, 640);


    std::vector<Rect> side_faces;

    ((CascadeClassifier *) cascadeClassifier_side_face)->detectMultiScale(img_resize,
                                                                          side_faces, scale,
                                                                          neighbor,
                                                                          0 |
                                                                          CASCADE_SCALE_IMAGE,
                                                                          msize); //1.1 2
//    __android_log_print(ANDROID_LOG_DEBUG, (char *) "native-lib :: ",
//                        (char *) "side_face %d found ", side_faces.size());
    for (int i = 0; i < side_faces.size(); i++) {
        double real_facesize_x = side_faces[i].x / resizeRatio;
        double real_facesize_y = side_faces[i].y / resizeRatio;
        double real_facesize_width = side_faces[i].width / resizeRatio;
        double real_facesize_height = side_faces[i].height / resizeRatio;

        Point center(real_facesize_x + real_facesize_width / 2,
                     real_facesize_y + real_facesize_height / 2);
        ellipse(img_result, center,
                Size(real_facesize_width / 2, real_facesize_height / 2),
                0,
                0, 360,
                Scalar(255, 0, 255), 30, 8, 0);
        line(img_result, Point(real_facesize_x, real_facesize_y + real_facesize_height / 2),
             Point(real_facesize_x + real_facesize_width,
                   real_facesize_y + real_facesize_height / 2), Scalar(255, 0, 0), 5, 8);


        Rect face_area(real_facesize_x, real_facesize_y, real_facesize_width,
                       real_facesize_height / 2);
        Mat faceROI = img_gray(face_area);

        //-- In each face, detect eyes
        ((CascadeClassifier *) cascadeClassifier_eye)->detectMultiScale(faceROI, eyes, scale,
                                                                        neighbor,
                                                                        0 |
                                                                        CASCADE_SCALE_IMAGE,
                                                                        Size(30, 30));
        int sum_x = 0;
        int is_awake = 1;
        if (eyes.size() == 1) {
            float standard = real_facesize_width / 3;
            if (eyes[0].x + eyes[0].width / 2 < standard) {
                result_x =
                        real_facesize_x + eyes[0].x + eyes[0].width / 2 + real_facesize_width / 5;
            } else {
                result_x =
                        real_facesize_x + eyes[0].x + eyes[0].width / 2 - real_facesize_width / 5;
            }
            circle(img_result, Point(result_x, real_facesize_y + eyes[0].y + eyes[0].height / 2),
                   10, Scalar(255, 255, 255), 5, 8, 0);
        } else if (eyes.size() == 2) {
            for (short i = 0; i < eyes.size(); i++) {
                eye_center = Point(real_facesize_x + eyes[i].x + eyes[i].width / 2,
                                   real_facesize_y + eyes[i].y + eyes[i].height / 2);
                sum_x += eye_center.x;
            }
            result_x = sum_x / 2;
            circle(img_result, Point(result_x, real_facesize_y + eyes[0].y + eyes[0].height / 2),
                   10, Scalar(0, 0, 0), 5, 8, 0);
        } else {
            circle(img_result, Point(result_x, real_facesize_y), 10, Scalar(0, 255, 0), 5, 8, 0);
            is_awake = 0;
        }

        int rtn = (result_x - real_facesize_x - real_facesize_width / 2) / real_facesize_width * 50;

        int bias = center.x + 1000;
        putText(img_result, to_string(rtn), Point(20,40), 1, 4,Scalar(255, 255, 255), 3, 8);
        putText(img_result, to_string(bias-1000), Point(20,70), 1, 4,Scalar(255, 255, 255), 3, 8);
        return  is_awake*100000000+bias * 10000 + rtn+5000;
    }

    flip(img_resize, img_resize, 1);
    ((CascadeClassifier *) cascadeClassifier_side_face)->detectMultiScale(img_resize,
                                                                          side_faces, scale,
                                                                          neighbor,
                                                                          0 |
                                                                          CASCADE_SCALE_IMAGE,
                                                                          msize); //1.1 2
//    __android_log_print(ANDROID_LOG_DEBUG, (char *) "native-lib :: ",
//                        (char *) "side_face %d found ", side_faces.size());
    for (int i = 0; i < side_faces.size(); i++) {
        double real_facesize_x =
                (img_resize.cols - side_faces[i].x - side_faces[i].width) / resizeRatio;
        double real_facesize_y = side_faces[i].y / resizeRatio;
        double real_facesize_width = side_faces[i].width / resizeRatio;
        double real_facesize_height = side_faces[i].height / resizeRatio;

        Point center(real_facesize_x + real_facesize_width / 2,
                     real_facesize_y + real_facesize_height / 2);
        ellipse(img_result, center,
                Size(real_facesize_width / 2, real_facesize_height / 2), 0,
                0, 360,
                Scalar(255, 0, 255), 30, 8, 0);
        line(img_result, Point(real_facesize_x, real_facesize_y + real_facesize_height / 2),
             Point(real_facesize_x + real_facesize_width,
                   real_facesize_y + real_facesize_height / 2), Scalar(0, 0, 255), 5, 8);


        Rect face_area(real_facesize_x, real_facesize_y, real_facesize_width,
                       real_facesize_height / 2);
        Mat faceROI = img_gray(face_area);

        ((CascadeClassifier *) cascadeClassifier_eye)->detectMultiScale(faceROI, eyes,
                                                                        scale, neighbor,
                                                                        0 |
                                                                        CASCADE_SCALE_IMAGE,
                                                                        Size(30, 30));
        int sum_x = 0;
        int is_awake = 1;
        if (eyes.size() == 1) {
            float standard = real_facesize_width / 3 * 2;
            if (eyes[0].x + eyes[0].width / 2 < standard) {
                result_x =
                        real_facesize_x + eyes[0].x + eyes[0].width / 2 + real_facesize_width / 5;
            } else {
                result_x =
                        real_facesize_x + eyes[0].x + eyes[0].width / 2 - real_facesize_width / 5;
            }
            circle(img_result, Point(result_x, real_facesize_y + eyes[0].y + eyes[0].height / 2),
                   10, Scalar(255, 255, 255), 5, 8, 0);
        } else if (eyes.size() == 2) {
            for (short i = 0; i < eyes.size(); i++) {
                eye_center = Point(real_facesize_x + eyes[i].x + eyes[i].width / 2,
                                   real_facesize_y + eyes[i].y + eyes[i].height / 2);
                sum_x += eye_center.x;
            }
            result_x = sum_x / 2;
            circle(img_result, Point(result_x, real_facesize_y + eyes[0].y + eyes[0].height / 2),
                   10, Scalar(0, 0, 0), 5, 8, 0);
        } else {
            circle(img_result, Point(result_x, real_facesize_y), 10, Scalar(0, 255, 0), 5, 8, 0);
            is_awake = 0;
        }

        int rtn = (result_x - real_facesize_x - real_facesize_width / 2) / real_facesize_width * 50;

        int bias = center.x + 1000;
        putText(img_result, to_string(rtn), Point(20,40), 1, 4,Scalar(255, 255, 255), 3, 8);
        putText(img_result, to_string(bias-1000), Point(20,70), 1, 4,Scalar(255, 255, 255), 3, 8);
        return  is_awake*100000000+bias * 10000 + rtn+5000;
    }
    flip(img_resize, img_resize, 1);
//-- Detect faces
    ((CascadeClassifier *) cascadeClassifier_face)->detectMultiScale(img_resize, faces, 1.3,
                                                                     neighbor,
                                                                     0 | CASCADE_SCALE_IMAGE,
                                                                     msize); //1.1 2


//    __android_log_print(ANDROID_LOG_DEBUG, (char *) "native-lib :: ",
//                        (char *) "face %d found ", faces.size());

    for (int i = 0; i < faces.size(); i++) {
        double real_facesize_x = faces[i].x / resizeRatio;
        double real_facesize_y = faces[i].y / resizeRatio;
        double real_facesize_width = faces[i].width / resizeRatio;
        double real_facesize_height = faces[i].height / resizeRatio;

        Point center(real_facesize_x + real_facesize_width / 2,
                     real_facesize_y + real_facesize_height / 2);
        ellipse(img_result, center, Size(real_facesize_width / 2, real_facesize_height / 2),
                0,
                0, 360,
                Scalar(120, 80, 120), 30, 8, 0);
//        line(img_result, Point(real_facesize_x, real_facesize_y + real_facesize_height / 2),
//             Point(real_facesize_x + real_facesize_width,
//                   real_facesize_y + real_facesize_height / 2), Scalar(255, 0, 255), 5, 8);

        Rect face_area(real_facesize_x, real_facesize_y, real_facesize_width,
                       real_facesize_height / 2);
        Mat faceROI = img_gray(face_area);

        ((CascadeClassifier *) cascadeClassifier_eye)->detectMultiScale(faceROI, eyes, scale,
                                                                        neighbor,
                                                                        0 | CASCADE_SCALE_IMAGE,
                                                                        Size(30, 30));
        int sum_x = 0;
        int is_awake = 1;
        if (eyes.size() == 1) {
            float standard = real_facesize_width / 2;
            if (eyes[0].x + eyes[0].width / 2 < standard) {
                result_x =
                        real_facesize_x + eyes[0].x + eyes[0].width / 2 + real_facesize_width / 5;
            } else {
                result_x =
                        real_facesize_x + eyes[0].x + eyes[0].width / 2 - real_facesize_width / 5;
            }
            circle(img_result, Point(result_x, real_facesize_y + eyes[0].y + eyes[0].height / 2),
                   10, Scalar(255, 255, 255), 5, 8, 0);
        } else if (eyes.size() == 2) {
            for (short i = 0; i < eyes.size(); i++) {
                eye_center = Point(real_facesize_x + eyes[i].x + eyes[i].width / 2,
                                   real_facesize_y + eyes[i].y + eyes[i].height / 2);
                sum_x += eye_center.x;
            }
            result_x = sum_x / 2;
            circle(img_result, Point(result_x, real_facesize_y + eyes[0].y + eyes[0].height / 2),
                   10, Scalar(0, 0, 0), 5, 8, 0);
        } else {
            circle(img_result, Point(result_x, real_facesize_y), 10, Scalar(0, 255, 0), 5, 8, 0);
            is_awake = 0;
        }
        int rtn = (result_x - real_facesize_x - real_facesize_width / 2) / real_facesize_width * 50;

        int bias = center.x + 1000;
        putText(img_result, to_string(rtn), Point(20,40), 1, 4,Scalar(255, 255, 255), 3, 8);
        putText(img_result, to_string(bias-1000), Point(20,70), 1, 4,Scalar(255, 255, 255), 3, 8);
        return  is_awake*100000000+bias * 10000 + rtn+5000;

    }
    putText(img_result, "NOT FOUND", Point(20,40), 1, 4,Scalar(0, 0, 255), 3, 8);
    return 120001000;
}

JNIEXPORT jint JNICALL Java_com_example_capston_CamService_detect
        (JNIEnv *env, jobject type, jlong cascadeClassifier_face, jlong cascadeClassifier_side_face,
         jlong cascadeClassifier_eye, jlong matAddrInput, jlong matAddrResult) {
    Mat &img_input = *(Mat *) matAddrInput;
    Mat &img_result = *(Mat *) matAddrResult;
    std::vector<Rect> faces;
    std::vector<Rect> eyes;
    Mat img_gray;

    equalizeHist(img_input, img_gray);
    img_result = img_gray.clone();
    Mat img_resize;
    float resizeRatio = resize(img_gray, img_resize, 640);



    std::vector<Rect> side_faces;

    ((CascadeClassifier *) cascadeClassifier_side_face)->detectMultiScale(img_resize,
                                                                          side_faces, scale,
                                                                          neighbor,
                                                                          0 |
                                                                          CASCADE_SCALE_IMAGE,
                                                                          msize); //1.1 2
    __android_log_print(ANDROID_LOG_DEBUG, (char *) "native-lib :: ",
                        (char *) "side_face %d found ", side_faces.size());
    for (int i = 0; i < side_faces.size(); i++) {
        double real_facesize_x = side_faces[i].x / resizeRatio;
        double real_facesize_y = side_faces[i].y / resizeRatio;
        double real_facesize_width = side_faces[i].width / resizeRatio;
        double real_facesize_height = side_faces[i].height / resizeRatio;

        Point center(real_facesize_x + real_facesize_width / 2,
                     real_facesize_y + real_facesize_height / 2);
        ellipse(img_result, center,
                Size(real_facesize_width / 2, real_facesize_height / 2),
                0,
                0, 360,
                Scalar(255, 0, 255), 30, 8, 0);
        line(img_result, Point(real_facesize_x, real_facesize_y + real_facesize_height / 2),
             Point(real_facesize_x + real_facesize_width,
                   real_facesize_y + real_facesize_height / 2), Scalar(255, 0, 255), 5, 8);


        Rect face_area(real_facesize_x, real_facesize_y, real_facesize_width,
                       real_facesize_height / 2);
        Mat faceROI = img_gray(face_area);

        //-- In each face, detect eyes
        ((CascadeClassifier *) cascadeClassifier_eye)->detectMultiScale(faceROI, eyes, scale,
                                                                        neighbor,
                                                                        0 |
                                                                        CASCADE_SCALE_IMAGE,
                                                                        Size(30, 30));
        int sum_x = 0;
        int is_awake = 1;
        if (eyes.size() == 1) {
            float standard = real_facesize_width / 3;
            if (eyes[0].x + eyes[0].width / 2 < standard) {
                result_x =
                        real_facesize_x + eyes[0].x + eyes[0].width / 2 + real_facesize_width / 5;
            } else {
                result_x =
                        real_facesize_x + eyes[0].x + eyes[0].width / 2 - real_facesize_width / 5;
            }
            circle(img_result, Point(result_x, real_facesize_y + eyes[0].y + eyes[0].height / 2),
                   10, Scalar(255, 255, 255), 5, 8, 0);
        } else if (eyes.size() == 2) {
            for (short i = 0; i < eyes.size(); i++) {
                eye_center = Point(real_facesize_x + eyes[i].x + eyes[i].width / 2,
                                   real_facesize_y + eyes[i].y + eyes[i].height / 2);
                sum_x += eye_center.x;
            }
            result_x = sum_x / 2;
            circle(img_result, Point(result_x, real_facesize_y + eyes[0].y + eyes[0].height / 2),
                   10, Scalar(0, 0, 0), 5, 8, 0);
        } else {
            circle(img_result, Point(result_x, real_facesize_y), 10, Scalar(0, 255, 0), 5, 8, 0);
            is_awake = 0;
        }

        int rtn = (result_x - real_facesize_x - real_facesize_width / 2) / real_facesize_width * 100;
        int bias = (center.x>1250 ? 1:(center.x<950?2:0));
        return bias * 100000 + (rtn+5000) * 10 + is_awake;
    }

    flip(img_resize, img_resize, 1);
    ((CascadeClassifier *) cascadeClassifier_side_face)->detectMultiScale(img_resize,
                                                                          side_faces, scale,
                                                                          neighbor,
                                                                          0 |
                                                                          CASCADE_SCALE_IMAGE,
                                                                          msize); //1.1 2
    __android_log_print(ANDROID_LOG_DEBUG, (char *) "native-lib :: ",
                        (char *) "side_face %d found ", side_faces.size());
    for (int i = 0; i < side_faces.size(); i++) {
        double real_facesize_x =
                (img_resize.cols - side_faces[i].x - side_faces[i].width) / resizeRatio;
        double real_facesize_y = side_faces[i].y / resizeRatio;
        double real_facesize_width = side_faces[i].width / resizeRatio;
        double real_facesize_height = side_faces[i].height / resizeRatio;

        Point center(real_facesize_x + real_facesize_width / 2,
                     real_facesize_y + real_facesize_height / 2);
        ellipse(img_result, center,
                Size(real_facesize_width / 2, real_facesize_height / 2), 0,
                0, 360,
                Scalar(255, 0, 255), 30, 8, 0);
        line(img_result, Point(real_facesize_x, real_facesize_y + real_facesize_height / 2),
             Point(real_facesize_x + real_facesize_width,
                   real_facesize_y + real_facesize_height / 2), Scalar(255, 0, 255), 5, 8);


        Rect face_area(real_facesize_x, real_facesize_y, real_facesize_width,
                       real_facesize_height / 2);
        Mat faceROI = img_gray(face_area);

        ((CascadeClassifier *) cascadeClassifier_eye)->detectMultiScale(faceROI, eyes,
                                                                        scale, neighbor,
                                                                        0 |
                                                                        CASCADE_SCALE_IMAGE,
                                                                        Size(30, 30));
        int sum_x = 0;
        int is_awake = 1;
        if (eyes.size() == 1) {
            float standard = real_facesize_width / 3 * 2;
            if (eyes[0].x + eyes[0].width / 2 < standard) {
                result_x =
                        real_facesize_x + eyes[0].x + eyes[0].width / 2 + real_facesize_width / 5;
            } else {
                result_x =
                        real_facesize_x + eyes[0].x + eyes[0].width / 2 - real_facesize_width / 5;
            }
            circle(img_result, Point(result_x, real_facesize_y + eyes[0].y + eyes[0].height / 2),
                   10, Scalar(255, 255, 255), 5, 8, 0);
        } else if (eyes.size() == 2) {
            for (short i = 0; i < eyes.size(); i++) {
                eye_center = Point(real_facesize_x + eyes[i].x + eyes[i].width / 2,
                                   real_facesize_y + eyes[i].y + eyes[i].height / 2);
                sum_x += eye_center.x;
            }
            result_x = sum_x / 2;
            circle(img_result, Point(result_x, real_facesize_y + eyes[0].y + eyes[0].height / 2),
                   10, Scalar(0, 0, 0), 5, 8, 0);
        } else {
            circle(img_result, Point(result_x, real_facesize_y), 10, Scalar(0, 255, 0), 5, 8, 0);
            is_awake = 0;
        }

        int rtn = (result_x - real_facesize_x - real_facesize_width / 2) / real_facesize_width * 100;
        int bias = (center.x>1250 ? 2:(center.x<950?1:0));
        return  bias * 100000 + (rtn+5000) * 10 + is_awake;
    }
    flip(img_resize, img_resize, 1);
//-- Detect faces
    ((CascadeClassifier *) cascadeClassifier_face)->detectMultiScale(img_resize, faces, 1.3,
                                                                     neighbor,
                                                                     0 | CASCADE_SCALE_IMAGE,
                                                                     msize); //1.1 2


    __android_log_print(ANDROID_LOG_DEBUG, (char *) "native-lib :: ",
                        (char *) "face %d found ", faces.size());

    for (int i = 0; i < faces.size(); i++) {
        double real_facesize_x = faces[i].x / resizeRatio;
        double real_facesize_y = faces[i].y / resizeRatio;
        double real_facesize_width = faces[i].width / resizeRatio;
        double real_facesize_height = faces[i].height / resizeRatio;

        Point center(real_facesize_x + real_facesize_width / 2,
                     real_facesize_y + real_facesize_height / 2);
        ellipse(img_result, center, Size(real_facesize_width / 2, real_facesize_height / 2),
                0,
                0, 360,
                Scalar(255, 0, 255), 30, 8, 0);
        line(img_result, Point(real_facesize_x, real_facesize_y + real_facesize_height / 2),
             Point(real_facesize_x + real_facesize_width,
                   real_facesize_y + real_facesize_height / 2), Scalar(255, 0, 255), 5, 8);

        Rect face_area(real_facesize_x, real_facesize_y, real_facesize_width,
                       real_facesize_height / 2);
        Mat faceROI = img_gray(face_area);

        ((CascadeClassifier *) cascadeClassifier_eye)->detectMultiScale(faceROI, eyes, scale,
                                                                        neighbor,
                                                                        0 | CASCADE_SCALE_IMAGE,
                                                                        Size(30, 30));
        int sum_x = 0;
        int is_awake = 1;
        if (eyes.size() == 1) {
            float standard = real_facesize_width / 2;
            if (eyes[0].x + eyes[0].width / 2 < standard) {
                result_x =
                        real_facesize_x + eyes[0].x + eyes[0].width / 2 + real_facesize_width / 5;
            } else {
                result_x =
                        real_facesize_x + eyes[0].x + eyes[0].width / 2 - real_facesize_width / 5;
            }
            circle(img_result, Point(result_x, real_facesize_y + eyes[0].y + eyes[0].height / 2),
                   10, Scalar(255, 255, 255), 5, 8, 0);
        } else if (eyes.size() == 2) {
            for (short i = 0; i < eyes.size(); i++) {
                eye_center = Point(real_facesize_x + eyes[i].x + eyes[i].width / 2,
                                   real_facesize_y + eyes[i].y + eyes[i].height / 2);
                sum_x += eye_center.x;
            }
            result_x = sum_x / 2;
            circle(img_result, Point(result_x, real_facesize_y + eyes[0].y + eyes[0].height / 2),
                   10, Scalar(0, 0, 0), 5, 8, 0);
        } else {
            circle(img_result, Point(result_x, real_facesize_y), 10, Scalar(0, 255, 0), 5, 8, 0);
            is_awake = 0;
        }
        int rtn = (result_x - real_facesize_x - real_facesize_width / 2) / real_facesize_width * 50;
        int bias = (center.x>1250 ? 1:(center.x<950?2:0));
        return bias * 100000 + (rtn+5000) * 10 + is_awake;
    }

    return 60001;
}
}