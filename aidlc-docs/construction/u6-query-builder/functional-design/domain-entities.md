# domain-entities.md — U6: Query Builder

`u6-query-builder-functional-design-plan.md`の回答（Q1〜Q10）に基づくドメインモデル定義。

---

## 内部DBエンティティなし（Q1 = A）

`querybuilder`パッケージが保持する状態は一切なく、対象RDBMSメタデータ参照
（`SchemaQueryService`、U3）・権限判定（`EffectivePermissionResolver`、U4）への委譲と、
リクエストごとの純粋なSQL生成/解析処理のみで完結する。以下すべての型はJPA非対応の純粋な
`record`/`enum`（service/dto層のPOJO）とする。

---

## メタデータ参照系（`QueryBuilderMetadataService`）

### TableRef（選択可能テーブル、Q1補足）

```java
record TableRef(String schema, String table, String comment)
```

`listSelectableTables`が`EffectivePermissionResolver.resolveEffectiveTablePermission`で
`READ`以上と判定したテーブルのみを`TableRef`として返す。`schema.TableMetadata`
（U3既存、`tableType`を保持）とは異なり、`querybuilder`ではテーブル種別（TABLE/VIEW）の
区別を要求するAC（GEN-6/GEN-7）がないため`tableType`フィールドは持たない。

### ColumnRef（選択可能カラム、Q1補足）

```java
record ColumnRef(String columnName, String dataType, boolean nullable)
```

`listSelectableColumns`が`EffectivePermissionResolver.resolveEffectiveColumnPermissions`で
`READ`以上と判定したカラムのみを`ColumnRef`として返す（Q6「そもそも見せない」方針）。
`masterdata.ColumnMetadata`（U5既存）と異なり`effectivePermission`フィールドを持たない
——`querybuilder`は読み取り専用の選択肢提供であり、`READ`/`UPDATE`の区別が不要なため。

---

## クエリモデル（`QueryBuilderModel`、Q2〜Q5）

### QueryBuilderModel

```java
record QueryBuilderModel(
        List<SelectItem> selectItems,
        FromItem fromItem,
        List<JoinItem> joinItems,
        List<Condition> whereConditions,
        List<String> groupByColumns,
        List<Condition> havingConditions,
        List<OrderByItem> orderByItems,
        Integer limit,
        Integer offset
)
```

`fromItem`は単数の`FromItem`（FROMタブは常に1件のベーステーブル）。`joinItems`のみ複数件を
許容する`List<JoinItem>`とする（Q2でのユーザ指摘により`fromItems`（List）から修正）。

### FromItem / JoinItem / JoinType（Q4）

```java
record FromItem(String schema, String table, String alias)

record JoinItem(JoinType type, String schema, String table, String alias, Condition onCondition)

enum JoinType { INNER, LEFT, RIGHT }
```

`JoinType`は4RDBMS（MySQL/MariaDB/PostgreSQL/H2）全てがネイティブ構文でサポートする
`INNER`/`LEFT`/`RIGHT`の3種類のみ。`FULL`はMVPスコープ外（`business-rules.md` 3.1）。

### SelectItem / AggregateFunction（Q5）

```java
record SelectItem(String tableAlias, String columnName, AggregateFunction aggregateFunction,
                   String outputAlias)

enum AggregateFunction { NONE, COUNT, SUM, AVG, MIN, MAX }
```

`NONE`は非集計カラム選択を表す。`DISTINCT`修飾（`COUNT(DISTINCT col)`等）はMVPスコープ外
（`business-rules.md` 4.1）。

### Condition / Operator（Q3, Q6）

```java
record Condition(String tableAlias, String columnName, AggregateFunction aggregateFunction,
                  Operator operator, Object value)

enum Operator { EQ, NE, GT, LT, GE, LE, LIKE, IS_NULL, IS_NOT_NULL }
```

WHERE/HAVINGタブで共有するフラットな条件モデル。AND結合のみ（OR・括弧によるネスト構造は
MVPスコープ外、`business-rules.md` 2.1）。`aggregateFunction`はHAVING条件で集計値に対する
比較を表現するために持たせる（WHERE条件では常に`NONE`）。

`Operator`は`masterdata.Operator`（U5既存）と値集合は同一だが、`component-dependency.md`の
とおり`querybuilder`は`masterdata`に依存しないため、`querybuilder`パッケージ内に独立して
再定義する（型の共有はしない、命名規約のみ揃える）。

### OrderByItem

```java
record OrderByItem(String tableAlias, String columnName, AggregateFunction aggregateFunction,
                    SortDirection direction)
```

`SortDirection`は`cherry.mastermeister.common.dialect.SortDirection`（U1既存、`ASC`/`DESC`）を
再利用する（新規定義しない）。

---

## SQL生成結果（`GeneratedSql`、Q7）

```java
record GeneratedSql(String sql, Map<String, Object> params)
```

`sql`は`:paramN`形式のプレースホルダを含む文字列、`params`はプレースホルダ名と値の対応。
`NamedParameterJdbcTemplate`（U3既存、`ConnectionPoolRegistry.getJdbcTemplate`）がそのまま
実行時に利用できる形式とする（U7 `QueryExecutionService`とのインタフェース整合、
`REQUIREMENTS.md` 5.7）。

---

## SQL解析結果（`ParseResult`、Q8）

```java
record ParseResult(boolean fullyParsed, Optional<QueryBuilderModel> model, Optional<String> notice)
```

- `fullyParsed = true`: `rawSql`が`QueryBuilderModel`で表現可能な構文範囲内に収まり、
  かつ参照するテーブル/カラムがすべて読み取り権限（R以上）を満たす場合。`model`に変換結果を
  設定、`notice`は空。
- `fullyParsed = false`: 非対応構文（サブクエリ・UNION・CTE・ウィンドウ関数・OR条件・
  括弧グルーピング等）を検出した場合、または権限外のテーブル/カラムを参照している場合
  （Q6）。`model`は空、`notice`に理由メッセージを設定する。

---

## 例外（既存の共通例外クラスを再利用、新規定義しない）

- `cherry.mastermeister.common.exception.EntityNotFoundException`（U1既存）: 存在しない
  `connectionId`/スキーマ/テーブルを指定した場合（`QueryBuilderMetadataService`）。
- `cherry.mastermeister.common.exception.ValidationException`（U1既存）: GROUP BY制約違反
  （`business-rules.md` 4.2）等、`SqlGenerationService.generate`が受け付けられない
  `QueryBuilderModel`を検出した場合。
- `cherry.mastermeister.common.exception.PermissionDeniedException`（U1既存）: 本ユニットでは
  基本的に「権限外は選択肢に出さない／`fullyParsed=false`で通知する」方針（Q6）のため
  例外送出のケースは限定的だが、メタデータ取得API自体への接続権限がない場合等に利用する。

新規の専用例外クラスは定義しない。

---

## 設計判断（AI提案、Q1〜Q10の対象外事項）

### `Operator`/`JoinType`/`AggregateFunction`を`querybuilder`パッケージ内に独立定義する理由

`component-dependency.md`で確定した依存マトリクス（`querybuilder → common, permission,
schema, rdbmsconnection`）は`masterdata`を含まない。`masterdata.Operator`と値集合が同一でも
型を共有すると逆方向の暗黙依存が生まれるため、`querybuilder`側に構造的に同一だが独立した
`enum`を定義する。

### `GeneratedSql.params`のキー命名規則

`:param1`, `:param2`, ... の連番形式とする（`SqlGenerationService`実装時に生成順で採番）。
具体的な採番アルゴリズムはCode Generationで確定する。