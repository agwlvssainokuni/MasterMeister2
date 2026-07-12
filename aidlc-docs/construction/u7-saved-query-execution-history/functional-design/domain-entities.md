# domain-entities.md — U7: Saved Query / Execution / History

`u7-saved-query-execution-history-functional-design-plan.md`の回答（Q1〜Q11）に基づく
ドメインモデル定義。

---

## 内部DBエンティティ一覧（Q1）

内部DB（H2, JPA）エンティティは`SavedQuery`（`savedquery`パッケージ）と`QueryHistory`
（`queryhistory`パッケージ）の2つのみ。`queryexecution`はステートレスな実行ロジックのみで
完結し、専用エンティティは持たない（U5の`MasterDataMutationService`と同様、対象RDBMSへの
実行は`NamedParameterJdbcTemplate`経由でその場で行い、内部DBには何も保持しない）。

JPA規約（U1〜U6踏襲）: `@ManyToOne`等の関係アノテーションは使用せず、エンティティ間参照は
素の`Long`型FKフィールドで表現する（`ddl-auto: update`によりDBレベルのFK制約も生成されない）。
全エンティティは`@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;`の
サロゲートキーを持つ。

`SavedQuery`は本プロジェクトで初めて「非管理者ユーザが所有し、所有者のみが編集できる」行を
持つエンティティである（U4の`Group`/`PermissionAssignment`は管理者専用操作であり、
「一般ユーザ本人が所有」という性質を持つ既存エンティティは存在しない）。

---

## SavedQuery（保存クエリ、Q2・Q5）

```java
record SavedQuery(
        Long id,
        Long ownerId,           // FK: User.id（U2）、作成者
        Long connectionId,      // FK: RdbmsConnection.id（U3）
        String name,
        String sql,             // :paramN形式のプレースホルダを含み得る文字列
        Visibility visibility,
        boolean retired,        // 既定false、Q5
        int executionCount,     // 既定0、Q3
        Instant createdAt,
        Instant updatedAt
)
```

- `sql`のみを保存し、`QueryBuilderModel`（U6の8タブ構成モデル）は保存しない（Q2）。GEN-16
  「ビルダーで編集」への遷移時は、保存済み`sql`を`SqlParsingService.parse`（U6既存API）で
  再度リバースエンジニアリングしてタブモデルへ復元する。パラメータの**デフォルト値**は
  GEN-10のACに言及がないため保存しない（実行のたびに値入力UIで指定する、GEN-13と同じ挙動）。
- `executionCount`は`executeSavedQuery`呼び出しのたびにアトミックにインクリメントする
  カウンタ（`UPDATE ... SET execution_count = execution_count + 1`相当、Q3）。
- `retired`は`retireQuery`で`true`に設定する一方向のフラグ（復元操作はMVPスコープ外、Q5）。
  `retired=true`の`SavedQuery`は「参照（`getQuery`）のみ許可、編集
  （`updateQuery`）・実行（`executeSavedQuery`）は作成者を含め全員拒否」となる。

### Visibility（enum、Q2）

```java
enum Visibility { PUBLIC, PRIVATE }
```

`PUBLIC`は全ユーザが参照・実行可能、`PRIVATE`は作成者のみ参照・実行可能（GEN-10〜11）。

---

## QueryHistory（実行履歴、Q3・Q3追補）

```java
record QueryHistory(
        Long id,
        Long userId,               // FK: User.id（U2）、実行者
        Long connectionId,         // FK: RdbmsConnection.id（U3）
        String sql,                // 実行時点のSQL文字列そのもの（スナップショット）
        Map<String, Object> params,
        int resultCount,
        long elapsedMillis,
        Instant executedAt,
        Long savedQueryId,         // nullable。手入力SQL実行時はnull
        String savedQueryName,     // nullable。実行時点での保存クエリ名のスナップショット
        Integer executionCount     // nullable。手入力SQL実行時はnull
)
```

- `sql`/`params`/`savedQueryName`はいずれも実行時点のスナップショットであり、`SavedQuery`が
  後から編集・廃止（`retired=true`）されても影響を受けない（Q3追補・Q5訂正: `retired`は
  `savedquery`側の一覧・詳細画面の既定非表示にのみ影響し、`QueryHistory`の内容には一切
  影響しない）。`savedQueryName`をスナップショットとして複製する理由は、`savedQueryId`から
  都度`SavedQueryService.getQuery`で名前を引く設計だと、廃止後に名前解決ができなくなって
  しまうため。
- `AuditLog`（U1、`EventCategory.DATA_ACCESS` / `EventType.QUERY_EXECUTED`）とは別エンティティ・
  別呼び出しのまま独立させる（`AuditLogService.record`は実行のたびに`QueryExecutionService`が
  明示的に呼び出す、`AuditLog.summaryMessage`は概要のみで`QueryHistory`が詳細を保持する
  役割分担）。

---

## クエリ実行結果（`QueryResult`、Q8）

