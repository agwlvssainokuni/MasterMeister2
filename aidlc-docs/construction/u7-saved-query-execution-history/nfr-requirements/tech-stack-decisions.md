# tech-stack-decisions.md — U7: Saved Query / Execution / History

`nfr-requirements.md`に基づく技術選定の決定事項一覧。

| # | 決定事項 | 選定 | 根拠 |
|---|---|---|---|
| 1 | 実行履歴一覧（`listHistory`）ページングの検証方式 | `AuditLogService`/U5と同じ既定値＋選択肢リスト方式。`mm.app.query-history.default-page-size`（既定`50`）／`mm.app.query-history.page-size-options`（既定`50,100,200`） | Question 1 = A |
| 2 | 対象RDBMSクエリ実行タイムアウトの実装機構 | `NamedParameterJdbcTemplate.setQueryTimeout`（U5と同じ機構）。`mm.app.query-execution.query-timeout`（既定`30`秒、キー名・既定値はFunctional Design Q11で確定済み）、executeAdhocSql/executeSavedQuery両方に一律適用 | Question 2 = A |
| 3 | GEN-13手入力SQLに対するJSqlParser解析ガード | `mm.app.query-execution.sql-max-length`（既定`10000`文字）・`mm.app.query-execution.parse-timeout`（既定`5`秒、`ExecutorService`によるタイムアウト制御）。U6の`querybuilder.parse-max-length`/`parse-timeout`とは別スコープで新設 | Question 3 = A |
| 4 | SQL・パラメータ保持カラムの型 | `@Lob`（H2の`CLOB`型相当）、長さ上限なし | Question 4 = A |
| 5 | `executionCount`アトミック更新の実装方式 | Spring Data JPAの`@Modifying @Query`によるUPDATE文（`clearAutomatically = true`）。楽観ロック不採用 | Question 5 = A |
| 6 | JSqlParserライブラリ | U6で導入済み（`com.github.jsqlparser:jsqlparser`、Apache License 2.0）をそのまま再利用。バージョン再選定なし | U6 `tech-stack-decisions.md` #1を踏襲 |
| 7 | PBT（Property-Based Testing）フレームワーク | `jqwik`（U1で確定済み、再選定なし） | U1 `tech-stack-decisions.md` #16を踏襲 |

---

## 依存関係追加（本ユニットで新規追加）

なし。JSqlParserはU6で既にプロジェクト共通のビルド定義（単一Gradleモジュール）に追加済みで
あり、`queryexecution`パッケージから同一ライブラリを直接呼び出すのみ（`business-rules.md`
2.1）。本ユニットのNFR Requirementsで確定した方式（ページング検証、クエリタイムアウト、
JSqlParser入力ガード、`@Lob`カラム、`@Modifying`アトミック更新）はいずれも既存の
`NamedParameterJdbcTemplate`（Spring JDBC）・`@ConfigurationProperties`（Spring Boot）・
Spring Data JPA・`ValidationException`（既存の独自例外）の範囲内で実現でき、新規ライブラリの
導入は不要。

---

## クエリ実行タイムアウトと他ユニットのタイムアウト設定との区別（Question 2 = A）

- `mm.app.query-execution.query-timeout`（本ユニットで新設、既定`30`秒）は、`queryexecution`
  スコープでのコネクション取得後のSQL文実行時間を制限する。
- U3で確定済みの`connection-timeout`（既定`5`秒、`ConnectionPoolRegistry`のプール設定）は、
  プールからのコネクション取得までの待機時間を制限するものであり、別軸の設定値。
- U5で確定済みの`mm.app.master-data.query-timeout`（既定`30`秒、`masterdata`スコープ）とは
  値こそ同じだが、設定キー・適用箇所は完全に独立している（`masterdata`と`queryexecution`は
  それぞれ別のJDBC呼び出し経路を持つため）。

---

## GEN-13入力SQLガードとU6ガードの独立性（Question 3 = A）

- `mm.app.query-execution.sql-max-length`/`parse-timeout`（本ユニット）は、`queryexecution`
  から直接JSqlParserを呼び出す箇所（読み取り専用検証、`business-rules.md` 2.1）にのみ適用
  する。
- U6の`mm.app.query-builder.parse-max-length`/`parse-timeout`は、`SqlParsingService.parse`
  （GEN-9、`QueryBuilderModel`への変換）にのみ適用され、`queryexecution`の呼び出しには
  一切影響しない——両者は同じ「JSqlParserへの入力ガード」という目的だが、対象パッケージ・
  設定キーとも完全に分離している。

---

## `@Lob`カラムと`sql-max-length`ガードの関係（Question 4 = A）

- `SavedQuery.sql`・`QueryHistory.sql`はDBカラムとしては`@Lob`（無制限）で保存する。
- 実行前のJSqlParser解析ガード（`sql-max-length`、既定`10000`文字）を通過したSQLのみが
  実際に対象RDBMSへ送られるため、`executeAdhocSql`経由で保存されるSQL（`SavedQuery.sql`は
  `saveQuery`呼び出し時に別途保存されるものであり、実行を経由しない保存も可能——
  `business-rules.md` 1節、GEN-10は「保存」のみでSQLの実行を要求しない）は、
  `sql-max-length`のガードを経由しない場合がある。この非対称性は許容する——保存クエリの
  実行時（`executeSavedQuery`）には1.2のガードが必ず適用されるため、実行時点で安全性は
  担保される。