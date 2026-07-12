# business-rules.md — U6: Query Builder

`u6-query-builder-functional-design-plan.md`の回答（Q1〜Q10）に基づく業務ルール定義。

---

## 1. メタデータ選択と権限フィルタ（GEN-6, GEN-7, Q6）

### 1.1 スキーマ/テーブル/カラム選択の権限フィルタ

`querybuilder`のUI組立系（SELECT/FROM/JOIN/WHERE/GROUP BY/HAVING/ORDER BY全タブ）は例外なく
`EffectivePermissionResolver`（U4既存）による読み取り権限（`READ`以上）フィルタを適用する。
U5のGEN-2（手入力WHERE/ORDER BY）のような「権限フィルタ対象外」の例外は`querybuilder`には
存在しない——U6は常にUI経由の組み立てであり、生SQL文字列を直接実行する経路を持たない。

- `listSelectableSchemas` → `EffectivePermissionResolver.listAccessibleSchemas`
- `listSelectableTables` → `EffectivePermissionResolver.listAccessibleTables` +
  `resolveEffectiveTablePermission`が`READ`以上のテーブルのみ`TableRef`化
- `listSelectableColumns` → `SchemaQueryService.getTableDetail`（U3既存）でカラム一覧を取得し、
  `EffectivePermissionResolver.resolveEffectiveColumnPermissions`が`READ`以上と判定した
  カラムのみ`ColumnRef`化

UIの選択肢自体に権限外テーブル/カラムが現れない構造とする（U5 Q2と同じ「そもそも見せない」
方針）。

### 1.2 権限外参照を含む解析結果の扱い（GEN-9, Q6後半）

`SqlParsingService.parse`が構文解析に成功しても、参照するテーブル/カラムに`READ`未満の
ものが含まれる場合は`ParseResult.fullyParsed = false`とし、`notice`に「アクセス権限のない
カラム/テーブルを含むため反映できません」等のメッセージを設定する。解析はできてもモデルに
反映しない、という区別ではなく、権限外参照は構文解析成否とは独立に「反映不可」として扱う
（`domain-entities.md`の`ParseResult`）。

---

## 2. WHERE/HAVING条件の構造（Q3）

### 2.1 AND結合のみ、OR・括弧グルーピングはMVPスコープ外

WHERE/HAVINGともにAND結合のみとし、OR・括弧によるグルーピングはMVPスコープ外とする
（GEN-7のACに忠実）。`Condition`はフラットな`List`で表現し（`domain-entities.md`）、
AND/ORが混在する条件木は導入しない。将来的にOR・括弧対応が必要になった場合は`Condition`を
`AND`/`OR`/`LEAF`の再帰構造に置き換える拡張余地があるが、SQL生成・解析（GEN-9）双方の
実装が複雑化するため、本ユニットのスコープでは見送る。

---

## 3. JOIN（Q4）

### 3.1 サポートするJOIN種別と方言差異

`JoinType`は`INNER`/`LEFT`/`RIGHT`の3種類のみサポートする（MySQL/MariaDB/PostgreSQL/H2
全てがネイティブ構文でサポートする共通部分）。`FULL OUTER JOIN`はMySQL/MariaDBが構文として
サポートしない（`UNION`によるエミュレートが必要）ため、MVPスコープ外とする。`UNION`
エミュレーションは生成SQLの複雑度・可読性を大きく損なうため見送る。

`DialectStrategy`（U1既存）にJOIN句組み立て用のメソッドは存在しない
（`quoteIdentifier`/`buildPagingClause`/`buildNullsOrderingClause`/`buildJdbcUrl`のみ）。
JOIN構文自体（`INNER JOIN` / `LEFT JOIN` / `RIGHT JOIN` キーワード）は4RDBMS共通のため、
`DialectStrategy`の拡張は不要——`SqlGenerationService`側で直接組み立てる。

---

## 4. 集計関数とGROUP BY制約（Q5）

### 4.1 サポートする集計関数、DISTINCT非対応

`AggregateFunction`は`NONE, COUNT, SUM, AVG, MIN, MAX`の6値とする。4RDBMS共通でサポートされる
基本的な集計関数のみとし、`DISTINCT`修飾（`COUNT(DISTINCT col)`等）はMVPスコープ外とする
（要件に明記なし、UIの複雑化を避ける）。

### 4.2 GROUP BY制約違反のエラー化

`groupByColumns`に含まれないカラムをSELECT/ORDER BYで非集計（`aggregateFunction = NONE`）
指定するケースは、標準SQLのGROUP BY制約に合わせ、SQL生成時（`SqlGenerationService.generate`）
に`ValidationException`（U1既存）とする。`groupByColumns`が空の場合は本制約を適用しない
（非集計クエリ）。

