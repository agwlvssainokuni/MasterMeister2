# logical-components.md — U7: Saved Query / Execution / History

`nfr-design-patterns.md`に基づく論理コンポーネント一覧。

---

## 1. Saved Query（`cherry.mastermeister.savedquery`）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `SavedQueryService` | Service | `listQueries`/`saveQuery`/`getQuery`/`updateQuery`/`retireQuery`（`business-rules.md` 1節、フロー1）に加え、`queryhistory`向けのバッチ判定API`getStatuses`（`business-rules.md` 5.2） |
| `SavedQuery`（JPAエンティティ） | Entity | `id, ownerId, connectionId, name, sql, visibility, retired, executionCount, createdAt, updatedAt`（`domain-entities.md`確定）。`sql`は`@Lob`（`nfr-requirements.md` 3.1）。追加の明示的インデックスなし（`nfr-design-patterns.md` 2） |
| `Visibility`（enum） | Enum | `PUBLIC`/`PRIVATE`（`domain-entities.md`確定） |
| `SavedQueryStatus` | DTO（`record`） | `getStatuses`の戻り値要素（`visibleToViewer`, `retired`、`domain-entities.md`確定） |

**依存方向**: `savedquery → common`（`component-dependency.md`確定済み）のみ。

---

## 2. Query Execution（`cherry.mastermeister.queryexecution`）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `QueryExecutionService` | Service | `executeAdhocSql`/`executeSavedQuery`（フロー2手順1-9のオーケストレーション）。`SavedQueryService`（保存クエリ実行時のSQL取得・可視性/retiredチェック）・`ReadOnlySqlValidator`・`SqlParamDetector`・`PagingSqlBuilder`・`ConnectionPoolRegistry`（U3）・`QueryHistoryService`・`AuditLogService`（U1）を呼び出す（`nfr-design-patterns.md` 1.1、1.3） |
| `ReadOnlySqlValidator` | Component | JSqlParserによる`Select`型判定（`business-rules.md` 2.1）。共有`ExecutorService`によるタイムアウト制御（`nfr-design-patterns.md` 1.2） |
| `SqlParamDetector` | Component | 正規表現によるパラメータ検出（`business-rules.md` 3節） |
| `PagingSqlBuilder` | Component | サブクエリラップ＋`DialectStrategy.buildPagingClause`（`business-rules.md` 4節） |
| `QueryResult`/`ResultColumn`/`DetectedParam`/`PagingOption` | DTO（`record`） | 呼び出し境界データ構造（`domain-entities.md`確定） |

**依存方向**: `queryexecution → common, audit, rdbmsconnection, queryhistory, savedquery`
（`component-dependency.md`確定済み）の一方向のみ。`querybuilder`（U6）・`masterdata`
（U5）・`permission`（U4）への依存は持たない。専用の内部DBエンティティは持たない
（`domain-entities.md`確定）。

---

## 3. Query History（`cherry.mastermeister.queryhistory`）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `QueryHistoryService` | Service | `recordExecution`（フロー2手順8、失敗時非伝播——`nfr-design-patterns.md` 1.3）・`listHistory`（フロー3、`business-rules.md` 5.1-5.3のページング・マスキング） |
| `QueryHistory`（JPAエンティティ） | Entity | `id, userId, connectionId, sql, params, resultCount, elapsedMillis, executedAt, savedQueryId, savedQueryName, executionCount`（`domain-entities.md`確定）。`sql`は`@Lob`（`nfr-requirements.md` 3.1）。`(connectionId, executedAt)`・`savedQueryId`の明示的インデックスを追加（`nfr-design-patterns.md` 2） |
| `HistoryFilterCriteria`/`ExecutorScope` | DTO/Enum | 絞り込み条件（`domain-entities.md`確定） |

**依存方向**: `queryhistory → common, savedquery`（`component-dependency.md`確定済み、
`SavedQueryService.getStatuses`呼び出しのための新規依存）。`queryexecution → savedquery`
とは別方向であり、循環依存にはならない。

---

## 4. `QueryHistory`インデックス構成（`nfr-design-patterns.md` 2）

| インデックス | 対象カラム | カバーするクエリ |
|---|---|---|
| 複合インデックス | `connectionId, executedAt` | `listHistory`の絞り込み（`connectionId`必須）・日時降順ソート |
| 単独インデックス | `savedQueryId` | `getStatuses`の`savedQueryId IN (...)`バッチ検索 |

---

## 5. GEN-13入力SQLタイムアウト制御構成（`nfr-design-patterns.md` 1.2）

| 項目 | 値 |
|---|---|
| `ExecutorService`の生成単位 | `ReadOnlySqlValidator`のインスタンスフィールド（Bean生成時に1回のみ生成） |
| プールサイズ設定キー | `mm.app.query-execution.parse-executor-pool-size`（既定`4`） |
| タイムアウト待機方式 | `Future.get(parseTimeoutSeconds, TimeUnit.SECONDS)`（`mm.app.query-execution.parse-timeout`、既定`5`秒） |
| タイムアウト超過時の扱い | `future.cancel(true)`実行後、`ValidationException`で実行を拒否（U6とは異なりnotice応答なし） |

---

## 6. Frontend（`features/{savedQuery,queryExecution,queryHistory}/`）

`u7-saved-query-execution-history/functional-design/frontend-components.md`で確定済みの
コンポーネント構成（`SavedQueryListPage`/`SavedQuerySaveForm`/`SavedQueryDetailPage`/
`QueryExecutionPage`/`QueryHistoryListPage`、各`api.ts`、U6との連携実装方針）をそのまま
踏襲する（本ステージでの追加変更なし）。

---

## 7. 設定ファイル

| ファイル | 内容 |
|---|---|
| `application.yml` | `mm.app.query-history.default-page-size`（既定`50`）、`mm.app.query-history.page-size-options`（既定`50,100,200`）、`mm.app.query-execution.query-timeout`（既定`30`秒）、`mm.app.query-execution.sql-max-length`（既定`10000`文字）、`mm.app.query-execution.parse-timeout`（既定`5`秒）、`mm.app.query-execution.parse-executor-pool-size`（既定`4`）、`mm.app.query-execution.max-result-rows`（既定`1000`件） |
| `build.gradle.kts` | 変更なし（JSqlParserはU6で導入済みの依存関係をそのまま再利用、`tech-stack-decisions.md`確定） |

---

## 8. U4/U5/U6責務境界の再確認

- `queryexecution`はテーブル/カラム単位の読み取り権限フィルタ（U4）を持たない
  （`business-rules.md` 2.2、`security-baseline`拡張オプトイン時に再検討）。
- `queryexecution`・`savedquery`はいずれも内部DBエンティティとしての結果保存を行わない
  （`masterdata`のように対象RDBMSのデータそのものを扱うわけではなく、`QueryResult`は
  リクエストの都度構築されるDTOのみ）。
- `querybuilder`（U6）との連携はURLクエリパラメータ（`rawSql`/`connectionId`）による画面遷移
  のみであり、バックエンドパッケージ間の直接依存は持たない（`business-rules.md` 6節）。