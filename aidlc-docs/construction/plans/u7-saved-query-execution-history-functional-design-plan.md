# U7: Saved Query / Execution / History — Functional Design Plan

## Step 1: ユニットコンテキスト分析

- **ユニット定義**（`unit-of-work.md`）: バックエンドパッケージ `savedquery`, `queryexecution`,
  `queryhistory`。フロントエンド`features/savedQuery/`, `queryExecution/`, `queryHistory/`。
  対応ストーリー GEN-10〜GEN-16。
  責務: SQL（手入力またはクエリビルダー生成）の保存・実行・履歴管理。
  - SQLへの名前付け保存、公開/非公開設定、一覧・詳細取得、作成者限定編集（GEN-10〜12）
  - 手入力SQL・保存クエリの実行。読み取り専用SQLのみ許可する簡易検証、`:param`形式パラメータの
    自動検出・バインド、ページング制御（GEN-13, GEN-14）
  - 実行のたびに履歴記録（`QueryHistoryService.recordExecution`）と監査記録
    （`AuditLogService.record`）を明示的に呼び出す
  - 実行履歴の記録・一覧・絞り込み（GEN-15）、履歴からの画面遷移（GEN-16）
  - 主要コンポーネント: `SavedQueryService`, `QueryExecutionService`, `QueryHistoryService`
  - U1（`common`, `audit`）, U3（`rdbmsconnection`）に依存。**U4（`permission`）には依存しない**
    設計上の意図的例外（後述）。
- **対応ストーリー**（`stories.md`）:
  - GEN-10: クエリの名前付き保存——手入力/クエリビルダー生成SQLに名前を付け、公開/非公開を
    選択して保存
  - GEN-11: 保存クエリの実行——Publicは他ユーザも実行可、Privateは作成者のみ、実行時はSQL編集
    不可
  - GEN-12: 保存クエリの編集——作成者のみ、作成者以外は拒否
  - GEN-13: SQLの直接入力・編集による実行——読み取り専用SQLのみ許可、`:param`自動検出・値入力
    UI、ページング有無の設定
  - GEN-14: クエリ実行結果の表示——表形式・必要に応じページング、SQL/パラメータ/結果件数/
    実行時間/実行日時/実行者を履歴記録、保存クエリの場合は実行回数も記録
  - GEN-15: クエリ実行履歴の一覧・絞り込み——ページング付き一覧、保存/直接入力の区別表示、
    実行日時範囲・実行者（全ユーザ/自分のみ）・SQLテキストで絞り込み
  - GEN-16: クエリ履歴からの画面遷移——各行から「再実行」「保存」「ビルダーで編集」へ遷移、
    元のSQL・パラメータを引き継ぐ