---

## 5. SQL生成（GEN-8, Q7）

### 5.1 自前StringBuilder実装、DialectStrategy再利用

外部SQL構築ライブラリ（jOOQ等）は導入せず、`QueryBuilderModel`から`StringBuilder`ベースで
直接SQL文字列を組み立てる自前実装とする。

- 識別子のクオート: `DialectStrategy.quoteIdentifier`（U1既存）を再利用
- LIMIT OFFSET句: `DialectStrategy.buildPagingClause`（U1既存）を再利用
- ORDER BYのNULLS順序: `DialectStrategy.buildNullsOrderingClause`（U1既存）を再利用
- JOIN句・WHERE句・GROUP BY/HAVING句・ORDER BY句の組み立てロジックは`querybuilder`側に
  新規実装する

### 5.2 パラメータ化（SQLインジェクション対策）

パラメータ値はSQLインジェクション対策として文字列連結せず、生成SQLは`:paramN`形式の
プレースホルダとし、値は別途`Map<String, Object>`として返す（`GeneratedSql`、
`domain-entities.md`）。`NamedParameterJdbcTemplate`（U3既存）が実行時に利用する形式であり、
U7 `QueryExecutionService`とのインタフェース整合、`REQUIREMENTS.md` 5.7の":param"形式
パラメータ対応に合わせる。

---

## 6. SQL解析（GEN-9, Q8）

### 6.1 JSqlParser導入、解析範囲の限定

外部SQLパーサライブラリ（JSqlParser等、ANSI SQL準拠のオープンソースパーサ）を導入し、
パース結果のASTを`QueryBuilderModel`へマッピングする自前の変換ロジックを実装する。自前の
文字列解析（正規表現等）でSQL構文を解析するのは対象4RDBMSの方言差異・エッジケースの網羅が
現実的でないため避ける。

「解析可能」の範囲は`QueryBuilderModel`が表現できる構文（単純な`SELECT ... FROM ...
[INNER/LEFT/RIGHT JOIN ...] [WHERE ...] [GROUP BY ...] [HAVING ...] [ORDER BY ...]
[LIMIT/OFFSET]`、2.1で確定したAND結合のみのWHERE/HAVING、3.1で確定したJOIN種別、4.1で
確定した集計関数）に限定する。

### 6.2 解析不能構文の検出とnotice

サブクエリ・UNION・CTE（WITH句）・ウィンドウ関数・OR条件・括弧によるグルーピング等、
`QueryBuilderModel`で表現できない構文を検出した場合は`ParseResult.fullyParsed = false`、
`notice`に非対応構文の種類を可能な範囲で含めたメッセージを設定する（GEN-9のAC「解析できない
複雑なSQLの場合は、その旨を表示する」に対応）。権限外参照の扱いは1.2を参照。

---

## 7. GEN-8「保存・実行」連携の責務範囲（Q9, U7未着手）

U6の責務範囲は「生成したSQLとパラメータ（`GeneratedSql`）をフロントエンド画面上に表示し、
コピー可能にする」までとする。「保存」「実行」ボタンは画面上に用意するが、U6時点では
遷移先ページ（U7の`savedquery`/`queryexecution`画面）が存在しないため、ボタン押下時の
実際の画面遷移・API連携はU7のFunctional Design/Code Generationで実装する。バックエンド
`querybuilder`パッケージ自体は`savedquery`/`queryexecution`への依存を持たない
（`component-dependency.md`のとおり）。詳細は`frontend-components.md`のU7申し送り事項を
参照。

---

## 8. API認可（`SecurityConfig`、U1 NFR Design 1.3の規約に基づく）

| パスパターン（想定、正確なパスはCode Generationで確定） | 対象 |
|---|---|
| `GET /api/query-builder/{connectionId}/schemas` | `QueryBuilderMetadataService.listSelectableSchemas` |
| `GET /api/query-builder/{connectionId}/schemas/{schema}/tables` | `QueryBuilderMetadataService.listSelectableTables` |
| `GET /api/query-builder/{connectionId}/schemas/{schema}/tables/{table}/columns` | `QueryBuilderMetadataService.listSelectableColumns` |
| `POST /api/query-builder/generate` | `SqlGenerationService.generate` |
| `POST /api/query-builder/parse` | `SqlParsingService.parse` |

全エンドポイントは認証済みユーザ（一般ユーザ/管理者いずれも利用可）を対象とし、管理者限定の
制約は課さない（GEN-6〜GEN-9のACに管理者限定の記載なし、マスタデータ閲覧・編集権限を持つ
一般ユーザが利用する機能のため）。実際のテーブル/カラムへのアクセス可否は1.1の権限フィルタで
制御される。