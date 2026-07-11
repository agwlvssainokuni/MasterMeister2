# domain-entities.md — U5: Master Data Maintenance

`u5-master-data-maintenance-functional-design-plan.md`（回答Q1〜Q8）に基づくドメインモデル。

---

## 内部DBエンティティなし（Q1 = A）

`masterdata`パッケージは内部DB（H2, JPA）に固有の永続状態を一切持たない。すべての処理は
以下への都度アクセス・委譲で完結する。

- `SchemaQueryService`（U3）: 対象RDBMSの取り込み済みメタデータ参照
- `ConnectionPoolRegistry`（U3）: 対象接続の`NamedParameterJdbcTemplate`取得
- `EffectivePermissionResolver`（U4）: 実効権限判定
- `AuditLogService`（U1）: 監査記録

そのため本ユニットが定義する型はすべてservice/dto層の純粋なJava `record`（JPA非対応）で
あり、以下は内部DBスキーマではなく`MasterDataQueryService`/`MasterDataMutationService`の
呼び出し境界を流れるデータ構造のカタログである。

---

## 読み取り系（`MasterDataQueryService`）

### TableSummary（アクセス可能テーブル一覧、MVP-10）

```java
record TableSummary(
        String schemaName,
        String tableName,
        TableType tableType,        // cherry.mastermeister.schema.TableType（U3既存enum、再利用）
        String comment,
        Permission effectivePermission,  // cherry.mastermeister.permission.Permission（U4既存enum、再利用）
        boolean canCreate,
        boolean canDelete
)
```

`listAccessibleTables(userId, connectionId, schema)`が返す。U3の`TableMetadata`
（`schemaName`/`tableName`/`tableType`/`comment`のみ、権限情報を持たない生の一覧）とは別の
型として`component-methods.md`で確定済み（Application Design Question時点の決定）。
`effectivePermission`が`NONE`のテーブルは一覧に含めない（MVP-10 AC「Allow権限のあるテーブル/
ビューのみ表示」＝`EffectivePermissionResolver.listAccessibleTables`が既にNONE除外済みの
一覧を返す設計、U4`business-rules.md` 2.5参照）。`canCreate`/`canDelete`は
`EffectivePermissionResolver.canCreate`/`canDelete`をテーブルごとに呼び出した結果をそのまま
コピーする（Q8のUI事前制御——新規作成・削除ボタンの表示可否——に用いる）。

### ColumnMetadata（列メタデータ、Q2）

```java
record ColumnMetadata(
        String columnName,
        String dataType,
        boolean nullable,
        Integer primaryKeySequence,      // 主キーでない場合はnull
        Permission effectivePermission   // 常にREAD以上（NONE列はSELECT句から除外されるため）
)
```

`columnName`/`dataType`/`nullable`は当該`listRecords`実行時の`ResultSetMetaData`から導出する
（U3インポート時点の`SchemaColumn`スナップショットに依存しない、Q2設計のポイント）。
`primaryKeySequence`/`effectivePermission`は`ResultSetMetaData`からは信頼できる形で取得
できないため、`SchemaColumn.getPrimaryKeySequence()`と
`EffectivePermissionResolver.resolveEffectiveColumnPermissions`の結果をカラム名で突合して
付与する。

### RecordListResult（レコード一覧、Q2 — `RecordDto`は廃止）

```java
record RecordListResult(
        List<ColumnMetadata> columns,
        PageResult<List<Object>> records   // cherry.mastermeister.common.PageResult（U1既存、再利用）
)
```

`listRecords(userId, connectionId, schema, table, criteria, page)`の戻り値
（`component-methods.md`確定の`PageResult<RecordDto>`をFunctional Designで詳細化、
U4 Q4のメソッド追加と同種の許容範囲）。JDBCの`ResultSet`が「行データ」と`ResultSetMetaData`
から取れる「列メタデータ」を対で提供する構造をそのまま踏襲する。実際に発行するSELECT文は
NONE権限のカラムをSELECT句から**そもそも除外**するため、`columns`と`records`の列は
構造的に食い違い得ない。各行は`columns`と同じ並び順の位置ベース`List<Object>`（列名を
行ごとに繰り返し保持するマップ形式は`columns`側に列名が既にあるため冗長と判断し廃止）。
値の型は`NamedParameterJdbcTemplate`のデフォルト型マッピング
（`java.time.LocalDate`/`LocalDateTime`/`OffsetDateTime`、`BigDecimal`、`String`、
`Boolean`等）をそのまま用いる。U7（クエリ実行、未着手）の結果表示も同じ「`ResultSetMetaData`
由来のカラム情報＋結果行を同梱する」構造になる想定（申し送り事項）。

### FilterCriteria（絞り込み・ソート条件、Q3）

