# business-logic-model.md — U6: Query Builder

`domain-entities.md`・`business-rules.md`で定義したモデル・ルールに基づく業務フロー。
技術非依存（実装方式はNFR Design/Code Generationで確定）。

---

## フロー1: スキーマ/テーブル/カラム選択（GEN-6, GEN-7）

**関与コンポーネント**: フロントエンド`queryBuilder/`（`QueryBuilderPage` →
`FromJoinTab`/`SelectTab`/`WhereHavingTab`/`GroupByOrderByTab`） →
`QueryBuilderMetadataService` → `EffectivePermissionResolver`（U4） →
`SchemaQueryService`（U3）

1. `QueryBuilderPage`が対象RDBMS接続を選択し、`listSelectableSchemas`を呼び出す
   （`business-rules.md` 1.1）。
2. `EffectivePermissionResolver.listAccessibleSchemas`が`READ`以上のテーブルを1件以上含む
   スキーマのみを返す。
3. ユーザがスキーマを選択すると`FromJoinTab`が`listSelectableTables`を呼び出し、
   `resolveEffectiveTablePermission`が`READ`以上と判定したテーブルのみ`TableRef`一覧として
   表示する（GEN-6 AC）。
4. `FromJoinTab`でベーステーブル・JOINテーブルとエイリアスが指定されると、以降の各タブ
   （SELECT/WHERE/GROUP BY/HAVING/ORDER BY）は選択済みテーブル/エイリアスに対して
   `listSelectableColumns`を呼び出し、`resolveEffectiveColumnPermissions`が`READ`以上と
   判定したカラムのみ`ColumnRef`一覧として表示する（GEN-7 AC）。
5. SELECT/HAVING/ORDER BYタブでは`ColumnRef`ごとに`AggregateFunction`選択肢
   （`NONE, COUNT, SUM, AVG, MIN, MAX`）を提供する（`business-rules.md` 4.1）。

---

## フロー2: クエリビルダーからのSQL生成（GEN-8）

**関与コンポーネント**: フロントエンド`queryBuilder/`（`GeneratedSqlPanel`） →
`SqlGenerationService` → `common/dialect/DialectStrategy`（U1）

1. 各タブで組み立てられた`QueryBuilderModel`（`domain-entities.md`）を
   `SqlGenerationService.generate(model)`に渡す。
2. `groupByColumns`が空でない場合、SELECT/ORDER BYの非集計カラムが`groupByColumns`に
   含まれることを検証する。含まれない場合は`ValidationException`（`business-rules.md` 4.2）。
3. FROM句・JOIN句（`joinItems`、`business-rules.md` 3.1）・WHERE句（AND結合のみ、
   `business-rules.md` 2.1）・GROUP BY句・HAVING句・ORDER BY句（NULLS順序は
   `DialectStrategy.buildNullsOrderingClause`）・LIMIT OFFSET句
   （`DialectStrategy.buildPagingClause`）を順に組み立てる。識別子は
   `DialectStrategy.quoteIdentifier`でクオートする（`business-rules.md` 5.1）。
4. 条件値は文字列連結せず`:paramN`形式のプレースホルダに置き換え、値を`Map<String, Object>`
   に集約する（`business-rules.md` 5.2）。
5. `GeneratedSql(sql, params)`を返す。`GeneratedSqlPanel`が生成SQLとパラメータを表示し、
   コピー・「保存」「実行」ボタン（Q9、プレースホルダ）を提供する（`business-rules.md` 7）。

---

## フロー3: 手入力SQLの解析とタブ反映（GEN-9）

**関与コンポーネント**: フロントエンド`queryBuilder/`（`SqlReverseParsePanel`） →
`SqlParsingService` → `EffectivePermissionResolver`（U4）

1. クエリ実行画面/クエリ履歴からの遷移時（U7未着手のためU6単体では
   `SqlReverseParsePanel`への手動貼り付け入力でも代替）、`rawSql`を
   `SqlParsingService.parse(rawSql)`に渡す。
2. JSqlParserでASTを構築する。パースに失敗した場合、または`QueryBuilderModel`で表現できない
   構文（サブクエリ・UNION・CTE・ウィンドウ関数・OR条件・括弧グルーピング等）を検出した場合は
   `fullyParsed = false`、`notice`に理由を設定する（`business-rules.md` 6.1, 6.2）。
3. ASTを`QueryBuilderModel`にマッピングできた場合、参照する全テーブル/カラムについて
   `resolveEffectiveTablePermission`/`resolveEffectiveColumnPermissions`で権限を検証する。
   `READ`未満のテーブル/カラムを参照していた場合は`fullyParsed = false`とし、`notice`に
   権限エラーである旨を設定する（`business-rules.md` 1.2）。
