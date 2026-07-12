# U6: Query Builder — Functional Design Plan

## Step 1: ユニットコンテキスト分析

- **ユニット定義**（`unit-of-work.md`）: バックエンドパッケージ `querybuilder`。フロントエンド
  `features/queryBuilder/`。対応ストーリー GEN-6, GEN-7, GEN-8, GEN-9。
  責務: GUIによるSQL組み立てと、既存SQLのクエリビルダーへの逆変換。
  - スキーマ選択、FROM/JOINタブでのテーブル・エイリアス選択、他タブでのアクセス可能カラム一覧
    提供（権限フィルタ適用）
  - SELECT/FROM/JOIN/WHERE/GROUP BY/HAVING/ORDER BY/LIMIT OFFSET指定からのSQL生成
  - 手入力SQLの解析とクエリビルダー各タブモデルへの逆変換（GEN-9、解析不能な複雑SQLの検知）
  - 主要コンポーネント: `QueryBuilderMetadataService`, `SqlGenerationService`（実装方式は
    Functional Design/NFR Designで決定, Application Design Question 7 = C）,
    `SqlParsingService`（同上）
  - U1（`common`）, U3（`rdbmsconnection`, `schema`）, U4（`permission`）に依存。
- **対応ストーリー**（`stories.md`）:
  - GEN-6: FROM/JOINタブでのテーブル・エイリアス指定——アクセス可能テーブルのみ選択可、
    選択結果が他タブのカラム選択肢に反映される
  - GEN-7: WHERE/GROUP BY/HAVING/ORDER BY/LIMIT OFFSET指定——各タブで選択済みテーブル/
    エイリアスに対しアクセス可能なカラムのみ表示、SELECT/HAVING/ORDER BYタブで集計関数使用可、
    LIMIT OFFSETタブで件数・オフセット指定
  - GEN-8: クエリビルダーからのSQL生成——UI指定内容から妥当なSQLを生成し、保存/実行に連携
  - GEN-9: 手入力SQLからクエリビルダーへの反映（リバースエンジニアリング）——クエリ実行画面/
    クエリ履歴からの遷移時にSQLを解析し各タブに反映、解析不能な複雑SQLはその旨を表示
- **既存の確定事項**（Application Design / U1・U3・U4から継承、再検討不要）:
  - `component-methods.md`に以下のメソッドシグネチャが定義済み:
    ```
    QueryBuilderMetadataService:
      List<String> listSelectableSchemas(Long userId, Long connectionId)
      List<TableRef> listSelectableTables(Long userId, Long connectionId, String schema)
      List<ColumnRef> listSelectableColumns(Long userId, Long connectionId, String schema, String table)
        // TableRef/ColumnRef の詳細構造は未定義——Functional Designで確定する

    SqlGenerationService（抽象境界、実装方式はFunctional/NFR Designで決定）:
      GeneratedSql generate(QueryBuilderModel model)
        // QueryBuilderModel = SELECT/FROM/JOIN/WHERE/GROUP BY/HAVING/ORDER BY/LIMIT OFFSET の
        // タブ構成を表す入力モデル——詳細構造は未定義

    SqlParsingService（抽象境界、実装方式はFunctional/NFR Designで決定）:
      ParseResult parse(String rawSql)
        // ParseResult = { boolean fullyParsed, Optional<QueryBuilderModel> model, Optional<String> notice }
        // 解析不能な複雑SQLの場合は fullyParsed=false, notice にその旨を設定（GEN-9）
    ```
  - `components.md`の責務記述: `QueryBuilderMetadataService`はスキーマ選択・FROM/JOINタブでの
    テーブル選択・他タブでのアクセス可能カラム一覧提供を担い、`EffectivePermissionResolver`
    経由の読み取り権限フィルタを適用する。U5（`masterdata`）の手入力WHERE/ORDER BY（GEN-2）に
    存在する「権限フィルタ対象外」という設計上の例外は、`querybuilder`のUI組立系タブには
    **適用されない**（GEN-6/GEN-7のACはいずれも「アクセス可能なテーブル/カラムのみ」と明記）。
  - `component-dependency.md`の依存マトリクスで`querybuilder → common, permission, schema,
    rdbmsconnection`が確定済み（`masterdata`/`queryexecution`への依存はなし。生成したSQLの
    「保存・実行への連携」はフロントエンドの画面遷移／将来的なU7側APIが担い、`querybuilder`
    パッケージ自体はSQL文字列を返すところまでが責務）。
  - `EffectivePermissionResolver`（U4, `permission`）: `resolveEffectiveColumnPermissions`
    等、U5と同じAPI群を利用する。`querybuilder`は本サービスのAPIのみを呼び出し、`permission`の
    内部エンティティには直接アクセスしない。
  - `SchemaQueryService`（U3, `schema`）: `listSchemas`/`listTables`/`getTableDetail`で
    取り込み済みメタデータ（カラム名・データ型・`nullable`・主キー構成等）を参照する。権限
    フィルタを持たない生の一覧であり、`querybuilder`側で`EffectivePermissionResolver`の
    判定結果によりフィルタする（U5と同じパターン）。
  - `ConnectionPoolRegistry`（U3, `rdbmsconnection`）: SQL生成・解析自体はDB接続不要だが、
    `querybuilder`はスキーマ選択のために接続の存在確認等で参照する可能性がある。
  - `common/dialect/DialectStrategy`（U1）: `quoteIdentifier`, `buildPagingClause`,
    `buildNullsOrderingClause`等、対象RDBMS（MySQL/MariaDB/PostgreSQL/H2）の方言差異を吸収する
    既存Strategyを利用する（新規実装不要）。JOIN構文の方言差異（後述Q4）についても本Strategyの
    拡張要否を検討する。
  - U7（`savedquery`/`queryexecution`/`queryhistory`）は未着手。`unit-of-work.md`の申し送り
    どおり、U7が保存・参照するSQL（クエリビルダー生成SQL等）はU5/U6の成果物という関係にあるが、
    U6時点ではU7側の実装は存在しない（Q9で範囲を確定する）。

