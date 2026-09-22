<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white"/>
  <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/TensorFlow%20Lite-FF6F00?style=for-the-badge&logo=tensorflow&logoColor=white"/>
</p>

## Proje Hakkında

Bu repo, termal kamera görüntüleri üzerinde çalışan yapay zeka modellerini kullanıcıya sunan **Kotlin tabanlı Android mobil uygulamasının** kaynak kodlarını içerir. Uygulama, önceden eğitilmiş derin öğrenme modellerini entegre ederek kullanıcının görüntü üzerinde manuel yara alanı seçimi yapmasına, yara boyutunu hesaplamasına ve yapay zeka destekli analiz sonuçlarını görüntülemesine olanak tanır.

>Bu uygulamada kullanılan modellerin eğitim kodları ayrı bir repodadır ---> https://github.com/zeynepakyol2/Termal_Kamera

## Özellikler

- Termal kamera/galeri görüntüsü yükleme
- Görüntü üzerinde manuel yara alanı seçimi
- Seçilen alana göre yara boyutu hesaplama
- Entegre edilmiş modellerle:
  - Yara var/yok tespiti
  - Yara evresi tahmini
  - Risk seviyesi tahmini (yara yoksa)
- Analiz sonuçlarının uygulama içinde görselleştirilmesi

## Kullanılan Modeller

Uygulama, TensorFlow ile eğitilip mobil kullanım için optimize edilmiş (TensorFlow Lite) üç modeli entegre eder:

| Model | Görev |
|---|---|
| Model 1 | Yara Var / Yok Sınıflandırması |
| Model 2 | Yara Evresi Tahmini |
| Model 3 | Risk Seviyesi Tahmini |


## Kullanılan Teknolojiler

- **Dil:** Kotlin
- **Platform:** Android
- **Model Entegrasyonu:** TensorFlow Lite
- **Görüntü İşleme:** Android Camera/Gallery API, özel dokunmatik alan seçim bileşeni

## Kurulum

```bash
# Depoyu klonlayın
git clone https://github.com/zeynepakyol2/TermalKameraMobil.git
```

1. Projeyi **Android Studio** ile açın
2. Gradle senkronizasyonunun tamamlanmasını bekleyin
3. Bir Android cihaz veya emülatör seçip uygulamayı çalıştırın

## Kullanım

1. Uygulamayı açın ve termal görüntüyü yükleyin
2. Yapay zeka modeli görüntüyü otomatik analiz eder
3. Gerekirse görüntü üzerinde manuel olarak yara alanını işaretleyin
4. Yara boyutu ve analiz sonucu (tespit, evre, risk seviyesi) ekranda gösterilir