- **既存の確定事項**（Application Design / U1・U3・U6から継承、再検討不要）:
  - `component-methods.md`に以下のメソッドシグネチャが草案として定義済み（Functional Design時点
    での確定要）:
    ```
    SavedQueryService:
      Long saveQuery(Long userId, Long connectionId, String name, String sql, Visibility visibility)
      void updateQuery(Long userId, Long savedQueryId, String name, String sql, Visibility visibility)
        // 作成者以外の場合: PermissionDeniedException（GEN-12）
      List<SavedQuerySummary> listQueries(Long userId, Long connectionId)
        // Public全件 + 自分のPrivateのみ
      SavedQueryDetail getQuery(Long userId, Long savedQueryId)
        // Privateかつ作成者以外の場合: PermissionDeniedException

    QueryExecutionService:
      QueryResult executeAdhocSql(Long userId, Long connectionId, String sql,
                                   Map<String, Object> params, PagingOption paging)
        // 読み取り専用チェックに失敗した場合: ReadOnlyViolationException
      QueryResult executeSavedQuery(Long userId, Long connectionId, Long savedQueryId,
                                     Map<String, Object> params, PagingOption paging)
        // 実行時はSQL編集不可（GEN-11）。SavedQueryService経由でSQL本体を取得する

    QueryHistoryService:
      void recordExecution(ExecutionRecord record)
      PageResult<HistoryEntry> listHistory(Long userId, Long connectionId,
                                            HistoryFilterCriteria criteria, PageRequest page)
    ```
    いずれも引数の型・詳細構造・例外設計はFunctional Designで確定する。
  - `component-dependency.md`の依存マトリクスで`savedquery → common`、
    `queryexecution → common, audit, rdbmsconnection, queryhistory, savedquery`、
    `queryhistory → common`が確定済み。かつ次の注記がある（**未解決のセキュリティ論点として
    明示的にフラグが立っている**、Q4で扱う）:
    > 「`queryexecution`（手入力SQL・保存クエリの実行）は`stories.md` GEN-13 のAC上『読み取り
    > 専用SQLのみ実行可能』という制約のみが明記されており、`masterdata`のようなテーブル/カラム
    > 単位の読み取り権限フィルタは要件上明示されていない。本設計では要件に忠実に`permission`
    > パッケージへの依存を持たせていないが、これは対象RDBMSの任意のテーブル/カラムを権限に
    > 関わらず読み取れることを意味する。セキュリティ上の論点として、`security-baseline`拡張の
    > オプトイン時、または後続のNFR Requirements段階で改めて要否を確認することを推奨する。」
  - `unit-of-work-dependency.md`も同旨を確認済み: U7は技術的にはU1・U3にのみ依存しU4には
    依存しない設計（`queryexecution`の意図的な権限フィルタ除外）。ビルド順序が最終段なのは
    U5/U6が生成するSQLとの**ワークフロー上の関係**（U7が参照するSQLの提供元）によるもので、
    技術的な依存関係ではない。
  - U6（`querybuilder`）からの申し送り事項（`u6-query-builder/functional-design/
    frontend-components.md`）:
    > 「`GeneratedSqlPanel`の`onNavigateToSave`/`onNavigateToExecute` propsはU7のFunctional
    > Design時に具体的な実装（`savedQuery`/`queryExecution` featureへの遷移、`GeneratedSql`の
    > 受け渡し方法）を確定する。また、GEN-9（U7のクエリ実行画面/クエリ履歴画面からの遷移時の
    > SQL自動解析）についても、`QueryBuilderPage`のURLクエリパラメータ（`rawSql`）経由の連携
    > 方式はU6側で用意済みだが、実際に遷移元となるU7側画面の実装はU7のCode Generationで行う。」
    現状のU6コードでは`onNavigateToSave`/`onNavigateToExecute`は未指定（ボタンdisabled）。
  - U6の`GeneratedSql`型（`record GeneratedSql(String sql, Map<String, Object> params)`）が
    U7 `QueryExecutionService`とのインタフェース整合を意図して設計済み
    （`querybuilder/domain-entities.md` Q7）。`sql`は`:paramN`形式のプレースホルダを含む文字列。
  - U5（`masterdata`）からの申し送り事項（`u5-master-data-maintenance/functional-design/
    frontend-components.md`）:
    > 「`RecordListResult`（`columns` + `records`）はJDBCの`ResultSet`/`ResultSetMetaData`の
    > 構造を踏襲しており、U7の任意SQL実行結果も同じ『カラムメタデータ＋結果行』の形で返却される
    > 想定である。…共通化の要否・実装方式はU7のFunctional Designで改めて判断する。」
  - `AuditLog`（U1）の`EventType`に`DATA_ACCESS | QUERY_EXECUTED | U7 QueryExecutionService
    （GEN-14）`が予約済み（`u1-platform-foundation/functional-design/domain-entities.md`）。
    `QueryHistoryService.recordExecution`と`AuditLogService.record`は別々の明示呼び出しである
    ことが`services.md`で確定済み（同一トランザクション外・順不同）。
  - `common/dto`の`PageRequest`/`PageResult<T>`（既存、再利用）。
  - JPA規約（U1〜U6踏襲）: `@ManyToOne`等の関係アノテーションは使用せず、エンティティ間参照は
    素の`Long`型FKフィールドで表現する。全エンティティは`@Id @GeneratedValue(strategy =
    GenerationType.IDENTITY) private Long id;`のサロゲートキー。
  - `NamedParameterJdbcTemplate`（U3既存、`rdbmsconnection`の接続プール経由）を実行に利用する。
  - `DialectStrategy`（U1既存）: `quoteIdentifier`, `buildPagingClause`等、4RDBMS方言差異を
    吸収する既存Strategyを利用する。
  - JSqlParser（U6でbuild.gradle.ktsに追加済み、`querybuilder`パッケージで利用中）は依存関係上
    `queryexecution`から直接参照可能かどうかは未確定（`component-dependency.md`に
    `queryexecution → querybuilder`の依存は定義されていない）——読み取り専用SQL検証方式の
    選択（Q4）に影響する論点。