## Step 2-4: 計画・質問

以下10問について回答をお願いします。各質問には推奨案（A）を用意していますが、
別の選択肢や自由記述でも構いません。

---

### Q1. Domain Model — 内部DBエンティティの要否

`querybuilder`パッケージはSQLの組み立て・解析というステートレスなロジックが中心。内部DB(H2, JPA)
に本ユニット固有の新規エンティティが必要か。

- **A（推奨）**: 内部DBエンティティは追加しない。U5（`masterdata`）と同じ理由——`querybuilder`が
  保持する状態は一切なく、すべて対象RDBMSメタデータ参照（`SchemaQueryService`）・権限判定
  （`EffectivePermissionResolver`）への委譲と、リクエストごとの純粋なSQL生成/解析処理のみで
  完結する。`QueryBuilderModel`/`GeneratedSql`/`ParseResult`等はすべてJPA非対応の純粋なDTO
  （service/dto層のPOJO・record）とする。
- **B**: 何らかの内部DB状態（例: クエリビルダーの作業中モデルの一時保存等）を持たせる。
- **C**: その他（自由記述）

[Answer]: A

---

### Q2. Business Logic Modeling — `QueryBuilderModel`の全体データ構造

SELECT/FROM/JOIN/WHERE/GROUP BY/HAVING/ORDER BY/LIMIT OFFSETの8タブを表現するモデルを設計する
必要がある。

- **A（推奨）**: 各タブに対応するレコードを持つ集約モデルとする。
  ```
  record QueryBuilderModel(List<SelectItem> selectItems, FromItem fromItem,
                            List<JoinItem> joinItems, List<Condition> whereConditions,
                            List<String> groupByColumns, List<Condition> havingConditions,
                            List<OrderByItem> orderByItems, Integer limit, Integer offset)

  record FromItem(String schema, String table, String alias)
    // FROM タブは常に1件（ベーステーブル）のため単数の FromItem とする（List にしない）。
    // JOIN タブは複数件のため joinItems は List<JoinItem> のまま。

  record JoinItem(JoinType type, String schema, String table, String alias, Condition onCondition)
    // JoinType は Q4 で確定

  record SelectItem(String tableAlias, String columnName, AggregateFunction aggregateFunction,
                     String outputAlias)
    // aggregateFunction は Q5 で確定（NONE可）

  record Condition(...)
    // WHERE/HAVING で共有する条件モデル、詳細は Q3 で確定

  record OrderByItem(String tableAlias, String columnName, AggregateFunction aggregateFunction,
                      SortDirection direction)
  ```
  U5の`ColumnRef`/`TableRef`等の参照型は`querybuilder`固有に定義し（`masterdata`の型とは
  独立、パッケージ間の直接依存は持たせない——`component-dependency.md`のとおり`querybuilder`
  は`masterdata`に依存しない）、`TableRef(String schema, String table, String comment)`,
  `ColumnRef(String columnName, String dataType, boolean nullable)`とする。
