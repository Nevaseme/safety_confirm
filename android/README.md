# PlanD Mk1

安否・SOS、救助の対応記録、Nearbyでの共有、オフライン地図を備えたAndroidアプリ。

## ビルド

このフォルダーをAndroid Studioで開き、SDKを設定する。最小SDKは24。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`

## 使い方

名前と住居を登録し、地図を保存する。災害・訓練を開始して「無事」「SOS」を記録し、Nearbyで相手と共有する。

本人認証・電子署名は未実装。試験には架空の情報を使う。

- [試験手順](TESTING.md)
- [PWA・PCとの通信実験](../lab/README.md)
- [設計書](../docs/superpowers/plans/2026-10-19-integrated-design.md)（今後の仕様を含む）