## Step 2-4: 計画・質問

以下11問について回答をお願いします。各質問には推奨案（A）を用意していますが、
別の選択肢や自由記述でも構いません。

---

### Q1. Domain Model — 内部DBエンティティの一覧

`savedquery`/`queryexecution`/`queryhistory`の3パッケージにまたがるユニットである。内部DB
（H2, JPA）エンティティの範囲を確定する。

- **A（推奨）**: 内部DBエンティティは`SavedQuery`（`savedquery`パッケージ）と`QueryHistory`
  （`queryhistory`パッケージ）の2つのみとする。`queryexecution`はステートレスな実行ロジックの
  みで完結し、専用エンティティは持たない（U5の`MasterDataMutationService`と同様、対象RDBMSへの
  実行そのものは`NamedParameterJdbcTemplate`経由でその場で行い、内部DBには何も保持しない）。
- **B**: `queryexecution`にも実行中セッション等の一時状態を保持するエンティティを追加する。
- **C**: その他（自由記述）

[Answer]: A

---

### Q2. Domain Model — `SavedQuery`の構造・保存対象

GEN-10のACは「手入力またはクエリビルダーで作成したSQLに名前を付けて保存できる」のみで、
`QueryBuilderModel`（U6の8タブ構成モデル）自体を保存するかSQL文字列のみを保存するかは
明記されていない。`component-methods.md`草案は`saveQuery(..., String sql, ...)`とSQL文字列を
引数にしている。

- **A（推奨）**: `SavedQuery`は`sql: String`（生成済み/手入力済みのSQL文字列、`:paramN`形式の
  プレースホルダを含み得る）のみを保存し、`QueryBuilderModel`（U6の構造化モデル）は保存しない。
  GEN-16「ビルダーで編集」への遷移時は、保存済み`sql`を`SqlParsingService.parse`（U6既存API）で
  再度リバースエンジニアリングしてタブモデルへ復元する（U6の`QueryBuilderPage`が既に対応済みの
  `rawSql`クエリパラメータ経由の流れをそのまま再利用する）。パラメータの**デフォルト値**は
  GEN-10のACに言及がないため保存しない（実行のたびに値入力UIで指定する、GEN-13と同じ挙動）。
  `Visibility`は`PUBLIC`/`PRIVATE`の2値enumとする。
- **B**: `SavedQuery`に`QueryBuilderModel`のJSON直列化列も追加し、クエリビルダーへの復元時は
  再解析ではなく直接モデルを復元する（保存データが増えるが再解析コストを避けられる）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q3. Domain Model — `QueryHistory`の構造・実行回数の記録方式

GEN-14のACは「SQL・パラメータ・結果件数・実行時間・実行日時・実行者」に加え「保存クエリの
場合は実行回数も記録される」ことを要求する。実行回数の算出・保持方式を確定する。

