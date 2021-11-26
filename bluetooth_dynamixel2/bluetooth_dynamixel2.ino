/*******************************************************************************
* Copyright 2016 ROBOTIS CO., LTD.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/

#include <Dynamixel2Arduino.h>

// Please modify it to suit your hardware.
#if defined(ARDUINO_AVR_UNO) || defined(ARDUINO_AVR_MEGA2560) // When using DynamixelShield
  #include <SoftwareSerial.h>
  SoftwareSerial soft_serial(7, 8); // DYNAMIXELShield UART RX/TX
  #define DXL_SERIAL   Serial
  #define DEBUG_SERIAL soft_serial
  const uint8_t DXL_DIR_PIN = 2; // DYNAMIXEL Shield DIR PIN
#elif defined(ARDUINO_SAM_DUE) // When using DynamixelShield
  #define DXL_SERIAL   Serial
  #define DEBUG_SERIAL SerialUSB
  const uint8_t DXL_DIR_PIN = 2; // DYNAMIXEL Shield DIR PIN
#elif defined(ARDUINO_SAM_ZERO) // When using DynamixelShield
  #define DXL_SERIAL   Serial1
  #define DEBUG_SERIAL SerialUSB
  const uint8_t DXL_DIR_PIN = 2; // DYNAMIXEL Shield DIR PIN
#elif defined(ARDUINO_OpenCM904) // When using official ROBOTIS board with DXL circuit.
  #define DXL_SERIAL   Serial3 //OpenCM9.04 EXP Board's DXL port Serial. (Serial1 for the DXL port on the OpenCM 9.04 board)
  #define DEBUG_SERIAL Serial
  const uint8_t DXL_DIR_PIN = 22; //OpenCM9.04 EXP Board's DIR PIN. (28 for the DXL port on the OpenCM 9.04 board)
#elif defined(ARDUINO_OpenCR) // When using official ROBOTIS board with DXL circuit.
  // For OpenCR, there is a DXL Power Enable pin, so you must initialize and control it.
  // Reference link : https://github.com/ROBOTIS-GIT/OpenCR/blob/master/arduino/opencr_arduino/opencr/libraries/DynamixelSDK/src/dynamixel_sdk/port_handler_arduino.cpp#L78
  #define DXL_SERIAL   Serial3
  #define DEBUG_SERIAL Serial
  const uint8_t DXL_DIR_PIN = 84; // OpenCR Board's DIR PIN.    
#else // Other boards when using DynamixelShield
  #define DXL_SERIAL   Serial1
  #define DEBUG_SERIAL Serial
  const uint8_t DXL_DIR_PIN = 2; // DYNAMIXEL Shield DIR PIN
#endif
 


const uint8_t DXL_ID[4] = {1, 4, 2, 3};
const float DXL_PROTOCOL_VERSION1 = 1.0;
const float DXL_PROTOCOL_VERSION2 = 2.0;

int first, second, len;
int m_angle[4] = {1000, 1000, 1000, 1000};

String msg, mode_select, motor_dir;
int motor_select;
boolean is_position = 1;
Dynamixel2Arduino dxl1(DXL_SERIAL, DXL_DIR_PIN);
Dynamixel2Arduino dxl2(DXL_SERIAL, DXL_DIR_PIN);

//This namespace is required to use Control table item names
using namespace ControlTableItem;

void position_setup(){
  for(char i=0; i<2; i++){
    dxl2.ping(DXL_ID[i]);
    dxl2.torqueOff(DXL_ID[i]);
    dxl2.setOperatingMode(DXL_ID[i], OP_POSITION);
    dxl2.torqueOn(DXL_ID[i]);
    dxl2.writeControlTableItem(PROFILE_VELOCITY, DXL_ID[3], 30);
  }
  for(char i=2; i<4; i++){
    dxl1.ping(DXL_ID[i]);
    dxl1.torqueOff(DXL_ID[i]);
    dxl1.setOperatingMode(DXL_ID[i], OP_POSITION);
    dxl1.torqueOn(DXL_ID[i]);
    dxl1.writeControlTableItem(PROFILE_VELOCITY, DXL_ID[i], 1);
  }
}

String get_angle(){
  for(int i=0; i<3; i++){
    m_angle[i] = dxl1.getPresentPosition(DXL_ID[i]);
  }
  m_angle[3] = dxl2.getPresentPosition(DXL_ID[3]);

  return String(m_angle[0]) + " " + String(m_angle[1]) + " " + String(m_angle[2]) + " " + String(m_angle[3]);
}

void velocity_setup(){
  for(char i=0; i<2; i++){
    dxl2.ping(DXL_ID[i]);
    dxl2.torqueOff(DXL_ID[i]);
    dxl2.setOperatingMode(DXL_ID[i], OP_VELOCITY);
    dxl2.torqueOn(DXL_ID[i]);
  }
  for(char i=2; i<4; i++){
    dxl1.ping(DXL_ID[i]);
    dxl1.torqueOff(DXL_ID[i]);
    dxl1.setOperatingMode(DXL_ID[i], OP_VELOCITY);
    dxl1.torqueOn(DXL_ID[i]);
  }
}

void VMDforDistance(int motor_select, String motor_dir){
  int dir = motor_dir.equals("c")?1:(motor_dir.equals("C")?-1:0);
  switch(motor_select){
      case 2:
        dxl1.setGoalVelocity(DXL_ID[2], 800*dir);//1044*(dir*dir-dir)/2+10*dir);
        break;
      case 1:
        dxl2.setGoalVelocity(DXL_ID[0], dir*100);
        break;
      case 3:
        dxl1.setGoalVelocity(DXL_ID[3], 1000*dir);//(200+612*(dir-1))*dir);
        break;
      case 4:
        dxl2.setGoalVelocity(DXL_ID[1], dir*10);
        break;
    }
}

void VMDforTracking(int motor_select, String motor_dir){
  int velocity = motor_dir.toInt();
  if (velocity < 5 && velocity > -5) velocity = 0;
  else velocity = velocity>0?velocity:1023-velocity;
  dxl1.setGoalVelocity(DXL_ID[0], velocity);
}

void PMD(){
  int k_present_position = dxl1.getPresentPosition(DXL_ID[0]);
  
  dxl1.setGoalPosition(DXL_ID[0], 100);

  int i_present_position = 0; 
  
  while (abs(200 - i_present_position) > 10)
  {
    i_present_position = dxl1.getPresentPosition(DXL_ID[0]);
  }
  delay(500);
  dxl1.setGoalPosition(DXL_ID[0], 2000);
  while (abs(1000 - i_present_position) > 10)
  {
    i_present_position = dxl1.getPresentPosition(DXL_ID[0]);
  }
  delay(500);
  dxl1.setGoalPosition(DXL_ID[0], 1100);
}


void setup() {
  // put your setup code here, to run once:
  
  // Use UART port of DYNAMIXEL Shield to debug.
  DEBUG_SERIAL.begin(115200);
  
  // Set Port baudrate to 57600bps. This has to match with DYNAMIXEL baudrate.
  dxl1.begin(1000000);
  dxl2.begin(1000000);
  // Set Port Protocol Version. This has to match with DYNAMIXEL protocol version.
  dxl1.setPortProtocolVersion(DXL_PROTOCOL_VERSION1);
  dxl2.setPortProtocolVersion(DXL_PROTOCOL_VERSION2);
  // Get DYNAMIXEL information
  

  // Turn off torque when configuring items in EEPROM area
  
  //Serial.begin(9600);
  Serial1.begin(115200);
  position_setup();
  for(char i=0; i<2; i++){
    dxl2.setGoalPosition(DXL_ID[i], m_angle[i]);
  }
  for(char i=2; i<4; i++){
    dxl1.setGoalPosition(DXL_ID[i], m_angle[i]);
  }
}



void loop() {
//  velocity_setup();
//  dxl1.setGoalVelocity(DXL_ID[1], 100);
  while(Serial1.available()) {
    msg = Serial1.readStringUntil('\n');
    msg.trim();
    first = msg.indexOf(" ");
    second = msg.indexOf(" ", first+1);
    len = msg.length();

    mode_select = msg.substring(0, first);
    motor_select = msg.substring(first+1, second).toInt();
    motor_dir = msg.substring(second+1, len);
    
  }

  if(mode_select.equals("Distance")){
    if (is_position == 1){
      velocity_setup();
      is_position = 0;
    }
    if (motor_select == 0){
      position_setup();
      msg = get_angle();
      is_position = 1;
      Serial1.println(msg);
      mode_select = "";
    }
    VMDforDistance(motor_select, motor_dir);
  }
  
  else if(mode_select.equals("Test")){
    if (is_position == 0){
      position_setup();
      is_position = 1;
    }
    PMD();
    mode_select = "";
  }
  
  else if(mode_select.equals("Tracking")){
    if (is_position == 1){
      velocity_setup();
      is_position = 0;
    }
    VMDforTracking(motor_select, motor_dir);
  }
}
    
      