- **B**: SELECT/FROM/JOIN等を区別せず、単一の汎用AST（抽象構文木）ノードで表現する
  （より柔軟だが実装・UI連携の複雑度が上がる）。
- **C**: その他（自由記述）

[Answer]: A（`fromItems`→`fromItem`（単数）に修正済み）

---

### Q3. Business Rules — WHERE/HAVING条件のグルーピング範囲（AND/OR・括弧によるネスト）

U5の`FilterCriteria`（`UiCondition`）はAND結合のみに限定した（GEN-1のACがそこまでしか要求して
いないため）。GEN-7のACも「各タブで条件を指定できる」以上の明示要求はないが、「クエリビルダー」
という機能の性質上、任意のSQLを組み立てられる汎用性が期待される可能性がある。

- **A（推奨、MVPスコープをU5と揃える）**: WHERE/HAVINGともにAND結合のみとし、OR・括弧による
  グルーピングはMVPスコープ外とする（GEN-7のACに忠実、`Condition(String tableAlias,
  String columnName, AggregateFunction aggregateFunction, Operator operator, Object value)`の
  フラットなリストで表現）。将来的な拡張余地として`business-rules.md`に明記する。
- **B**: AND/ORの混在・括弧によるネストまで対応する条件木（`Condition`が`AND`/`OR`/`LEAF`の
  いずれかを表す再帰構造）をこの時点で導入する。SQL生成・解析（GEN-9）双方の実装が複雑化する。
- **C**: その他（自由記述）

[Answer]: A

---

### Q4. Business Rules — JOIN種別と対象RDBMS方言差異の扱い

GEN-6のACは「テーブルとエイリアスを指定できる」のみで、JOIN種別（INNER/LEFT/RIGHT/FULL）の
範囲は明記されていない。MySQL/MariaDBはFULL OUTER JOINを構文としてサポートしない
（`UNION`によるエミュレートが必要）という方言差異がある。

- **A（推奨）**: `JoinType`は`INNER`/`LEFT`/`RIGHT`の3種類のみサポートする（4RDBMS全てが
  ネイティブ構文でサポートする共通部分）。`FULL OUTER JOIN`はMVPスコープ外とし、
  `business-rules.md`に理由（MySQL/MariaDB非対応、`UNION`エミュレーションは生成SQLの
  複雑度・可読性を大きく損なうため見送り）を明記する。
- **B**: `FULL`も含めた4種類をサポートし、MySQL/MariaDBの場合のみ`LEFT JOIN ... UNION
  RIGHT JOIN ...`相当のSQLを自動生成する。
- **C**: その他（自由記述）

[Answer]: A

---

### Q5. Business Rules — 集計関数の範囲・DISTINCT対応

要件（`REQUIREMENTS.md` 5.5）は「SELECT / HAVING / ORDER BY タブで集計関数を使用可能」とのみ
記載。具体的な関数セットと`DISTINCT`対応要否を確定する。

- **A（推奨）**: `AggregateFunction`は`NONE, COUNT, SUM, AVG, MIN, MAX`の6値（`NONE`は非集計
  カラム選択）とする。4RDBMS共通でサポートされる基本的な集計関数のみとし、`DISTINCT`修飾
  （例: `COUNT(DISTINCT col)`）はMVPスコープ外とする（要件に明記なし、UIの複雑化を避ける）。
  GROUP BYタブで指定した`groupByColumns`に含まれないカラムをSELECT/ORDER BYで非集計指定する
  ケースはSQL生成時にエラーとする（標準SQLのGROUP BY制約に合わせる）。
- **B**: `DISTINCT`修飾や`COUNT(*)`等の特殊形も含めて対応する。
- **C**: その他（自由記述）

[Answer]: A

---

### Q6. Business Rules / Security — カラム選択の権限フィルタ適用方針

U5のGEN-2（手入力WHERE/ORDER BY）はカラム権限フィルタの対象外という設計上の例外があったが、
`querybuilder`はUIによる組み立てが前提（GEN-6/GEN-7のACに忠実）。