- **A（推奨）**: `SavedQuery`に`executionCount: int`（既定0）のカウンタ列を追加し、
  `executeSavedQuery`呼び出しのたびにアトミックにインクリメントする（`UPDATE ... SET
  execution_count = execution_count + 1`相当）。`QueryHistory`レコードには、インクリメント後の
  その時点の値を**スナップショット**として記録する（`QueryHistory.executionCount:
  Integer（nullable）`、手入力SQL実行時はnull）。`QueryHistory`本体の属性は
  `id, userId, connectionId, sql, params（JSON文字列またはMap直列化）, resultCount,
  elapsedMillis, executedAt, savedQueryId（nullable Long）, executionCount（nullable Integer）`
  とする。`AuditLog`（`QUERY_EXECUTED`）とは別エンティティ・別呼び出しのまま独立させ、重複記録
  を許容する（`AuditLog.summaryMessage`は概要のみ、`QueryHistory`が詳細を保持する役割分担、
  `services.md`の設計方針どおり）。
- **B**: 実行回数はカウンタ列を持たず、`QueryHistory`テーブルを`savedQueryId`で都度`COUNT`して
  動的に算出する（カウンタの整合性管理が不要になるが、履歴一覧のたびに集計コストが発生する）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q4. Business Rules / Security — 読み取り専用SQL検証方式と権限フィルタ非依存の扱い

GEN-13のACは「読み取り専用SQLのみ実行できる（更新系SQLは拒否される）」のみを要求し、
`component-dependency.md`には`queryexecution`が`permission`（U4）に依存しない設計が明記され、
「セキュリティ上の論点として…改めて要否を確認することを推奨する」という未解決の申し送りが
残っている。

- **A（推奨、確定済み設計を踏襲）**: 読み取り専用検証は、SQL文の先頭キーワードが`SELECT`
  （空白・コメントを除去した上で大文字小文字を無視して判定）であることのみを確認する軽量な
  キーワード検証とする。JSqlParser（U6で導入済み）を`queryexecution`からも利用し、パース結果の
  文の種類（`net.sf.jsqlparser.statement.select.Select`かどうか）で判定することで、コメント内
  キーワードの誤検知やセミコロン区切りの複数文（例: `SELECT ...; DROP TABLE ...`）を確実に
  拒否する（正規表現ベースの単純なキーワード検査より安全）。この際`component-dependency.md`に
  `queryexecution → querybuilder`の依存を追記する（JSqlParserライブラリ自体への直接依存でも
  良いが、U6で確立済みの解析ロジックとの重複を避けるため`querybuilder`の`SqlParsingService`が
  提供する解析結果を再利用する方が一貫性がある）。
  U4（テーブル/カラム単位の読み取り権限フィルタ）には**引き続き依存させない**——
  `component-dependency.md`の申し送りどおり要件上明示されていないためMVPスコープ外とし、
  この設計判断（対象RDBMSの任意のテーブル/カラムを権限に関わらず読み取れる）を
  `business-rules.md`に明記した上で、`security-baseline`拡張のオプトイン時に再検討すべき
  項目として引き続きフラグを残す。
- **B**: 正規表現ベースの単純なキーワード検査のみとする（`SELECT`で始まり、`INSERT`/`UPDATE`/
  `DELETE`/`DROP`/`ALTER`/`TRUNCATE`等のキーワードを含まないことを確認する）。JSqlParser
  依存を追加しない分シンプルだが、コメント回避や複数文注入のようなエッジケースに弱い。
- **C**: この機会にU4（`permission`）への依存を追加し、テーブル/カラム単位の読み取り権限
  フィルタも適用する（要件のACを超える追加スコープ、実装コストと保護範囲拡大のトレードオフ）。
- **D**: その他（自由記述）

[Answer]: A

---

### Q5. Business Rules — 保存クエリの可視性・実行・編集・削除権限

GEN-10〜12のACを整理すると: 保存時にPublic/Privateを選択（GEN-10）、Publicは全ユーザ実行可・
Privateは作成者のみ実行可（GEN-11）、編集は作成者のみ（GEN-12）。削除についてはいずれのACにも
言及がない。

