# logical-components.md — U5: Master Data Maintenance

`nfr-design-patterns.md`に基づく論理コンポーネント一覧。

---

## 1. Master Data（`cherry.mastermeister.masterdata`）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `MasterDataQueryService` | Service | `listAccessibleSchemas`/`listAccessibleTables`/`listRecords`（`business-rules.md` 1-2節）。内部DBエンティティは持たず、`SchemaQueryService`（U3）・`EffectivePermissionResolver`（U4）・`ConnectionPoolRegistry`（U3）への都度委譲で完結する |
| `MasterDataMutationService` | Service | `applyChanges`（`business-rules.md` 3節）。`ConnectionPoolRegistry.getTransactionTemplate(connectionId)`のコールバック内で`creates`/`updates`/`deletes`を個別実行ループ（`nfr-requirements.md` 2.2）で発行し、`AuditLogService.record`で監査記録する |
| `RecordRowMapper`（クラス名はCode Generationで確定） | `RowMapper<List<Object>>` | `ResultSetMetaData.getColumnType`に基づき`ResultSet.getObject(int, Class)`（JDBC 4.2標準API）で対象RDBMS4種間の型マッピング差異を吸収する（`nfr-design-patterns.md` 2.1） |
| `TableSummary`/`ColumnMetadata`/`RecordListResult`/`FilterCriteria`/`UiCondition`/`UiSort` | DTO（`record`） | 読み取り系の呼び出し境界データ構造（`domain-entities.md`確定） |
| `RecordCreate`/`RecordUpdate`/`RecordDelete`/`MutationRequest`/`MutationResult` | DTO（`record`） | 更新系の呼び出し境界データ構造（`domain-entities.md`確定） |

**依存方向**: `masterdata → schema`（U3）・`masterdata → permission`（U4）・`masterdata → audit`
（U1）の一方向のみ。`common`への切り出しは行わない（`nfr-design-patterns.md` 1.1参照）。

---

## 2. クエリタイムアウトの適用（`nfr-design-patterns.md` 2.2）

| 呼び出し元 | `getJdbcTemplate`呼び出し回数 | `setQueryTimeout`呼び出し回数 |
|---|---|---|
| `MasterDataQueryService.listRecords` | 1回（SELECT文1件） | 1回 |
| `MasterDataMutationService.applyChanges` | 1回（`getTransactionTemplate`コールバック内、`creates`/`updates`/`deletes`全体で再利用） | 1回（ループ開始前） |

いずれも`mm.app.master-data.query-timeout`（既定30秒）をステートメント単位で適用する。
トランザクション全体の累積タイムアウト予算は導入しない。

---

## 3. Frontend（`features/masterData/`）

`u5-master-data-maintenance/functional-design/frontend-components.md`で確定済みのコンポーネント
構成（`SchemaTableListPage`/`FilterPanel`/`RecordListPage`/`MutationResultDialog`/`api.ts`）を
そのまま踏襲する（本ステージでの追加変更なし）。

---

## 4. 設定ファイル

| ファイル | 内容 |
|---|---|
| `application.yml` | `mm.app.master-data.default-page-size`（既定`50`）、`mm.app.master-data.page-size-options`（既定`50,100,200`）、`mm.app.master-data.query-timeout`（既定`30`秒）、`mm.app.master-data.max-mutation-batch-size`（既定`500`）、`mm.app.master-data.large-record-threshold`（既定`100`、`business-rules.md` 2.5で確定済み） |
| `build.gradle.kts` | 新規依存追加なし（`tech-stack-decisions.md`確定） |

---

## 5. U3/U4/U5責務境界の再確認

- `masterdata`パッケージは内部DBエンティティを一切持たず、`schema`（U3）・`permission`
  （U4）・`audit`（U1）の既存コンポーネントへの都度委譲のみで完結する（`domain-entities.md`
  Q1 = A）。
- `ConnectionPoolRegistry.getTransactionTemplate(connectionId)`（U3所有、`business-rules.md`
  3.3で追加確定）は`masterdata`から呼び出されるが、実装自体はU3側に置かれる（U4 Q4と同種の
  Functional Designレベルでの許容される詳細化）。