# エミュレーターとWindowsの通信試験

作業場所は、Android側のターミナルが `android/`、サーバー・相手役側が `lab/`。実機の保存情報を変更しないよう、専用エミュレーターを使う。

## 1. 準備

1. [実験環境の準備](../lab/README.md#起動)に従い、Node.jsの依存関係・ローカル証明書・接続コードを用意する。
2. Androidの `app/src/debug/res/raw/plan_lab_ca.pem` と、実験サーバーの `lab/.local/ca.pem` が同じ公開CAであることを確認する。新しい実験CAを使う場合は公開CAを合わせてからビルドする。
3. lab/の別ターミナルで `npm start` を実行する。他のAndroid・PWAの実験通信は停止し、試験用のA/B接続に使う。
4. AndroidターミナルでAPKをビルド・インストールし、設定を読み込む。

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
$adbPath = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
$testDevice = "emulator-5554"
$runner = "io.github.nevaseme.plandmk1.test/androidx.test.runner.AndroidJUnitRunner"
& $adbPath devices
& $adbPath -s $testDevice install -r app/build/outputs/apk/debug/app-debug.apk
& $adbPath -s $testDevice install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$labConfig = Get-Content -Raw ../lab/.local/config.json | ConvertFrom-Json
$labServer = "wss://10.0.2.2:$($labConfig.port)/socket"
if ([string]::IsNullOrWhiteSpace($labConfig.token)) { throw "実験用接続コードがありません" }
```

SDKの場所とエミュレーターIDは自分の環境に合わせる。各コマンドが成功してから次へ進む。10.0.2.2はエミュレーターから開発PCを指すアドレスで、サーバー証明書にもこのIPが必要。[Android公式：エミュレーターのアドレス](https://developer.android.com/studio/run/emulator-networking-address)

接続コードは上記のローカル設定から読み込む。コード入りの設定・コマンド展開結果はGitへ保存しない。

## 2. 接続状態の試験

相手役Bを起動せず、Android側で次を実行する。`LabFeedbackTest` は固定グループ `feedback-test` に入り、相手待ち・開始連打・背面での停止を確認する。

```powershell
& $adbPath -s $testDevice shell am instrument -w -r -e class io.github.nevaseme.plandmk1.LabFeedbackTest -e labServer $labServer -e labToken $labConfig.token $runner
```

## 3. 保存・対応記録の往復試験

次の2組を順番に試す。試験グループは毎回新しい名前にし、Android側と相手役側で揃える。使用可能文字は英数字・ハイフン・アンダースコア、40文字以内。

| Androidの試験 | 相手役 | 必要な引数 |
|---|---|---|
| IncidentLabTest | native-incident-peer.mjs | labServer、labToken、incidentGroup |
| PlaceLabTest | native-place-peer.mjs | labServer、labToken、placeGroup |

相手役は既存のJavaScript同期エンジンを使い、受信情報を模擬IndexedDBへ保存してから対応記録を返す。保存先はプロセス内の模擬データベース。ブラウザーの永続保存やiPhoneの実機試験とは別に記録する。

### 災害・個人対応

lab/の相手役ターミナル：

```powershell
node scripts/native-incident-peer.mjs incident-check-01
```

`READY` を確認後、Android側ですぐに実行する：

```powershell
$labGroup = "incident-check-01"
& $adbPath -s $testDevice shell am instrument -w -r -e class io.github.nevaseme.plandmk1.IncidentLabTest -e labServer $labServer -e labToken $labConfig.token -e incidentGroup $labGroup $runner
```

AndroidからSTARTとSOSが届くと、相手役が「確認中・JS保存後に返信」を返す。Androidは内容と受領確認を検証する。

### 建物・住居の関連付け

前の相手役が終了した後、lab/で起動：

```powershell
node scripts/native-place-peer.mjs place-check-01
```

`READY` を確認後、Android側ですぐに実行する：

```powershell
$labGroup = "place-check-01"
& $adbPath -s $testDevice shell am instrument -w -r -e class io.github.nevaseme.plandmk1.PlaceLabTest -e labServer $labServer -e labToken $labConfig.token -e placeGroup $labGroup $runner
```

Androidから建物・住居の関連付けが届くと、相手役が「確認中・北玄関・2階は未確認」を返す。Androidは内容、関連付け、元のSOSと受領確認を検証する。

相手役は開始から60秒、Androidの返信待ちは30秒でタイムアウトする。APKのビルド・インストールを済ませてから相手役を起動する。失敗時は相手役とAndroidの原因を確認し、新しいグループで再試行する。

## 4. 成功・失敗・スキップの確認

- `-r` の出力で、指定したクラス・メソッドの `INSTRUMENTATION_STATUS_CODE: 0` が成功。
- `-3` は無視、`-4` は前提条件不成立によるスキップ。上記3試験は引数不足でスキップするため、成功として数えない。
- `-1`／`-2`、クラッシュ、タイムアウト、対象テスト0件は成功扱いにしない。
- 往復試験は相手役の保存・返信表示に加え、最後のACK確認の `PASS` と終了コード0も確認する。接続の `READY` だけでは完了ではない。
- adbの終了コードや末尾の `OK` だけで判定せず、各テストの状態を見る。adb実行結果は標準出力に出るため、Gradleの過去のHTML結果と混同しない。

コードの意味は[AndroidX公式実装](https://github.com/android/android-test/blob/main/runner/android_junit_runner/java/androidx/test/internal/runner/listener/InstrumentationResultPrinter.java)を参照。

終了後は今回起動した相手役・サーバーを停止する。[試験状況](../docs/STATUS.md)には対象版、成功・失敗・スキップ数、実験経路での確認範囲を記録する。

## 公式資料

2026-10-20にContext7でAndroid Developersを参照し、次の公式本文と試験コードを照合した。

- [コマンドラインからの試験](https://developer.android.com/studio/test/command-line)：APKの準備、am instrument、引数と結果の出力先。
- [AndroidJUnitRunner](https://developer.android.com/reference/androidx/test/runner/AndroidJUnitRunner)：classでの試験選択。