- **A（推奨）**: `listQueries`はPublic全件＋自分が作成したPrivateのみを返す（他ユーザの
  Privateは一覧にも現れない）。`getQuery`はPublicなら誰でも取得可、Privateは作成者のみ
  （作成者以外は`PermissionDeniedException`）。`updateQuery`（名前・SQL・可視性の変更）は
  作成者のみ（GEN-12のAC通り）。**削除機能はMVPスコープ外**とする（ACに明記がないため、
  `business-rules.md`に「削除は本ユニットのスコープ外、将来的な拡張候補」と明記する）。
  実行（GEN-11）は`getQuery`と同じ可視性チェックを`executeSavedQuery`内でも行う
  （`SavedQueryService`経由でSQL取得時に検証、`QueryExecutionService`が直接可視性を判定しない
  ——単一責任の原則、`SavedQueryService.getQuery`を内部的に再利用）。
- **B**: 削除機能もこの時点でスコープに含める（作成者のみ削除可、実行履歴からの参照
  （`QueryHistory.savedQueryId`）が残る場合の扱い——論理削除か物理削除かも合わせて確定が必要）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q6. Business Rules — パラメータ自動検出・バインド方式

GEN-13のACは「`:param`形式のパラメータが自動検出され、値入力UIが表示される」ことを要求する。
検出・バインドの実装方式を確定する。

- **A（推奨）**: SQL文字列に対し正規表現（例: `:([a-zA-Z_][a-zA-Z0-9_]*)`、ただし文字列リテラル
  内の`:xxx`やPostgreSQLの`::type`キャストとの誤検知を避けるため、簡易的な文字列リテラル
  スキップを行う）でパラメータ名を抽出し、フロントエンドへ返す。値は`NamedParameterJdbcTemplate`
  にそのまま渡せる`Map<String, Object>`として受け取り、**型変換は行わずすべて文字列として
  受け取り、JDBCドライバのデフォルトの型変換に委ねる**（`java.time`型等への明示的な型指定は
  MVPスコープ外とし、`business-rules.md`に明記する）。パラメータが1つも検出されない場合は
  空の`Map`をそのまま渡し、通常のSQL実行として扱う。
- **B**: パラメータの型（文字列/数値/日付等）をUIまたはAPI側で明示的に指定させ、
  `java.sql.Types`相当の型情報を持つ`Map<String, TypedValue>`のような構造でバインドする
  （型安全性は高いが実装・UI複雑度が増す）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q7. Business Rules — ページング制御方式

GEN-13のACは「ページングの有無を設定できる」ことを要求する。任意のSELECT文に対しページングを
適用する実装方式を確定する。

- **A（推奨）**: ユーザがページングを「あり」に設定した場合、入力SQLをサブクエリとしてラップし
  `SELECT * FROM (<入力SQL>) AS subquery`の外側に`DialectStrategy.buildPagingClause`
  （U1既存、4RDBMS方言吸収済み）でLIMIT/OFFSET相当句を付与する。この方式であれば入力SQLが既に
  `ORDER BY`やLIMIT相当句を含んでいてもサブクエリ化により安全に外側から制御でき、入力SQL自体を
  構文解析してLIMIT句の有無を判定する必要がない（Q4のJSqlParser利用は文種別判定のみに限定し、
  ページング注入には使わない）。ページング「なし」の場合はそのまま実行する（全件取得、大量結果
  リスクはGEN-14の表示側でのページング表示・`business-rules.md`での注記に委ねる）。
- **B**: 入力SQLをJSqlParserでパースし、SQL自体にLIMIT句を直接追加/上書きする
  （サブクエリラップより生成SQLがシンプルだが、パース失敗時のフォールバックが必要になる）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q8. Business Rules — 実行履歴の絞り込み仕様・結果表現

GEN-15のACは「実行日時（範囲）、実行者（全ユーザ／自分のみ）、SQLテキストで絞り込める」ことを
要求する。「全ユーザ」を選択した場合に他ユーザの履歴（SQL文面含む）を閲覧できる範囲、および
GEN-14の実行結果表現の方式を確定する。

