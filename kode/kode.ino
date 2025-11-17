#include <ESP8266WiFi.h>
#include <Firebase_ESP_Client.h>
#include <TinyGPSPlus.h>
#include <SoftwareSerial.h>
#include <addons/TokenHelper.h>
#include <addons/RTDBHelper.h>

// WiFi & Firebase
#define WIFI_SSID "Free"
#define WIFI_PASSWORD "123456789"
#define API_KEY "AIzaSyBtQRpzlN2tryHwDl_o8DSmAkJgJXZl3bc"
#define DATABASE_URL "https://mototrack-33904-default-rtdb.firebaseio.com/"
#define DATABASE_SECRET "Z7uVnHu01YarMKdOHUjGbHyVsMkJzuGoHqVMGZa6"

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// GPS
static const int RXPin = D6, TXPin = D7;
static const uint32_t GPSBaud = 9600;
SoftwareSerial gpsSerial(RXPin, TXPin);
TinyGPSPlus gps;

unsigned long lastGPSData = 0;
bool gpsDataReceived = false;

// Sensor & Aktuator
#define VIBRATION_SENSOR D5
#define BUZZER D0
#define RELAY1 D1
#define RELAY2 D2

const int TIME_OFFSET = 9;

bool statusGetar = false;
unsigned long lastSend = 0;
const unsigned long SEND_INTERVAL = 5000;

// ----------------------
// Setup
// ----------------------
void setup() {
  Serial.begin(115200);
  gpsSerial.begin(GPSBaud);
  delay(1500);

  pinMode(VIBRATION_SENSOR, INPUT_PULLUP);
  pinMode(BUZZER, OUTPUT);
  pinMode(RELAY1, OUTPUT);
  pinMode(RELAY2, OUTPUT);

  digitalWrite(BUZZER, LOW);
  digitalWrite(RELAY1, LOW);
  digitalWrite(RELAY2, LOW);

  Serial.println("Menghubungkan WiFi...");
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }
  Serial.println("\nWiFi Terhubung.");

  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;
  config.signer.tokens.legacy_token = DATABASE_SECRET;

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  Serial.println("Setup selesai.");
}

// ----------------------
// Kirim lokasi
// ----------------------
void kirimLokasi(float lat, float lon) {
  char timeStr[10];

  int hour = gps.time.hour() + TIME_OFFSET;
  if (hour >= 24) hour -= 24;
  sprintf(timeStr, "%02d:%02d:%02d", hour, gps.time.minute(), gps.time.second());

  Firebase.RTDB.setFloat(&fbdo, "trackingMap/latitude", lat);
  Firebase.RTDB.setFloat(&fbdo, "trackingMap/longitude", lon);
  Firebase.RTDB.setString(&fbdo, "trackingMap/time", timeStr);

  Serial.printf("Lokasi terkirim | LAT: %.6f | LON: %.6f\n", lat, lon);
}

// ----------------------
// Loop utama
// ----------------------
void loop() {

  // --------- GPS Tetap Diproses ----------
  while (gpsSerial.available()) {
    gps.encode(gpsSerial.read());
    gpsDataReceived = true;
    lastGPSData = millis();
  }

  // Jika 5 detik tidak ada data NMEA = RX/TX terlepas
  if (!gpsDataReceived && millis() - lastGPSData > 5000) {
    Serial.println("⚠️ GPS TIDAK TERHUBUNG (cek kabel RX/TX)");
  }
  
  // setelah ada 1 data, reset flag agar bisa cek lagi
  if (gpsDataReceived && millis() - lastGPSData > 5000) {
    gpsDataReceived = false;
  }

  // --------- Sensor Getar ---------
  int gv = digitalRead(VIBRATION_SENSOR);
  if (gv == HIGH && !statusGetar) {
    statusGetar = true;
    digitalWrite(BUZZER, HIGH);
    Firebase.RTDB.setBool(&fbdo, "Motor/statusGetar", true);
    Serial.println("⚠️ Getaran terdeteksi!");
  } 
  else if (gv == LOW && statusGetar) {
    statusGetar = false;
    digitalWrite(BUZZER, LOW);
    Firebase.RTDB.setBool(&fbdo, "Motor/statusGetar", false);
    Serial.println("Getaran normal.");
  }

  // --------- Relay dari Firebase ---------
  if (Firebase.RTDB.getBool(&fbdo, "Motor/statusKelistrikan"))
      digitalWrite(RELAY1, fbdo.boolData() ? HIGH : LOW);

  if (Firebase.RTDB.getBool(&fbdo, "Motor/statusStarter"))
      digitalWrite(RELAY2, fbdo.boolData() ? HIGH : LOW);

  // --------- Kirim lokasi jika FIX ---------
  if (gps.location.isValid() && gps.satellites.value() >= 4) {
    if (millis() - lastSend > SEND_INTERVAL) {
      lastSend = millis();
      kirimLokasi(gps.location.lat(), gps.location.lng());
    }
  } 
  else {
    Serial.printf("⌛ GPS belum fix | Satelit: %d\n", gps.satellites.value());
  }

  delay(200);
}
