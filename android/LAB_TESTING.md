# PlanD Mk1 1.2：iPhoneを使う通信・救助記録の実験

実装：個人への救助・確認記録、訂正履歴、Room v1→v2移行、version 2 CARE_*通信、開発用WSS通信。
通常のNearby通信とversion 1の安否情報は継続します。旧版は救助記録を表示できません。

- 実験用PWA・サーバー・起動手順：[lab/README.md](C:/Users/TAKUMI/projects/planD/lab/README.md)
- 試験結果：[RESULTS.md](C:/Users/TAKUMI/projects/planD/artifacts/lan-rescue/RESULTS.md)
- 実験APK：[PlanD-Mk1-1.2.0-debug.apk](C:/Users/TAKUMI/projects/planD/artifacts/PlanD-Mk1-1.2.0-debug.apk)

実験用WSSはdebug版のみ開始可能です。公開CAはsrc/debugにだけ置き、release版には含めません。
アプリ更新時は既存の住民情報を保持して救助記録の表を追加します。アンインストールは不要です。

実機2台以上のNearby無線試験、長時間省電力、iOSネイティブ版は今回の実証とは別です。