- **A（推奨）**: `executorScope=ALL`は管理者ロール制約を設けず、認証済みユーザなら誰でも選択
  可能とする（U7は`permission`に依存しないQ4の設計方針と一貫させ、`queryhistory`も追加の権限
  制約を持たない。実行対象RDBMSデータへのアクセス制御自体がU4非依存であるため、実行履歴の
  閲覧のみを別途制限する理由がない）。`sqlTextSearch`はSQL文字列への部分一致（大文字小文字を
  区別しない`LIKE '%...%'`相当）とする。GEN-14の実行結果（`QueryResult`）は、U5の
  `RecordListResult`（`columns` + `records`、JDBCの`ResultSet`/`ResultSetMetaData`構造を踏襲）
  と同一の形（カラムメタデータ＋結果行の配列）で統一する——`masterdata`と`queryexecution`の
  両方が同じ形の結果を返すことで、フロントエンド側の結果テーブル描画ロジック
  （`DataTable`のアダプタ部分）をU5・U7間で共通化できる余地を残す（U5の申し送り事項どおり）。
  ただし`masterdata`パッケージへの直接のバックエンド依存は追加しない（型定義のみ
  `queryexecution`側に独立して複製する、フロントエンドの`他feature非依存の方針`と同じ扱い）。
- **B**: `executorScope=ALL`は管理者ロールのみ選択可能とする（一般ユーザは自分の履歴のみ閲覧
  可、プライバシー配慮を優先）。この場合`queryhistory`に何らかの管理者判定が必要になり、
  ロール判定自体は`auth`（U2、`Authentication`のロール情報）から行う（`permission`への依存は
  追加しない）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q9. Integration — U6↔U7連携の具体実装（`onNavigateToSave`/`onNavigateToExecute`、GEN-16）

U6が用意した`GeneratedSqlPanel`の`onNavigateToSave`/`onNavigateToExecute` props、および
`QueryBuilderPage`の`rawSql`/`connectionId`クエリパラメータ受け入れを、U7側でどう実装するかを
確定する。GEN-16は履歴の1行から「再実行」「保存」「ビルダーで編集」への遷移を要求する。

- **A（推奨）**: 画面間のSQL・パラメータの引き継ぎはReact Routerの`useNavigate`による画面遷移＋
  URLクエリパラメータ（`connectionId`, `rawSql`）を統一的な方式とする（U6が既に`rawSql`/
  `connectionId`を受け付ける実装を持つため、この方式に揃えるのが最小変更）。パラメータ値
  （`Map<String, Object>`）はURLクエリパラメータでは表現が煩雑になるため引き継がず、遷移先で
  Q6のパラメータ自動検出により再度値入力を求める（GEN-16のACは「元のSQL・パラメータが
  引き継がれる」だが、パラメータ**値**ではなく「どのパラメータが必要か」という構造の引き継ぎ
  （＝SQL文字列さえ引き継げば`:param`検出で自動的に再現される）と解釈する）。具体的な遷移先:
  「再実行」→`queryExecution`の実行画面（`rawSql`をプリセット）、「保存」→`savedQuery`の
  保存フォーム（`rawSql`をプリセット）、「ビルダーで編集」→`querybuilder`の`/query-builder`
  （既存の`rawSql`/`connectionId`クエリパラメータをそのまま利用）。U6側の`onNavigateToSave`/
  `onNavigateToExecute`実装は、`GeneratedSql.sql`を`rawSql`として同様のクエリパラメータ経由で
  `savedQuery`/`queryExecution`へ`navigate`する関数とする（`GeneratedSql.params`は
  Q6と同じ理由で引き継がない）。
- **B**: パラメータ値も含めて引き継ぐため、URLクエリパラメータではなくReact Routerの
  `navigate(path, { state })`（履歴スタックに残らない一時的な状態渡し）を使う方式とする
  （ブラウザの戻る/リロードでは状態が失われる制約があるが、値を含めた完全な引き継ぎが可能）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q10. Frontend Components — `savedQuery/`・`queryExecution/`・`queryHistory/`の画面構成