- **A（推奨）**: `querybuilder`のUI組立系（SELECT/FROM/JOIN/WHERE/GROUP BY/HAVING/ORDER BY
  全タブ）は例外なく`EffectivePermissionResolver.resolveEffectiveColumnPermissions`による
  読み取り権限（R以上）フィルタを適用する（U5のGEN-2のような手入力の例外は`querybuilder`には
  存在しない——U6は常にUI経由の組み立てであり、生SQL文字列を直接実行する経路を持たない）。
  `listSelectableColumns`がR以上のカラムのみを返すことで、UIの選択肢自体に権限外カラムが
  現れない構造とする（U5 Q2と同じ「そもそも見せない」方針）。
  GEN-9の解析結果（`ParseResult.model`）についても、解析されたSQLが権限外カラム/テーブルを
  参照していた場合は`fullyParsed=false`とし、`notice`に「アクセス権限のないカラム/テーブルを
  含むため反映できません」等のメッセージを設定する（解析はできてもモデルに反映しない、という
  区別ではなく、権限外参照は構文解析成否とは独立に「反映不可」として扱う）。
- **B**: 権限外カラムを含むSQLも解析結果としては`model`に含め、UI側で警告表示のみ行う（値の
  取得はできないが、クエリビルダーの構造としては見える）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q7. Integration — SQL生成方式の実装アプローチ

Application Design Question 7で「実装方式はFunctional Design/NFR Designで決定」と保留された
論点。`SqlGenerationService.generate(QueryBuilderModel)`の実装方式を確定する。

- **A（推奨）**: 外部SQL構築ライブラリ（jOOQ等）は導入せず、`QueryBuilderModel`から
  `StringBuilder`ベースで直接SQL文字列を組み立てる自前実装とする。識別子のクオート
  （`DialectStrategy.quoteIdentifier`）とLIMIT OFFSET句（`DialectStrategy.buildPagingClause`）
  は既存Strategyを再利用し、JOIN句・WHERE句・GROUP BY/HAVING句・ORDER BY句の組み立てロジックは
  `querybuilder`側に新規実装する。パラメータ値はSQLインジェクション対策として文字列連結せず、
  生成SQLは`:paramN`形式のプレースホルダとし、値は別途`Map<String, Object>`として返す
  （`NamedParameterJdbcTemplate`が実行時に利用する形式——U7 `QueryExecutionService`との
  インタフェース整合、`REQUIREMENTS.md` 5.7の":param"形式パラメータ対応に合わせる）。
  `GeneratedSql`は`record GeneratedSql(String sql, Map<String, Object> params)`とする。
- **B**: 外部SQL構築ライブラリ（jOOQ等）を導入し、そのAPI経由でSQLを組み立てる（4RDBMS方言
  対応がライブラリ側に委譲される利点があるが、新規依存追加・学習コストが発生する）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q8. Integration — SQL解析（リバースエンジニアリング、GEN-9）の実装方針とスコープ

Application Design Question 7で同じく保留された論点。`SqlParsingService.parse(String rawSql)`
の実装方式と、「解析可能」と見なすSQL構文の範囲を確定する。

- **A（推奨）**: 外部SQLパーサライブラリ（JSqlParser等、ANSI SQL準拠のオープンソースパーサ）を
  導入し、パース結果のASTを`QueryBuilderModel`へマッピングする自前の変換ロジックを実装する。
  自前の文字列解析（正規表現等）でSQL構文を解析するのは対象4RDBMSの方言差異・エッジケースの
  網羅が現実的でないため避ける。
  「解析可能」の範囲は`QueryBuilderModel`が表現できる構文（単純な`SELECT ... FROM ... [INNER/
  LEFT/RIGHT JOIN ...] [WHERE ...] [GROUP BY ...] [HAVING ...] [ORDER BY ...] [LIMIT/OFFSET]`、
  Q3で確定したAND結合のみのWHERE/HAVING、Q4で確定したJOIN種別、Q5で確定した集計関数）に限定
  する。サブクエリ・UNION・CTE（WITH句）・ウィンドウ関数・OR条件・括弧によるグルーピング等、
  `QueryBuilderModel`で表現できない構文を検出した場合は`fullyParsed=false`、
  `notice`に非対応構文の種類を可能な範囲で含めたメッセージを設定する（GEN-9のAC「解析できない
  複雑なSQLの場合は、その旨を表示する」に対応）。
