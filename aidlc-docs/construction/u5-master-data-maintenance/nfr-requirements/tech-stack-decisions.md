# tech-stack-decisions.md — U5: Master Data Maintenance

`nfr-requirements.md`に基づく技術選定の決定事項一覧。

| # | 決定事項 | 選定 | 根拠 |
|---|---|---|---|
| 1 | `listRecords`ページングの検証方式 | `AuditLogService`と同じ既定値＋選択肢リスト方式。`mm.app.master-data.default-page-size`（既定`50`）／`mm.app.master-data.page-size-options`（既定`50,100,200`） | Question 1 = A |
| 2 | 対象RDBMSクエリ実行タイムアウト | `NamedParameterJdbcTemplate.setQueryTimeout`。`mm.app.master-data.query-timeout`（既定`30`秒）、listRecords/applyChanges両方に一律適用 | Question 2 = A |
| 3 | `applyChanges`の実行方式 | `NamedParameterJdbcTemplate.update()`による個別実行ループ（`batchUpdate`不採用） | Question 3 = A |
| 4 | 単一`applyChanges`リクエストの最大件数 | `mm.app.master-data.max-mutation-batch-size`（既定`500`）、超過時はSQL実行前に`ValidationException`で拒否 | Question 4 = A |
| 5 | PBT（Property-Based Testing）フレームワーク | `jqwik`（U1で確定済み、再選定なし） | U1 `tech-stack-decisions.md` #16を踏襲 |

---

## 依存関係追加（本ユニットで新規追加）

なし。本ユニットのNFR Requirementsで確定した方式（ページング検証、クエリタイムアウト、個別実行
ループ、リクエスト件数上限）はいずれも既存の`NamedParameterJdbcTemplate`（Spring JDBC）・
`@ConfigurationProperties`（Spring Boot）・`ValidationException`（既存の独自例外）の範囲内で
実現でき、新規ライブラリの導入は不要。

---

## クエリ実行タイムアウトとコネクション取得タイムアウトの区別（Question 2 = A）

- `mm.app.master-data.query-timeout`（本ユニットで新設、既定`30`秒）は、コネクション取得後の
  SQL文実行時間を制限する。
- U3で確定済みの`connection-timeout`（既定`5`秒、`ConnectionPoolRegistry`のプール設定）は、
  プールからのコネクション取得までの待機時間を制限するものであり、別軸の設定値。両者は
  独立して設定・適用される。

---

## `applyChanges`個別実行ループとトランザクション制御の関係（Question 3 = A）

- 個別実行ループは、`business-rules.md` 3.3で確定済みの`ConnectionPoolRegistry.
  getTransactionTemplate(connectionId)`が返す`TransactionTemplate`の`execute`コールバック内で
  行う。ループ内の各`update()`呼び出しは同一トランザクションに参加し、いずれか1件でも
  `DataAccessException`が発生した場合はコールバック全体がロールバックされ、原子性
  （all-or-nothing）が維持される。
- バッチ更新を採用しないことによる性能上のトレードオフは、Question 4で確定した
  `max-mutation-batch-size`（既定`500`）による1リクエストあたりの件数上限で吸収する。