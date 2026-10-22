# Androidの試験手順

対象はリポジトリ内のandroid/。結果は対象版と確認方法を付けて[実装・試験状況](../docs/STATUS.md)へ記録する。新機能の受入条件は[実装計画](../docs/superpowers/plans/2026-10-08-pland-roadmap.md)にまとめる。

## 自動試験

1Aの採用デザインは `DesignFlowTest`、初回回答の保存・画面再作成は `FirstAnswerFlowTest` で確認する。メニューの「画面デモ」で同じ操作を手動確認できる。

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=io.github.nevaseme.plandmk1.DesignFlowTest'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=io.github.nevaseme.plandmk1.FirstAnswerFlowTest'
```

保存通知などCompose内の時間経過は `mainClock.advanceTimeBy()`、Roomや地図の読み込みは `waitUntil()` で待つ。[公式の同期手順](https://developer.android.com/develop/ui/compose/testing/synchronization)を参照。従来の画面文言・2層切替を前提にしたUI試験は、新画面に合わせた更新が必要。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

画面・保存・サービスの試験は専用エミュレーターで行う。テストは住民情報を書き込み、地図保存地域や無線設定を変更する。OfflineMapTestは毎回インターネット接続が必要で、保存済み地域を試験用地域へ置き換える。

```powershell
$env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat :app:connectedDebugAndroidTest
```

実際のエミュレーターIDに合わせて設定する。主な試験群：

| 試験 | 確認する内容 |
|---|---|
| SyncEngineTest / CareSyncTest / PlaceSyncTest / IncidentTest | 差分、保存後ACK、再送、訂正、災害と建物の分離 |
| SharingSessionTest / SharingFailureTest | 共有開始・停止、旧セッション、背景処理 |
| StorageTest / CareStorageTest / IncidentStorageTest / PlaceStorageTest | Room保存と移行 |
| ProfileFlowTest / CareFlowTest / IncidentFlowTest / BuildingFlowTest | 登録・安否・救助・建物の操作 |
| FeedbackFlowTest / NavigationFlowTest / NavigationPresentationTest | 入力保持、操作結果、画面構成 |
| OfflineMapTest / BuildingMapTest | 地図保存、建物と人の表示 |
| SharingServiceTest / NearbyLifecycleTest | サービスと模擬接続による受信・保存・ACK・中継 |

通知権限の再要求はAPI 33以上の専用エミュレーターで別起動する。通知を拒否した後、画面を再作成して再要求・許可し、共有開始と通知表示を確認する。通常実行のスキップは成功に含めない。APKを用意して次を実行する。

```powershell
$adbPath = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
& $adbPath -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
& $adbPath -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adbPath -s emulator-5554 shell pm revoke io.github.nevaseme.plandmk1 android.permission.POST_NOTIFICATIONS
& $adbPath -s emulator-5554 shell pm clear-permission-flags io.github.nevaseme.plandmk1 android.permission.POST_NOTIFICATIONS user-set
& $adbPath -s emulator-5554 shell pm clear-permission-flags io.github.nevaseme.plandmk1 android.permission.POST_NOTIFICATIONS user-fixed
& $adbPath -s emulator-5554 shell am instrument -w -r -e notificationRetry true -e class io.github.nevaseme.plandmk1.SharingServiceTest#notificationPermissionCanBeRequestedAgainAfterDenial io.github.nevaseme.plandmk1.test/androidx.test.runner.AndroidJUnitRunner
```

確認画面を再表示できない場合は通知設定を開く。設定で許可して戻り、「共有開始」で再開できることも手動確認する。

## 実験サーバーを使う自動試験

[通信自動試験の手順](LAB_AUTOMATED_TESTING.md)に従い、LabFeedbackTest・IncidentLabTest・PlaceLabTestを個別実行する。接続先・接続コード・グループの渡し方と、返信する相手役の起動を含む。通常の全体実行では、必要引数のないこれらの試験はスキップされる。

## 結果の確認

Gradleの結果は次で確認する。HTMLの集計とXMLの各テストを読み、成功・失敗・スキップを分けて記録する。

| 試験 | HTML | XML |
|---|---|---|
| JVM | app/build/reports/tests/ | app/build/test-results/ |
| Android | app/build/reports/androidTests/connected/ | app/build/outputs/androidTest-results/connected/ |

配下の対象版・端末・実行日時を確認し、XMLのskipped要素がある試験を成功に含めない。通知権限の再要求・実験通信の条件付き試験は、個別実行の結果も確認する。adbによる個別実行は標準出力に結果が出る。

公式資料：[コマンドライン試験](https://developer.android.com/studio/test/command-line)／[AndroidJUnitRunner](https://developer.android.com/reference/androidx/test/runner/AndroidJUnitRunner)。2026-10-20にContext7経由の調査と公式本文で確認。

## Android1台での確認

開始前に操作内容と待機時間を伝える。通知・共有の基本確認は約3分、登録・地図・保存を含む確認は約10分を目安に案内する。

実機操作はADBと画面の文字情報を優先し、操作をまとめて実行する。タップ位置はUIツリーの要素から求める。画像はレイアウト・地図描画の確認や、文字情報で原因を判断できない場合に絞る。入力中はキーボードの表示状態も確認する。試験ランナーが起動できない場合は通常起動とADB操作へ切り替え、ランナーによる試験成功とは分けて記録する。

1. 上書き更新し、既存の住居・建物・対応記録が残ることを確認する。
2. 登録、災害開始、無事／SOS、個人と建物の対応・訂正を操作する。
3. Nearby共有開始、通知、通知停止、接続0台で背面へ移ったときの停止を確認する。
4. 保存済み地図と一覧を通信なしで開き、再起動後の保存状態を確認する。
5. 権限拒否・再許可、無線OFF・ON、地図保存失敗からの復旧を確認する。
6. 文字拡大とTalkBackで主要操作を確認する。

## iPhone・Windowsを含む確認

[PWA実験手順](../lab/README.md)を使い、保存後のA→B→C中継と訂正返送を確認する。端末操作の案内は準備と機能試験に分け、相手の接続・受領を確認してから次の操作へ進む。

## 追加Android入手後の確認

Nearby実機間の探索・接続・圏外交換、相手がいる状態での画面消灯・別アプリ中の受信と中継、無線範囲からの離脱・復帰、長時間省電力を確認する。

通信相手を模擬した試験と、無線を使った実機試験は分けて記録する。
