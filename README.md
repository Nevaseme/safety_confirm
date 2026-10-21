# PlanD

災害時の安否や救助の記録を端末間で共有するアプリ。開発中。

[地図デモを見る](https://nevaseme.github.io/safety_confirm/)

- [Androidアプリ](android/)
- [PWA・中継サーバーの実験](lab/)
- [設計・開発資料](docs/)

## ビルド

`android/` をAndroid Studioで開く。最小SDKは24。

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```