4. `fullyParsed = true`の場合、`model`をもとに各タブ（`FromJoinTab`/`SelectTab`/
   `WhereHavingTab`/`GroupByOrderByTab`/`LimitOffsetTab`）へ反映する。
5. `fullyParsed = false`の場合、`SqlReverseParsePanel`が`notice`を表示し、タブへの反映は
   行わない（GEN-9 AC「解析できない複雑なSQLの場合は、その旨を表示する」）。

---

## テスト可能な性質（Testable Properties, PBT-01）

`property-based-testing`拡張（enabled）のRule PBT-01に基づき、本ユニットの業務ロジック
（フロー1〜3）が持つ性質をカテゴリ別に識別する。実際のPBTケース設計・生成器定義はCode
Generation計画時に確定する。

| # | 対象 | カテゴリ | 性質 | 備考 |
|---|---|---|---|---|
| P1 | `QueryBuilderMetadataService.listSelectableColumns`のフィルタ（フロー1） | Invariant | 返される`ColumnRef`は常に呼び出しユーザの実効カラム権限が`READ`以上であるカラムのみ——`READ`未満のカラムが結果に含まれることは一切ない | `business-rules.md` 1.1 |
| P2 | `SqlGenerationService.generate`のJOIN句組み立て（フロー2） | Invariant | 生成SQLに含まれるJOINキーワードは常に`INNER JOIN`/`LEFT JOIN`/`RIGHT JOIN`のいずれかであり、`FULL`は一切出現しない | `business-rules.md` 3.1, Q4 |
| P3 | `SqlGenerationService.generate`のGROUP BY制約検証（フロー2） | Invariant（境界値） | `groupByColumns`が非空かつSELECT/ORDER BYに`groupByColumns`未含有の非集計カラムが1件でも存在する場合、常に`ValidationException`となる | `business-rules.md` 4.2 |
| P4 | `SqlGenerationService.generate`のWHERE/HAVING句組み立て（フロー2） | Invariant | 生成SQLのWHERE/HAVING句に`OR`キーワードや条件の括弧グルーピングが一切出現しない（`whereConditions`/`havingConditions`は常にAND結合として組み立てられる） | `business-rules.md` 2.1, Q3 |
| P5 | `SqlGenerationService.generate`のパラメータ化（フロー2） | Invariant | `GeneratedSql.sql`中の`:paramN`プレースホルダ集合と`GeneratedSql.params`のキー集合は常に一致する（一方にしか存在するプレースホルダ/キーは生じない） | `business-rules.md` 5.2 |
| P6 | `SqlGenerationService.generate`の識別子クオート（フロー2） | Invariant | 生成SQLに含まれるスキーマ名・テーブル名・カラム名・エイリアスは常に`DialectStrategy.quoteIdentifier`でクオートされた形で出現する（未クオートの識別子は一切出現しない） | `business-rules.md` 5.1 |
| P7 | `SqlParsingService.parse`の非対応構文検出（フロー3） | Invariant | サブクエリ・UNION・CTE・ウィンドウ関数・OR条件・括弧グルーピングのいずれかを含む構文的に正しいSQLを入力した場合、常に`fullyParsed = false`となる（誤って`model`を返すことはない） | `business-rules.md` 6.1, 6.2 |
| P8 | `SqlParsingService.parse`と`SqlGenerationService.generate`の往復（フロー2→3） | Invariant（状態遷移） | `generate`が受理する任意の`QueryBuilderModel`について、`generate(model)`の結果を`parse`した`ParseResult`は`fullyParsed = true`であり、その`model`は元の`model`と（フィールド順・値の集合として）等価である | ラウンドトリップ性質、GEN-8/GEN-9連携 |
| P9 | `SqlParsingService.parse`の権限フィルタ（フロー3） | Invariant | 解析対象SQLが呼び出しユーザにとって`READ`未満のテーブル/カラムを1件でも参照する場合、構文的に完全に解析可能であっても常に`fullyParsed = false`となる | `business-rules.md` 1.2、U4 P1系との連携 |
| P10 | `SqlGenerationService.generate`のLIMIT OFFSET句（フロー2） | Invariant（境界値） | `limit`/`offset`が`null`の場合は常にLIMIT OFFSET句を含まないSQLが生成され、非`null`の場合は常に`DialectStrategy.buildPagingClause`が返す形式のLIMIT OFFSET句を含む | `business-rules.md` 5.1 |