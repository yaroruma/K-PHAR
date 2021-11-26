# K-PHAR

## Brief
캡스톤 디자인(졸업작품) 코드입니다. 휴대폰 카메라와 연동해 사용자의 얼굴을 트래킹할 수 있는 로봇팔을 만들었습니다. 얼굴인식을 위한 안드로이드 애플리케이션과 로봇팔 동작을 위한 아두이노 코드로 이루어져 있습니다.

## Application
openCV를 이용해 사용자의 얼굴인식. 얼굴과 눈의 좌표를 이용해 각도 정보를 계산하고 블루투스를 통해 아두이노와 통신. 백그라운드 환경에서 카메라를 사용할 수 있도록 서비스 구현.

### Environment
 - Android Studio
 - minSdkVersion : 21
 - targetSdkVersion : 28
 - openCV 4.5.4 android SDK
 - NDK

### Classes
 - `MainActivity.java`   
    메인 화면 구성. 첫 구동 시 권한 관리. 블루투스 연결. cascade 파일 로드.
 - `Bluetooth.java`   
    블루투스 통신을 위한 클래스.
 - `DistanceSettings.java`   
    사용자가 직접 거리를 조절할 수 있도록 화면 구성 및 통신.
 - `Settings.java`   
    사용환경을 확인할 수 있도록 카메라 영상을 화면에 출력.
 - `CamService.java`   
    백그라운드 서비스에서의 카메라 사용을 위한 클래스. 
    ```java
   private ImageReader.OnImageAvailableListener imageListener    // 카메라 프레임 당 실행되는 핸들러, detect 함수 실행
   private void createCaptureSession()                           // 카메라 캡쳐 화면 생성, 화면 비활성화
   private void startForeground(int detection)                   // 포그라운드 서비스 생성
    ```
 - `main.cpp`   
   C++기반의 실질적인 openCV 영상처리를 하는 클래스. NDK를 통해 java 클래스와 링크되어 사용.
   ```cpp
   JNIEXPORT jlong JNICALL Java_com_example_capston_MainActivity_loadCascade(...)     // cascade 파일 로드
   JNIEXPORT jint JNICALL Java_com_example_capston_Settings_detect(...)               // Settings 화면에서의 detect 함수
   JNIEXPORT jint JNICALL Java_com_example_capston_CamService_detect(...)             // 백그라운드 서비스에서의 detect 함수
   ```

### Flow map
![flow](https://user-images.githubusercontent.com/72549957/143595640-735a52e8-f180-4fe3-888e-fcc5e027c7d2.JPG)

### Face detection
백그라운드 환경에서 카메라와 블루투스를 사용하면서 영상처리를 하기 위해 리소스가 적은 haar-cascade 방식의 얼굴 검출 방법을 사용. 정면 얼굴과 왼쪽 얼굴, 눈을 읽는 cascade classifier를 이용해 사용자의 시선방향 검출.   
 1. 왼쪽 얼굴 판별. 
 2. 화면을 좌우반전 시키고 왼쪽 얼굴 판별.
 3. 정면 얼굴 판별.
 4. 1~3 중 얼굴이 검출됐다면 검출된 영역 안에서 눈 판별.
 5. 눈이 2개 검출된다면 미간 좌표와 얼굴 중점 좌표로 각도 계산.
 6. 눈이 하나만 검출된다면 이전 검출 정보와 비율을 통해 나머지 눈의 위치를 추정하고 5. 실행
 7. 얼굴은 검출되지만 눈이 검출되지 않는 상황이 이어질 경우 사용자가 잔다고 인식.


## Manipulator
![manipulator](https://user-images.githubusercontent.com/72549957/143595923-dfdd01b7-9b19-4c37-b5c9-0607ece17a5a.png)
   
애플리케이션에서 받아온 정보를 통해 Dinamixel 모터를 움직여 자세 조정.

### Spec
 - Board : Arduino MEGA
 - Motor driver : Dynamixel shield for Arduino
 - Motor : Dynamixel XL430-W250 x2, MX-64AT x1, MG9996R Servo motor x2
 - Bluetooth : HC-06

### `bluetooth_dynamixel2.ino`
requires - Dynamixel2Arduino Library
```
void loop() // 블루투스 통신, 모드에 따른 동작 실행
void VMDforDistance(int motor_select, String motor_dir) // 수동조작 시의 모터 컨트롤
void VMDforTracking(int motor_select, String motor_dir) // 트래킹 시의 모터 컨트롤
String get_angle() // 각 모터의 angle값 통신
```



    