- **B**: 外部ライブラリを導入せず、限定的な自前パーサ（正規表現ベース等）を実装する。対応範囲は
  Aと同様に限定するが、パーサ自体の保守性・エッジケース対応力はライブラリに劣る。
- **C**: その他（自由記述）

[Answer]: A

---

### Q9. Integration — GEN-8「保存または実行への連携」の実装範囲（U7未着手）

GEN-8のAC「生成したSQLをそのまま保存または実行に連携できる」の連携先（`savedquery`/
`queryexecution`、U7）は未着手。承認済みビルド順序（U1→U2→U3→U4→{U5,U6}→U7）どおり、U6は
U7より先に着手している。

- **A（推奨）**: U6の責務範囲は「生成したSQLとパラメータ（`GeneratedSql`）をフロントエンド画面上に
  表示し、コピー可能にする」までとする。「保存」「実行」ボタンは画面上に用意するが、U6時点では
  遷移先ページ（U7の`savedquery`/`queryexecution`画面）が存在しないため、ボタン押下時の実際の
  画面遷移・API連携はU7のFunctional Design/Code Generationで実装する（U6ではボタンの配置と
  クリックハンドラのインタフェース——例えば`onNavigateToSave(generatedSql)`のようなprops——を
  定義しておき、U7側で実装を差し込む）。バックエンド`querybuilder`パッケージ自体は`savedquery`/
  `queryexecution`への依存を持たない（`component-dependency.md`のとおり）。
- **B**: U6の時点でU7の最小限のプレースホルダページ（未実装であることを示すダミー画面）まで
  作成する。
- **C**: その他（自由記述）

[Answer]: A

---

### Q10. Frontend Components — `queryBuilder/`機能のコンポーネント構成

U1-U5の`frontend-components.md`と同様の粒度で、本ユニットのフロントエンド機能を設計する。

- **A（推奨）**: 以下の画面・コンポーネントを`features/queryBuilder/`配下に設計する（一般ユーザ
  向け、`/query-builder`配下）。
  - `QueryBuilderPage`（接続・スキーマ選択、タブ切り替えコンテナ、生成SQL表示エリア）
  - `FromJoinTab`（FROM/JOINタブ、テーブル・エイリアス・JOIN種別の指定、GEN-6）
  - `SelectTab`（SELECTタブ、カラム・集計関数・出力エイリアスの指定）
  - `WhereHavingTab`（WHERE/HAVING共通、条件モデル（Q3）の組み立てUI。共通コンポーネント化し
    WHERE/HAVINGで再利用する）
  - `GroupByOrderByTab`（GROUP BYタブ・ORDER BYタブ、カラム・集計関数・並び順の指定）
  - `LimitOffsetTab`（LIMIT OFFSETタブ、件数・オフセットの数値入力）
  - `GeneratedSqlPanel`（生成SQL・パラメータの表示、コピーボタン、Q9の「保存」「実行」ボタン
    プレースホルダ）
  - `SqlReverseParsePanel`（手入力SQLの貼り付け→解析→タブ反映のUI。GEN-9は本来U7画面からの
    遷移時の自動反映が主だが、U6単体でも動作確認・利用ができるよう手動貼り付け入力も用意する。
    解析失敗時は`ParseResult.notice`を表示）
  - `AppRouter.tsx`に`/query-builder`ルートを追加する（クエリパラメータでの`rawSql`受け渡しにも
    対応し、U7からの遷移（GEN-9）時に同じ画面・ロジックを再利用できるようにする）。
  - 権限に応じたUI制御: `listSelectableTables`/`listSelectableColumns`が返す一覧のみを選択肢に
    表示する（読み取り権限のないテーブル/カラムはそもそも選択肢に現れない、Q6と同じ「見せない」
    方針）。
- **B**: 別の粒度・構成を希望する（自由記述）。

[Answer]: A

---

## Step 6: 成果物生成チェックリスト

- [x] `aidlc-docs/construction/u6-query-builder/functional-design/domain-entities.md`
- [x] `aidlc-docs/construction/u6-query-builder/functional-design/business-rules.md`
- [x] `aidlc-docs/construction/u6-query-builder/functional-design/business-logic-model.md`
      （PBT-01: テスト可能な性質セクションを含む、`property-based-testing`拡張が有効なため）
- [x] `aidlc-docs/construction/u6-query-builder/functional-design/frontend-components.md`