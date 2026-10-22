# PlanD：技術検討

2026-10-20の調査を整理した参考資料。[設計書](2026-10-19-integrated-design.md)の実現方法と、後の段階で必要な調査を示す。今回の文書整理では外部資料の再調査や新機能の実機試験は行っていない。

## 実現方法と残る検証

| 対象 | 方針 | 残る検証 |
|---|---|---|
| 人数条件・保存・中継 | 承認情報と人単位の報告を各端末で検証。既存の差分同期を拡張 | 分断・遅延・取消を含む集計の一致 |
| 継続測位 | Fused Location Provider、location型サービス、Room保存 | OS・権限・省電力と電池消費 |
| 電源断後の位置 | 受信済みの最後の点と取得時刻を表示 | 元の端末停止後の閲覧・中継 |
| 建物とスマホの地図 | MapLibreの建物面と点を別データ源として描画 | 重なり、タップ、文字拡大、オフライン属性 |
| 建物の強さ | PLATEAU等の属性と評価手法を組み合わせる | 地域データの収録内容、評価の妥当性 |
| 緊急地震速報 | 正規のリアルタイム配信元と連携 | 利用条件、地域、続報・取消、背景受信 |
| 衝撃 | 加速度等の日時付き観測 | 落下等の誤検知、消灯中の取得、機種差 |
| 閉所 | 照度・近接・動き等の比較実験 | 検知精度、解除条件、機種差 |
| 埋没者の探索 | 近距離通信・音響等の距離／方向推定 | 実環境で絞り込める範囲 |

画面・保存・同期は既存のKotlin／Compose／Room／Nearby／MapLibreを拡張する。建物評価と閉所推定は、表示の実装と判定精度の検証を分けて進める。

## 技術資料

当初の調査ではContext7のAndroid DevelopersとMapLibre Nativeを参照し、以下の公式資料で補完した。

- [Android：location型サービス](https://developer.android.com/develop/background-work/services/fgs/service-types#location) — 本人による開始、権限、背景からの開始・復旧条件を確認する。
- [Android：センサー概要](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview) — 背景取得の制限、センサーの有無・能力を確認する。
- [MapLibre：FillLayer](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.style.layers/-fill-layer/index.html) — 建物面の描画。
- [MapLibre：queryRenderedFeatures](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-libre-map/query-rendered-features.html) — 建物を選択するタップ判定。
- [国土交通省：建物振動シミュレーション v3.0](https://www.mlit.go.jp/plateau/use-case/uc25-02/)／[先行事例](https://www.mlit.go.jp/plateau/use-case/uc23-24/) — 建築属性・地震動による推定の参考。PlanDの評価方法としての適合性は第4段階で検証する。
- [気象庁：緊急地震速報のデータ案内](https://www.data.jma.go.jp/developer/weatherdataguide/appendix/1-3-a.html) — リアルタイム配信の入手先・条件を調べる。発表履歴と即時配信を区別する。

## 着手時のコード確認点

- 現行の共有サービスはconnectedDevice型。継続測位用の型・権限・処理を追加する。
- 現行のCOARSE／FINE権限はAPI32まで。対象Android環境の位置取得に合わせて見直す。
- Residentの住居座標を維持し、スマホの測位レコードを追加する。
- 既存の救助・訂正記録を利用し、本人のSOSメモは別に扱う。
- 人数条件には承認済みメンバーと報告の作成元を検証する仕組みを追加する。

仕様の決定と初期値は設計書、工程は実装計画、実装・試験結果は[実装・試験状況](../../STATUS.md)に集約する。
