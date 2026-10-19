# safety_confirm / PlanD

災害時の安否確認・情報中継・救助支援を開発するリポジトリです。

## 構成

| 場所 | 内容 |
|---|---|
| [android/](android/) | Androidアプリ「PlanD Mk1」。Android Studioで開くプロジェクト |
| [lab/](lab/) | iPhone用実験PWA、Windows実験用サーバー、同期テスト |
| [plan.txt](plan.txt) | 利用者が管理する原案。明示的な依頼なしに編集しない |
| [docs/superpowers/plans/](docs/superpowers/plans/) | 設計・実装計画・調査結果 |

Git管理開始時のAndroidコードは1.4.5です。設計書には今後の機能も含みます。位置の継続取得、新しい人数条件による地図開放、建物の強度評価などを、実装・検証済みという意味ではありません。

## Android

このリポジトリの `android/` をAndroid Studioで開き、SDK設定とGradle同期を行います。最小SDKは24です。SDKのローカルパスを記録する `local.properties` はGit管理しません。

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

従来のAndroid Studioフォルダーからソースを複製し、ファイル内容の一致を確認してGit管理を開始しました。今後の開発先はこのリポジトリの `android/` です。元のフォルダーへの自動同期はありません。

## 実験PWAとサーバー

Node.js 24以上を使います。

```powershell
cd lab
npm ci
npm test
```

実機との接続にはローカル証明書と実験用接続コードの準備が必要です。[実験手順](lab/README.md)を参照してください。秘密鍵・コードは `lab/.local/` に置き、Git管理しません。

Androidのdebug版は既存の実験用公開CA証明書を参照しています。この公開証明書だけをソースに含め、対応する秘密鍵は含めません。別PCで新しい実験CAを作った場合は、Androidの公開CAリソースもその実験のものへ合わせてからビルドしてください。

## 文書

- [統合設計](docs/superpowers/plans/2026-10-19-integrated-design.md)
- [実装計画](docs/superpowers/plans/2026-10-08-pland-roadmap.md)
- [実現可能性の検討](docs/superpowers/plans/2026-10-20-feasibility.md)

生成APK、実機ログ、過去の作業用コピー、ダウンロードしたDADS参考資料はローカルに残し、リポジトリから除外しています。既存文書にある `artifacts/` への参照はローカルの試験成果物を指します。DADSの原典は[デジタル庁デザインシステム](https://design.digital.go.jp/dads/)です。
