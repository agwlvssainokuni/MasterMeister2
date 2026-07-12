# business-logic-summary.md — U6: Query Builder

Step 2（ビジネスロジック生成）・Step 3（ビジネスロジック単体テスト）で生成したクラス一覧と、
`business-logic-model.md`のP1〜P10との対応関係。

## 生成クラス一覧（Step 2）

| パッケージ | クラス/インタフェース | 役割 |
|---|---|---|
| `querybuilder` | `TableRef`, `ColumnRef`（record） | 選択可能テーブル/カラムの読み取り系DTO（フロー1） |
| `querybuilder` | `JoinType`, `AggregateFunction`, `Operator`（enum） | JOIN種別（`INNER/LEFT/RIGHT`）、集計関数、WHERE/HAVING比較演算子 |
| `querybuilder` | `FromItem`, `JoinItem`, `SelectItem`, `Condition`, `OrderByItem`, `QueryBuilderModel`（record） | クエリビルダーのモデルDTO一式。`JoinItem.onCondition`（`Condition`型）はJOIN句のON条件を表現し、`value`フィールドに`"alias.column"`形式の文字列を格納することで列参照を表す（WHERE/HAVINGの`Condition.value`が実リテラル値を保持するのとは異なる、Step 2-2時点の設計判断） |
| `querybuilder` | `GeneratedSql`, `ParseResult`（record） | `generate`/`parse`のAPI境界DTO |
| `querybuilder` | `QueryBuilderMetadataService` | `listSelectableSchemas`/`listSelectableTables`/`listSelectableColumns`（実効権限に基づく選択肢フィルタ、フロー1） |
| `querybuilder` | `SqlGenerationService` | `generate`（`QueryBuilderModel`→`GeneratedSql`、件数上限検証・GROUP BY制約検証・`DialectStrategy`によるSQL組み立て、フロー2） |
| `querybuilder` | `AggregateExpressionVisitor`（`ExpressionVisitorAdapter<Void>`） | 単一カラム/単一引数集計関数式を`(tableAlias, columnName, AggregateFunction)`に変換するVisitor（SELECT項目・HAVING条件左辺・ORDER BY項目で共用） |
| `querybuilder` | `WhereConditionVisitor`（`ExpressionVisitorAdapter<Boolean>`） | WHERE/HAVING共通のAND結合限定条件式木を`List<Condition>`に変換するVisitor（内部で`AggregateExpressionVisitor`を再利用） |
| `querybuilder` | `SqlParsingService` | `parse`（手入力SQL文字列→`ParseResult`、共有`ExecutorService`によるタイムアウト制御、非対応構文検出、parse側権限フィルタ、フロー3） |

## 生成テストクラス一覧（Step 3）

| テストクラス | 検証方式 |
|---|---|
| `querybuilder.QueryBuilderMetadataServiceTest` | jqwik `@Property`（`SchemaQueryService`/`EffectivePermissionResolver`をMockitoモック。`generate`/`parse`はいずれも対象RDBMSへJDBC接続しないため、他ユニットと異なりH2 TCPサーバは使用しない） |
| `querybuilder.SqlGenerationServiceTest` | jqwik `@Property`（`RdbmsConnectionRepository`をモック、`DialectStrategy`はH2/MySQL/MariaDB/PostgreSQLの実装を実際に束ねて使用） |
| `querybuilder.SqlParsingServiceTest` | jqwik `@Property`（`RdbmsConnectionRepository`/`EffectivePermissionResolver`をモック） |
| `querybuilder.QueryBuilderRoundTripTest` | jqwik `@Property`（`SqlGenerationService`と`SqlParsingService`の両方を実インスタンス化。`generate`の`:paramN`プレースホルダは`params`の値をSQLリテラルへ埋め戻した上で`parse`に渡す往復検証） |

## P1〜P10対応表

