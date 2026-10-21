# PlanD 画面見本

地図の集約表示、人物詳細、登録住居・寝室、救助記録をチームで確認する操作見本です。

## 共有

公開URL： https://nevaseme.github.io/safety_confirm/

- PCでは広域・拡大の2画面、スマートフォンでは1画面を表示します。
- `?view=near` を付けると拡大地図だけ、`?view=wide` を付けると広域地図だけを開きます。
- サンプルの安否・位置・住所・建物評価は架空です。
- 入力した記録はそのブラウザーのメモリー内で更新され、再読み込みで初期状態に戻ります。

## 氏名の扱い

- 無事・救助済みの人は、人物詳細・住民一覧・読み上げ表示で氏名を表示しません。
- 初期状態で無事・救助済みの人の氏名は配信データにも含めていません。
- SOS・応答なし・位置不明の人の氏名は表示します。位置不明の2人は安否未受信の例です。

## 公開対象

`.github/workflows/demo-pages.yml` は、この `demo/` フォルダだけをPagesへ配信します。
Androidアプリ・実験サーバー・設計書は別のディレクトリで管理します。

ローカルでは、このフォルダを静的HTTPサーバーで開いてください。地図・字体・標準アイコン・地図ライブラリはインターネット経由で読み込みます。

## 素材・ライブラリ

- 地図：OpenFreeMap Positron / OpenMapTiles / OpenStreetMap。地図内に帰属表示を掲載。
- 描画：MapLibre GL JS 5.6.0、集約：Supercluster 8.0.1。
- 字体：Noto Sans JP（Google Fonts、SIL Open Font License）。
- 標準アイコン：Phosphor Icons 2.1.1（MIT）。
- 閉所・衝撃：プロジェクトで提供された `image/閉所.png` と `image/impact-burst-orange.svg`。

ライブラリと字体は配信元から取得し、依存物をリポジトリへ同梱しません。