```java
enum FilterMode { UI, RAW }

enum Operator { EQ, NE, GT, LT, GE, LE, LIKE, IS_NULL, IS_NOT_NULL }

record UiCondition(String columnName, Operator operator, Object value)

record UiSort(String columnName, SortDirection direction)
   // cherry.mastermeister.common.dialect.SortDirection（U1既存enum、再利用。新規定義しない）

record FilterCriteria(
        FilterMode mode,
        List<UiCondition> uiConditions,   // mode = RAW の場合は空リスト
        List<UiSort> uiSorts,             // mode = RAW の場合は空リスト
        String rawWhere,                  // mode = UI の場合はnull
        String rawOrderBy                 // mode = UI の場合はnull
)
```

`mode = UI`の場合は`uiConditions`/`uiSorts`（複数条件はAND結合のみ、複数カラムソートは
リスト順で優先順位付け）、`mode = RAW`の場合は`rawWhere`/`rawOrderBy`（手入力、GEN-2）を
使用する排他的な2種類。

### PageRequest（既存型の再利用）

`listRecords`のページング引数は`cherry.mastermeister.common.PageRequest`（U1既存、
`page`/`pageSize`）をそのまま用いる。新規型は定義しない。

---

## 更新系（`MasterDataMutationService`）— Q2「読み取り系との非対称設計」

読み取り系（`RecordListResult`）とは異なり、更新系は`Map<String, Object>`ベースの構造を
維持する。`RecordCreate`/`RecordUpdate`は編集・入力された一部カラムのみを疎に表現する
必要があり（全カラムを毎回位置指定で送るのは不自然）、`RecordUpdate`/`RecordDelete`の
主キー特定もカラム名で明示する方が意図が明確なため（Q2, Q6）。

```java
record RecordCreate(Map<String, Object> values)
   // 挿入する列名→値のマップ（未指定列はDB既定値/NULLに委ねる）

record RecordUpdate(Map<String, Object> primaryKeyValues, Map<String, Object> changedValues)
   // primaryKeyValues: 対象行を再特定する主キー列名→値のマップ（WHERE句組立てに使用）
   // changedValues: 変更された列名→値のマップのみ（未変更列は送信しない）

record RecordDelete(Map<String, Object> primaryKeyValues)
   // 対象行を再特定する主キー列名→値のマップ（WHERE句組立てに使用）

record MutationRequest(
        List<RecordCreate> creates,
        List<RecordUpdate> updates,
        List<RecordDelete> deletes
)
```

`RecordUpdate.primaryKeyValues`/`RecordDelete.primaryKeyValues`は、主キーなしテーブルに
対しては構造上送信できない（Q6 = A: `RecordUpdate`は主キーなしテーブルで全体拒否、
`RecordDelete`は`canDelete`が常にfalseで自然に拒否）。

### MutationResult（`applyChanges`の戻り値）

```java
record MutationResult(
        boolean success,
        int createdCount,
        int updatedCount,
        int deletedCount,
        String errorMessage   // 失敗時のみ設定。SQLException由来または権限検証失敗の概要のみ（Q5）
)
```

Q5（all-or-nothing方針）により部分成功は存在しない。`success = false`の場合、
`createdCount`/`updatedCount`/`deletedCount`は`0`（対象RDBMSへの問い合わせ自体を行わない
権限検証失敗の場合）または実行前の値（`SQLException`によるロールバック時、実際には0件
反映のため同様に`0`）となる。

---

## 例外（既存の共通例外クラスを再利用、新規定義しない）

- `cherry.mastermeister.common.exception.PermissionDeniedException`（U1既存）: Q5の権限検証
  失敗（all-or-nothing拒否）、Q3のUIモード時カラム権限違反、Q4の複数ステートメント注入検知
  で使用する。
- `cherry.mastermeister.common.exception.ValidationException`（U1既存）: Q6の主キーなし
  テーブルへの`RecordUpdate`拒否等、権限とは無関係な入力不正で使用する。
- `cherry.mastermeister.common.exception.EntityNotFoundException`（U1既存）: 存在しない
  `schema`/`table`が指定された場合に使用する（U3`SchemaQueryService.getTableDetail`と同様の
  扱い）。

---

## 設計判断（AI提案、Q1〜Q8の対象外事項）

### `List<Object>`の要素型と`NamedParameterJdbcTemplate`の型マッピング

`RecordListResult.records`の各行要素は、`NamedParameterJdbcTemplate`が返す`ResultSet`の
列値をJavaの標準型にマッピングしたものをそのまま用いる（CLAUDE.md規約の`java.time`使用
方針に準拠、`java.sql.Date`/`Timestamp`等のレガシー型は使用しない）。対象RDBMS4種
（MySQL/MariaDB/PostgreSQL/H2）間の型マッピング差異の吸収方式は、NFR Design/Code
Generationで確定する。