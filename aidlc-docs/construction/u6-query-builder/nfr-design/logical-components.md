# logical-components.md — U6: Query Builder

`nfr-design-patterns.md`に基づく論理コンポーネント一覧。

---

## 1. Query Builder（`cherry.mastermeister.querybuilder`）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `QueryBuilderMetadataService` | Service | `listSelectableSchemas`/`listSelectableTables`/`listSelectableColumns`（`business-rules.md` 1.1、フロー1）。`SchemaQueryService`（U3）・`EffectivePermissionResolver`（U4）への都度委譲のみで完結し、独自キャッシュは持たない（`nfr-requirements.md` 3.1） |
| `SqlGenerationService` | Service | `generate`（`business-rules.md` 5.1-5.2、フロー2）。`QueryBuilderModel`から`StringBuilder`ベースでSQL文字列を組み立て、`DialectStrategy`（U1既存）の`quoteIdentifier`/`buildPagingClause`/`buildNullsOrderingClause`を再利用する。GROUP BY制約違反（`business-rules.md` 4.2）・各リスト件数上限超過（`nfr-requirements.md` 4.1）を`ValidationException`で検証する |
| `SqlParsingService` | Service | `parse`（`business-rules.md` 6.1-6.2、フロー3）。共有`ExecutorService`上でJSqlParser（`CCJSqlParserUtil.parse`）を呼び出し、Visitorクラス群でAST→`QueryBuilderModel`変換を行う（`nfr-design-patterns.md` 1.2、2.1）。権限外参照（`business-rules.md` 1.2）・非対応構文（`business-rules.md` 6.2）・タイムアウト（`nfr-design-patterns.md` 2.1）はいずれも`fullyParsed = false`として扱う |
| Visitorクラス群（`WhereConditionVisitor`等、クラス名はCode Generationで確定） | Visitor | JSqlParserの`ExpressionVisitorAdapter`等を継承し、WHERE/HAVING句の式ツリー・SELECT項目・JOIN句をそれぞれ`QueryBuilderModel`のフィールドへ変換する（`nfr-design-patterns.md` 1.2） |
| `TableRef`/`ColumnRef`/`QueryBuilderModel`一式（`FromItem`/`JoinItem`/`SelectItem`/`Condition`/`OrderByItem`等）/`GeneratedSql`/`ParseResult` | DTO（`record`） | 呼び出し境界データ構造（`domain-entities.md`確定） |

**依存方向**: `querybuilder → common, permission, schema, rdbmsconnection`
（`component-dependency.md`確定済み）の一方向のみ。`masterdata`/`queryexecution`/`savedquery`
への依存は持たない（`business-rules.md` 7）。`common`への切り出しは行わない
（`nfr-design-patterns.md` 1.1参照）。

---

## 2. SQL解析パーサのタイムアウト制御構成（`nfr-design-patterns.md` 2.1）

| 項目 | 値 |
|---|---|
| `ExecutorService`の生成単位 | `SqlParsingService`のインスタンスフィールド（Bean生成時に1回のみ生成、リクエストごとの使い捨て生成は行わない） |
| プールサイズ設定キー | `mm.app.query-builder.parse-executor-pool-size`（既定`4`） |
| タイムアウト待機方式 | `Future.get(parseTimeoutSeconds, TimeUnit.SECONDS)`（`mm.app.query-builder.parse-timeout`、既定`5`秒） |
| タイムアウト超過時の扱い | `future.cancel(true)`実行後、例外化せず`ParseResult(fullyParsed = false, model = Optional.empty(), notice = "解析に時間がかかりすぎたため中断しました")`を返す |

---

## 3. Frontend（`features/queryBuilder/`）

`u6-query-builder/functional-design/frontend-components.md`で確定済みのコンポーネント構成
（`QueryBuilderPage`/`FromJoinTab`/`SelectTab`（全カラム一括追加機能を含む）/`WhereHavingTab`/
`GroupByOrderByTab`/`LimitOffsetTab`/`GeneratedSqlPanel`/`SqlReverseParsePanel`/`api.ts`）を
そのまま踏襲する（本ステージでの追加変更なし）。

---

## 4. 設定ファイル

| ファイル | 内容 |
|---|---|
| `application.yml` | `mm.app.query-builder.parse-max-length`（既定`10000`文字）、`mm.app.query-builder.parse-timeout`（既定`5`秒）、`mm.app.query-builder.parse-executor-pool-size`（既定`4`）、`mm.app.query-builder.max-select-items`（既定`100`）、`mm.app.query-builder.max-join-items`（既定`10`）、`mm.app.query-builder.max-where-conditions`（既定`30`）、`mm.app.query-builder.max-group-by-columns`（既定`30`）、`mm.app.query-builder.max-having-conditions`（既定`20`）、`mm.app.query-builder.max-order-by-items`（既定`20`） |
| `build.gradle.kts` | `dependencyManagement`ブロックに`com.github.jsqlparser:jsqlparser`のバージョンを追加（`tech-stack-decisions.md`確定、正確なバージョン番号はCode Generationで確定） |

---

## 5. U3/U4/U6責務境界の再確認

- `querybuilder`パッケージは内部DBエンティティを一切持たず、`schema`（U3）・`permission`
  （U4）の既存コンポーネントへの都度委譲、またはJSqlParserによるインメモリ処理のみで完結する
  （`domain-entities.md`確定）。
- 対象RDBMSへのライブ接続・SQL実行はU6の責務範囲外であり（`business-rules.md` 7）、
  `ConnectionPoolRegistry`/`NamedParameterJdbcTemplate`への依存を持たない。