| # | 対象 | 検証テストクラス | 状態 |
|---|---|---|---|
| P1 | `listSelectableColumns`のREAD未満カラム除外Invariant | `QueryBuilderMetadataServiceTest`（`listSelectableColumnsExcludesBelowReadPermissionColumns`） | 実装済み（Step 3-1） |
| P2 | `generate`のJOIN句キーワード限定Invariant（`INNER/LEFT/RIGHT`のみ、`FULL`は出現しない） | `SqlGenerationServiceTest`（`generateJoinKeywordIsAlwaysRestrictedSet`） | 実装済み（Step 3-2） |
| P3 | `generate`のGROUP BY制約違反Invariant（常に`ValidationException`） | `SqlGenerationServiceTest`（`generateRejectsNonAggregatedColumnMissingFromGroupBy`） | 実装済み（Step 3-2） |
| P4 | `generate`のWHERE/HAVING句AND限定Invariant（OR・括弧グルーピング不出現） | `SqlGenerationServiceTest`（`generateBuildsWhereAndHavingAsAndOnlyConjunction`） | 実装済み（Step 3-2） |
| P5 | `generate`のプレースホルダ/paramsキー一致Invariant | `SqlGenerationServiceTest`（`generateKeepsPlaceholdersAndParamsKeysInSync`） | 実装済み（Step 3-2） |
| P6 | `generate`の識別子クオートInvariant | `SqlGenerationServiceTest`（`generateAlwaysQuotesIdentifiers`、4方言全てで検証） | 実装済み（Step 3-2） |
| P7 | `parse`の非対応構文検出Invariant（サブクエリ/UNION/CTE/ウィンドウ関数/OR/括弧） | `SqlParsingServiceTest`（`parseRejectsUnsupportedSyntax`） | 実装済み（Step 3-3） |
| P8 | `generate`→`parse`ラウンドトリップInvariant | `QueryBuilderRoundTripTest`（`generateThenParseRoundTripsToEquivalentModel`） | 実装済み（Step 3-4） |
| P9 | `parse`の権限フィルタInvariant（READ未満テーブル/カラム参照時は常に`fullyParsed=false`） | `SqlParsingServiceTest`（`parseRejectsWhenReferencedTableOrColumnLacksReadPermission`） | 実装済み（Step 3-3） |
| P10 | `generate`のLIMIT OFFSET句有無Invariant | `SqlGenerationServiceTest`（`generateLimitOffsetClausePresenceMatchesNullability`） | 実装済み（Step 3-2） |

**補足**: U6はU2〜U5と同様、P1〜P10すべてがStep 3で実装完了している。`querybuilder`パッケージは
`domain-entities.md`確定（Q1 = A）のとおり内部DBエンティティを一切持たないため、U4のような
Step 8専用リポジトリスタブは存在しない。また、`QueryBuilderMetadataService`/`SqlGenerationService`/
`SqlParsingService`のいずれも対象RDBMSへJDBC接続を直接開かない（メタデータは`SchemaQueryService`
経由、SQL実行は本ユニットの範囲外でU7へ引き継がれる）ため、U3〜U5のテストが用いる
`org.h2.tools.Server`によるH2 TCPサーバは本ユニットでは不要と判断した。

**Step 2完了時点で新規判明した設計要素**: item 2-2（`JoinItem.onCondition`）で、承認済み
`domain-entities.md`は`Condition`型のみ確定しており値の意味は未確定だったため、
`value`フィールドに`"alias.column"`形式の文字列を格納する設計をCode Generation時点で追加した。
item 2-8（`SqlParsingService.parse`）では、スキーマ非修飾のテーブル参照の解決基準
（明示スキーマがあればそれを使用、なければ`CATALOG_BASED`方言かつアクセス可能スキーマが一意の
場合のみそれを使用、それ以外は非対応）を、`generate`側の`qualifiedTableName`のスキーマ省略基準
との往復整合性（P8）のためにCode Generation時点で追加した。item 2-6/2-7のVisitorクラスは
JSqlParser 5.3の実際のAPI（`ExpressionVisitorAdapter<T>`が`<S> T visit(Expression, S)`という
文脈引数付きジェネリックシグネチャを持つこと）をjarの`javap`確認により検証した上で設計した。

**Step 3で新規判明・修正した実装バグ**: Step 3-3実行時、UNION文を含むSQLを`parse`すると
`Select.getPlainSelect()`が`null`ではなく`ClassCastException`を送出することが判明した
（JSqlParser 5.3の`getPlainSelect()`実装は`(PlainSelect) this`という無条件キャストであり、
`PlainSelect`と`SetOperationList`はいずれも抽象クラス`Select`を直接継承する兄弟クラスのため）。
`SqlParsingService.java`を`if (!(select instanceof PlainSelect plainSelect))`という
`instanceof`パターンマッチに修正した。さらにStep 3-4（P8ラウンドトリップ）実行時、
クオートされた識別子（`"t0"."c0"`等、`generate`が常に出力する形式）を含むSQLを`parse`すると
常に`fullyParsed=false`になることが判明した。原因はJSqlParser 5.3の`Table.getName()`/
`Column.getColumnName()`/`Alias.getName()`がいずれもクオート文字を含む生の文字列を返す仕様で
あり、クオートなし文字列を得るには別途`getUnquotedName()`/`getUnquotedColumnName()`/
`getUnquotedTableName()`/`getUnquotedSchemaName()`を呼ぶ必要があったためである
（`SqlParsingService.java`/`AggregateExpressionVisitor.java`の該当箇所を全てUnquoted系
アクセサに置換して修正）。いずれもハンドライトしたクオートなしSQLでの単体テストだけでは
見逃されていた欠陥であり、P7・P8のような構文網羅性の高いプロパティテストの価値を示す事例
となった。

**既知の課題（Step 3スコープ外）**: なし。`./gradlew compileJava`/`compileTestJava`はStep 2・
Step 3のいずれの時点でも成功しており、`./gradlew test`（バックエンド全体）も
Step 3完了時点でBUILD SUCCESSFULを確認済み。