```java
record QueryResult(
        List<ResultColumn> columns,
        List<List<Object>> rows,
        int totalRows,          // ページングなし時は rows.size() と一致、ページングあり時は不明
        boolean truncated       // mm.app.query-execution.max-result-rows 超過により切り捨てた場合true（Q11）
)

record ResultColumn(String columnName, String dataType)
```

U5の`RecordListResult`（`columns` + `records`、JDBCの`ResultSet`/`ResultSetMetaData`構造を
踏襲）と同一の形（カラムメタデータ＋結果行の配列）で統一する（U5からの申し送り事項どおり）。
ただし`masterdata`パッケージへの直接のバックエンド依存は追加しない——`ResultColumn`は
`masterdata.ColumnMetadata`と構造的に類似するが、`effectivePermission`フィールドは持たない
（`queryexecution`はU4に依存しないため権限概念自体を持たない、Q4）。値の型は
`NamedParameterJdbcTemplate`のデフォルト型マッピング（`java.time.LocalDate`/`LocalDateTime`/
`OffsetDateTime`、`BigDecimal`、`String`、`Boolean`等）をそのまま用いる。

---

## パラメータ（`ParamValue`、Q6）

パラメータは`Map<String, Object>`として受け渡す（専用の値型は定義しない、Q6）。値の型変換は
行わずJDBCドライバのデフォルトの型変換に委ねる。

```java
record DetectedParam(String name)   // parseParams APIの戻り値要素、値はまだ持たない
```

`:param`形式のプレースホルダをSQL文字列から検出した結果（パラメータ名のみ）を返す軽量な型。
フロントエンドはこれを元に値入力UIを描画し、実際の実行時に`Map<String, Object>`として
値を渡す。

---

## ページング指定（`PagingOption`、Q7）

```java
record PagingOption(boolean enabled, int page, int pageSize)
```

`enabled=true`の場合、入力SQLをサブクエリとしてラップし外側に`DialectStrategy.
buildPagingClause`でLIMIT/OFFSET相当句を付与する（Q7）。`enabled=false`の場合は無視され、
`mm.app.query-execution.max-result-rows`（Q11）が適用される。

---

## 実行履歴の絞り込み条件（`HistoryFilterCriteria`、Q8）

```java
record HistoryFilterCriteria(
        Instant executedAtFrom,    // nullable
        Instant executedAtTo,      // nullable
        ExecutorScope executorScope,
        String sqlTextSearch       // nullable、部分一致（LIKE '%...%'相当）
)

enum ExecutorScope { ALL, SELF }
```

`executorScope=ALL`は管理者ロール制約を設けず、認証済みユーザなら誰でも選択可能（Q8、
`REQUIREMENTS.md` 5.8・`stories.md` GEN-15で要件として明記済み）。

### SavedQueryStatus（`SavedQueryService`の新設バッチ判定API、Q8追補）

```java
record SavedQueryStatus(boolean visibleToViewer, boolean retired)
```

`queryhistory`が`listHistory`のページ内の行について、参照先`SavedQuery`が「今も見えるか
（`visibleToViewer`）」「廃止済みか（`retired`）」を判定するために`SavedQueryService`へ
問い合わせるバッチAPIの戻り値。`visibleToViewer = (visibility==PUBLIC || ownerId==viewerId)`
（`retired`は独立したフィールドとして返す、可視性マトリクスは`business-rules.md` 5参照）。
この型の新設に伴い、`component-dependency.md`に`queryhistory → savedquery`という新規依存が
1本追加される（既存の`queryexecution → savedquery`とは別方向、循環依存にはならない）。

---

## 例外（既存の共通例外クラスを再利用、新規定義しない）

U6と同じ方針（`querybuilder/domain-entities.md`）を踏襲し、`component-methods.md`草案が
示唆する`ReadOnlyViolationException`のような専用例外クラスは定義せず、既存の共通例外を
再利用する。

- `cherry.mastermeister.common.exception.EntityNotFoundException`（U1既存）: 存在しない
  `connectionId`/`savedQueryId`を指定した場合（`getQuery`/`updateQuery`/`retireQuery`/
  `executeSavedQuery`）。`retired=true`のクエリへの`getQuery`以外の操作（`updateQuery`/
  `executeSavedQuery`）もこれで表現する（廃止済みは「編集・実行の対象としては存在しない」
  という扱い、Q5）。
- `cherry.mastermeister.common.exception.PermissionDeniedException`（U1既存）: Private な
  `SavedQuery`への作成者以外からの`getQuery`/`executeSavedQuery`アクセス（GEN-11）、
  作成者以外からの`updateQuery`/`retireQuery`呼び出し（GEN-12）。
- `cherry.mastermeister.common.exception.ValidationException`（U1既存）: 読み取り専用でない
  SQL（更新系SQL、JSqlParserでの`Select`型判定に失敗、Q4）を`executeAdhocSql`/
  `executeSavedQuery`に渡した場合（`component-methods.md`草案の`ReadOnlyViolationException`
  を代替）。JSqlParserのパース自体に失敗した場合も同様に扱う（安全側に倒す、Q4）。

新規の専用例外クラスは定義しない。