# 開発資料

- [設計書](superpowers/plans/2026-10-19-integrated-design.md)
- [開発全体計画](superpowers/plans/2026-10-08-pland-roadmap.md)
- [各工程の着手・終了・次へ進む条件](superpowers/plans/2026-10-08-pland-roadmap.md#工程別の条件)
- [追加機能案と外部資料の検討](superpowers/plans/2026-10-22-research-and-feature-options.md)
- [確認事項](superpowers/plans/2026-10-20-decisions.md)
- [実装・試験状況](STATUS.md)
- [技術検討](superpowers/plans/2026-10-20-feasibility.md)

開発順序は、端末内の安否・位置→地震開始と終了→担当・対応→追加基本機能→建物・観測→1台での仕上げ→通信の統合試験→巡回ルート。追加基本機能は、通報・対応履歴、手動の緊急度、活動可能者、資材要請、表形式出力、地域集計の6つを採用済み。SOSメモ、実際の位置取得・保存・一覧を追加済み。次は人数による地震成立を進める。3日で位置の公開を終了する条件は全工程の最後に扱う。設計書には未実装の仕様を含む。

過去の資料は [archive/](archive/) に保管。

## 画面更新時のルール

画面を更新したら、同じ作業で `demo/` と[公開Webサイト](https://nevaseme.github.io/safety_confirm/)にも反映する。既存の比較画面は残し、更新版を右側に追加する。各画面の上にバージョンと、それまでの画面からの追加・変更点を1〜2文で表示する。画面案とAndroidの実装版は区別して表記する。

Webでの表示・操作と公開サイトへの反映を確認して完了とする。未公開の場合は未反映と報告する。公開デモには架空データを使う。