U1〜U6の`frontend-components.md`と同様の粒度で、本ユニットの3フロントエンド機能を設計する。

- **A（推奨）**: 以下を`features/{savedQuery,queryExecution,queryHistory}/`配下に設計する
  （一般ユーザ向け、ロール制約なし）。
  - `savedQuery/`: `SavedQueryListPage`（一覧、Public+自分のPrivate、`/saved-queries`）、
    `SavedQuerySaveForm`（保存フォーム、名前・可視性入力＋`rawSql`クエリパラメータでのSQL
    プリセット、GEN-10）、`SavedQueryDetailPage`（詳細表示・作成者のみ編集ボタン活性、GEN-12）
  - `queryExecution/`: `QueryExecutionPage`（SQL入力欄（`rawSql`プリセット対応）・パラメータ
    値入力UI・ページング切替・実行ボタン・結果テーブル、GEN-13/14。保存クエリ実行時
    （`savedQueryId`クエリパラメータ指定時）はSQL入力欄を読み取り専用にする、GEN-11）
  - `queryHistory/`: `QueryHistoryListPage`（一覧・絞り込みフォーム（日時範囲・実行者スコープ・
    SQLテキスト検索）、各行に「再実行」「保存」「ビルダーで編集」ボタン、GEN-15/16）
  - `AppRouter.tsx`に`/saved-queries`, `/saved-queries/new`, `/saved-queries/{id}`,
    `/query-execution`, `/query-history`ルートを追加する。
  - U6 `QueryBuilderPage`の`GeneratedSqlPanel`に`onNavigateToSave`/`onNavigateToExecute`
    ハンドラを接続する（Q9のとおり）。
- **B**: 別の粒度・構成を希望する（自由記述）。

[Answer]: A

---

### Q11. Business Rules — 実行結果の大量データ・タイムアウトに対する扱い

任意SQL実行（GEN-13）は`masterdata`のような既定ページサイズやレコード件数上限の枠組みが
存在しない。U5にはクエリタイムアウト（`mm.app.master-data.query-timeout`）や
大量レコード監査閾値（`large-record-threshold`）の前例がある。

- **A（推奨）**: U5と同様の設定キー体系を`queryexecution`にも導入する
  （`mm.app.query-execution.query-timeout`（既定30秒）、
  `mm.app.query-execution.large-record-threshold`（既定100件、超過時
  `AuditLog`の`LARGE_RECORD_READ`相当イベントを記録するか、`QUERY_EXECUTED`のみで足りるかは
  `business-rules.md`で確定）、ページング「なし」時の最大取得件数上限
  `mm.app.query-execution.max-result-rows`（既定1000件、超過分は切り捨てて`QueryResult`に
  「上限到達」フラグを含める））。具体的な既定値・キー名はNFR Requirements/NFR Designで
  再確認するが、この時点で「タイムアウト・大量データ対策が必要」という設計判断自体は確定して
  おく。
- **B**: MVPスコープでは特別な上限・タイムアウト対策を設けず、対象RDBMS・JDBCドライバの
  デフォルト挙動に委ねる（後続のNFR段階で必要に応じて追加する）。
- **C**: その他（自由記述）

[Answer]: A

---

## Step 6: 成果物生成チェックリスト

- [ ] `aidlc-docs/construction/u7-saved-query-execution-history/functional-design/domain-entities.md`
- [ ] `aidlc-docs/construction/u7-saved-query-execution-history/functional-design/business-rules.md`
- [ ] `aidlc-docs/construction/u7-saved-query-execution-history/functional-design/business-logic-model.md`
      （PBT-01: テスト可能な性質セクションを含む、`property-based-testing`拡張が有効なため）
- [ ] `aidlc-docs/construction/u7-saved-query-execution-history/functional-design/frontend-components.md`