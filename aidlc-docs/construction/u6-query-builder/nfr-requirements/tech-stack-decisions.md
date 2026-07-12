# tech-stack-decisions.md — U6: Query Builder

`nfr-requirements.md`に基づく技術選定の決定事項一覧。

| # | 決定事項 | 選定 | 根拠 |
|---|---|---|---|
| 1 | SQL構文解析ライブラリ | JSqlParser（Apache License 2.0、`com.github.jsqlparser:jsqlparser`最新安定版） | Question 1 = A |
| 2 | SQL解析APIの入力サイズ上限 | `mm.app.query-builder.parse-max-length`（既定`10000`文字） | Question 2 = A |
| 3 | SQL解析APIの処理時間上限 | `mm.app.query-builder.parse-timeout`（既定`5`秒、`ExecutorService`によるタイムアウト制御） | Question 2 = A |
| 4 | メタデータ選択API（スキーマ/テーブル/カラム一覧）のキャッシュ方式 | 独自キャッシュなし。`SchemaQueryService`/`EffectivePermissionResolver`（U3/U4既存）へ委譲 | Question 3 = A |
| 5 | `QueryBuilderModel`各リストの件数上限 | リストごとに個別設定（`selectItems=100`/`joinItems=10`/`whereConditions=30`/`groupByColumns=30`/`havingConditions=20`/`orderByItems=20`） | Question 4 = B, Question 4-2 = A |
| 6 | PBT（Property-Based Testing）フレームワーク | `jqwik`（U1で確定済み、再選定なし） | U1 `tech-stack-decisions.md` #16を踏襲 |

---

## 依存関係追加（本ユニットで新規追加）

- `com.github.jsqlparser:jsqlparser`（Apache License 2.0）を`backend/build.gradle.kts`の
  `dependencyManagement`ブロックにバージョンを追加し、`querybuilder`モジュールの依存関係とする
  （Gradleバージョン管理規約に従い、バージョン番号はインライン指定せず`dependencyManagement`側で
  一元管理する）。正確なバージョン番号はCode Generation時に確定する。

---

## SQL解析APIの入力サイズ・処理時間ガードの位置づけ（Question 2 = A）

- `mm.app.query-builder.parse-max-length`（既定`10000`文字）は`SqlParsingService.parse`呼び出し
  前の事前検証であり、JSqlParserへの入力そのものを制限する。
- `mm.app.query-builder.parse-timeout`（既定`5`秒）はJSqlParserの`CCJSqlParserUtil`による構文
  解析処理自体の実行時間を制限するものであり、対象RDBMSへの接続・クエリ実行とは無関係
  （U6はGEN-8/GEN-9いずれも対象RDBMSへの実行を伴わない）。

---

## メタデータキャッシュを新設しない判断の根拠（Question 3 = A）

- `SchemaQueryService`（U3既存）が扱うスキーマメタデータは対象RDBMSへのインポート時に内部DBへ
  保存済みのものであり、`listSelectableTables`/`listSelectableColumns`呼び出しのたびに対象
  RDBMSへライブ接続することはない。
- `EffectivePermissionResolver`（U4既存）も権限判定結果をキャッシュ済みのため、`querybuilder`
  側で重ねてキャッシュする必要性が薄い。

---

## リストごとの件数上限の設計判断（Question 4 = B, Question 4-2 = A）

- U5の`max-mutation-batch-size`（単一の合計上限）とは異なり、U6ではリストの性質差
  （SELECT句は幅広いカラム＋集計関数の組み合わせを想定し多め、JOIN句は実務上の段数が少ないため
  少なめ）を反映し、リストごとに個別の上限値を設定する。
- 検証は`SqlGenerationService.generate`・`SqlParsingService.parse`（変換後モデル）の両方で行い、
  SQL生成/実行前に完結させる（対象RDBMSへの問い合わせは行わない）。