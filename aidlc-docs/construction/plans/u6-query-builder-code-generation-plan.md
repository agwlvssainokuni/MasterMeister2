# u6-query-builder-code-generation-plan.md

U6（Query Builder）の Code Generation 計画。本ドキュメントが Code Generation の単一の真実源
（single source of truth）であり、Part 2（Generation）はこの計画のステップを順に実行する。
ワークスペースルート: `~/Documents/project/git/MasterMeister2`（`aidlc-state.md` Workspace
Root）。アプリケーションコードはワークスペースルート配下（`backend/`, `frontend/`）にのみ生成し、
`aidlc-docs/` にはドキュメント成果物のみ生成する。

---

## ユニットコンテキスト（code-generation.md Step 3）

### 対応ストーリー

GEN-6〜GEN-9（`unit-of-work-story-map.md`）:

| ID | タイトル |
|---|---|
| GEN-6 | クエリビルダーでのSELECT/FROM/JOIN指定 |
| GEN-7 | クエリビルダーでのWHERE/GROUP BY/HAVING/ORDER BY/LIMIT OFFSET指定 |
| GEN-8 | クエリビルダーからのSQL生成 |
| GEN-9 | 手入力SQLからクエリビルダーへの反映（リバースエンジニアリング） |

### 他ユニットへの依存

U1・U3・U4に依存（`component-dependency.md`: `querybuilder → common, permission, schema,
rdbmsconnection`）:
- `common`（`common.dialect.DialectStrategy`/`DialectStrategyFactory`/`SortDirection`/
  `NullsOrder`/`SchemaResolutionMode`、`common.exception`配下の`EntityNotFoundException`/
  `ValidationException`/`PermissionDeniedException`。いずれも既存、新規例外なし——
  `domain-entities.md`確定）
- `rdbmsconnection`（U3）: `RdbmsConnectionRepository.findById`（`RdbmsConnection.getRdbmsType()`
  取得用、`DialectStrategyFactory.resolve`と組み合わせて`masterdata`と同じパターンで使用）
- `schema`（U3）: `SchemaQueryService.getTableDetail`（カラムメタデータ取得、
  `listSelectableColumns`が内部で利用）
- `permission`（U4）: `EffectivePermissionResolver.listAccessibleSchemas`/
  `listAccessibleTables`/`resolveEffectiveTablePermission`/`resolveEffectiveColumnPermissions`

### 提供インタフェース・契約（他ユニットが依存する公開API）

- なし。`unit-of-work-dependency.md`上、U7（Saved Query / Execution / History）は
  `querybuilder`パッケージへの依存を持たない（フロントエンドのURLクエリパラメータ経由の連携のみ、
  `frontend-components.md`のU7申し送り事項）。ただし`GeneratedSql`/`ParseResult`の形状は
  U7実装時の参照契約となるため変更時は要注意。

### 本ユニットが所有するデータエンティティ（内部DB/JPA）

なし。`domain-entities.md`確定（Q1 = A）のとおり、`querybuilder`パッケージは内部DBエンティティを
一切持たない。全てのDTOは純粋なJava `record`/`enum`（JPA非対応）。

### パッケージ設計判断（`nfr-design-patterns.md`/`logical-components.md`からの継承）

- `QueryBuilderMetadataService`/`SqlGenerationService`/`SqlParsingService`・本ユニット固有の
  全DTO・Visitorクラス群は`cherry.mastermeister.querybuilder`パッケージに配置する
  （`nfr-design-patterns.md` 1.1、`common`への切り出しは行わない）。
- 依存方向は`querybuilder → common, permission, schema, rdbmsconnection`の一方向のみ
  （`nfr-design-patterns.md` 1.1）。`masterdata`/`queryexecution`/`savedquery`への依存は持たない。

### サービス境界・責務

- `querybuilder`: `QueryBuilderMetadataService`（`listSelectableSchemas`/`listSelectableTables`/
  `listSelectableColumns`、`business-rules.md` 1節）、`SqlGenerationService`（`generate`、
  5節）、`SqlParsingService`（`parse`、6節、共有`ExecutorService`によるタイムアウト制御、
  `nfr-design-patterns.md` 2.1）、Visitorクラス群（AST→`QueryBuilderModel`変換、
  `nfr-design-patterns.md` 1.2）、`QueryBuilderController`（REST API、8節）。
- `security`（U1、ブラウンフィールド拡張）: `SecurityConfig`に`query-builder`エンドポイント用の
  `authenticated()`マッチャを追記する。
- フロントエンド: `features/queryBuilder/`（`QueryBuilderPage`/`FromJoinTab`/`SelectTab`/
  `WhereHavingTab`/`GroupByOrderByTab`/`LimitOffsetTab`/`GeneratedSqlPanel`/
  `SqlReverseParsePanel`/`api.ts`/`types.ts`、`frontend-components.md`）。U1の`apiClient`/
  `AppRouter`/`AppLayout`/`ProtectedRoute`をブラウンフィールド拡張・再利用する。

### テスト可能な性質（PBT-01、`business-logic-model.md`で識別済み）

P1〜P10（`business-logic-model.md`「テスト可能な性質」表）。Step 3で対応する`@Property`テストを
生成する。

### Code Generation時点で確定する事項（Functional Design/NFR Designで「Code Generationで確定」と
留保されていた論点）

1. **REST APIパスの確定**: `business-rules.md` 8節は「想定、正確なパスはCode Generationで確定」
   としていた。`SchemaController`（U3）と同じ`@RequestMapping("/api/query-builder/{connectionId}")`
   クラスレベルパスを採用する。`generate`/`parse`は`EffectivePermissionResolver`の権限判定
   （`resolveEffectiveTablePermission`/`resolveEffectiveColumnPermissions`）・`DialectStrategy`
   解決（`RdbmsConnectionRepository`経由）のいずれも`connectionId`を要するため、
   `business-rules.md`記載の`POST /api/query-builder/generate`・`POST /api/query-builder/parse`
   （`connectionId`なし）ではなく`POST /api/query-builder/{connectionId}/generate`・
   `POST /api/query-builder/{connectionId}/parse`とする（`frontend-components.md`もAPI一覧の
   パスは「正確なパスはCode Generationで確定する」と明記済みのため矛盾しない）。
2. **JSqlParserバージョン**: `com.github.jsqlparser:jsqlparser:5.3`（2026-07-12時点のMaven Central
   最新安定版、Apache License 2.0）を採用する。`dependencyManagement`ブロックへの追加時に
   Maven Centralで最新版を再確認する。
3. **Visitorクラスの構成**: `nfr-design-patterns.md` 1.2は「WHERE/HAVING句の式ツリー・SELECT項目・
   JOIN句をそれぞれVisitorで変換」と方針のみ確定。実装時、SELECT項目/HAVING条件の左辺/ORDER BY
   項目はいずれも「単一カラム、または単一引数の集計関数」という共通構造を持つため、共通の
   `AggregateExpressionVisitor`（`ExpressionVisitorAdapter`継承）で処理する。WHERE/HAVING条件式木
   （AND結合のみ、`business-rules.md` 2.1）は`WhereConditionVisitor`（同じく
   `ExpressionVisitorAdapter`継承）で処理し、内部で`AggregateExpressionVisitor`を再利用する。
   FROM/JOIN句・GROUP BY句・LIMIT/OFFSETは式ツリーを持たない単純なリスト/フィールド抽出のため
   （`PlainSelect`の`getFromItem`/`getJoins`/`getGroupBy`/`getLimit`/`getOffset`から直接構築）、
   Visitorパターンを介さず`SqlParsingService`内で直接組み立てる
   （`nfr-design-patterns.md`の「将来の対応構文拡張時もVisitorメソッドの追加で対応」という
   設計意図は式ツリーを持つWHERE/HAVING/SELECT/ORDER BY側で維持される）。
4. **件数上限検証のparse側の扱い**: `tech-stack-decisions.md` #5は「検証は`generate`・`parse`
   （変換後モデル）の両方で行う」と確定済みだが、`parse`側で上限超過を検出した場合の扱い
   （例外化 or `fullyParsed=false`）は未確定だった。`business-rules.md`
   6.2の「非対応構文はfullyParsed=false」という一貫した設計方針に合わせ、`parse`側の件数上限
   超過は例外化せず`fullyParsed=false`・`notice`に上限超過である旨を設定する（`generate`側は
   `business-rules.md` 4.2のGROUP BY制約違反と同様`ValidationException`のまま）。
5. **`parse-max-length`超過時の扱い**: 同様の理由で、`SqlParsingService.parse`呼び出し時の
   入力文字数超過（`mm.app.query-builder.parse-max-length`）も例外化せず`fullyParsed=false`・
   `notice`に「入力SQLが長すぎるため解析できません」等のメッセージを設定する（タイムアウト・
   非対応構文と同じ「解析不能」カテゴリとして統一する）。
6. **`Optional`フィールドを持つ`ParseResult`のJSONシリアライズ**: Spring Boot
   `spring-boot-starter-web`が推移的に含む`spring-boot-starter-json`は`jackson-datatype-jdk8`
   モジュールを含み、Spring Bootの`JacksonAutoConfiguration`が自動登録するため、`ParseResult`の
   `Optional<QueryBuilderModel> model`/`Optional<String> notice`は追加設定なしで正しく
   JSON化される（値ありは値、空は`null`として直列化）。新規依存追加は不要（確認事項）。

---

## ステップ一覧

### Step 1: プロジェクト構造セットアップ
- [x] 1-1. `backend/build.gradle.kts`（既存、ブラウンフィールド修正）の`dependencyManagement`
      ブロックに`dependency("com.github.jsqlparser:jsqlparser:5.3")`を追加し、`dependencies`
      ブロックに`implementation("com.github.jsqlparser:jsqlparser")`を追加する
      （バージョン番号は`dependencyManagement`側で一元管理、Gradleバージョン管理規約）。

### Step 2: ビジネスロジック生成
- [x] 2-1. `backend/src/main/java/cherry/mastermeister/querybuilder/`にメタデータ参照系DTOを
      生成する（`domain-entities.md`確定）: `TableRef`（record: `schema, table, comment`）、
      `ColumnRef`（record: `columnName, dataType, nullable`）。
- [x] 2-2. `backend/src/main/java/cherry/mastermeister/querybuilder/`にクエリモデル系DTO・enumを
      生成する（`domain-entities.md`確定）: `JoinType`（enum: `INNER, LEFT, RIGHT`）、
      `AggregateFunction`（enum: `NONE, COUNT, SUM, AVG, MIN, MAX`）、`Operator`（enum: `EQ, NE,
      GT, LT, GE, LE, LIKE, IS_NULL, IS_NOT_NULL`）、`FromItem`（record: `schema, table,
      alias`）、`JoinItem`（record: `JoinType type, schema, table, alias, Condition
      onCondition`）、`SelectItem`（record: `tableAlias, columnName, AggregateFunction
      aggregateFunction, outputAlias`）、`Condition`（record: `tableAlias, columnName,
      AggregateFunction aggregateFunction, Operator operator, Object value`）、`OrderByItem`
      （record: `tableAlias, columnName, AggregateFunction aggregateFunction, SortDirection
      direction`——`SortDirection`はU1既存`common.dialect`を再利用、新規定義しない）、
      `QueryBuilderModel`（record: `List<SelectItem> selectItems, FromItem fromItem,
      List<JoinItem> joinItems, List<Condition> whereConditions, List<String> groupByColumns,
      List<Condition> havingConditions, List<OrderByItem> orderByItems, Integer limit, Integer
      offset`）。
- [x] 2-3. `backend/src/main/java/cherry/mastermeister/querybuilder/`にAPI境界DTOを生成する
      （`domain-entities.md`確定）: `GeneratedSql`（record: `String sql, Map<String, Object>
      params`）、`ParseResult`（record: `boolean fullyParsed, Optional<QueryBuilderModel>
      model, Optional<String> notice`）。
- [x] 2-4. `backend/src/main/java/cherry/mastermeister/querybuilder/QueryBuilderMetadataService.java`
      （`@Service`）: `List<String> listSelectableSchemas(Long userId, Long connectionId)`
      （`EffectivePermissionResolver.listAccessibleSchemas`へ委譲、`business-rules.md` 1.1）、
      `List<TableRef> listSelectableTables(Long userId, Long connectionId, String schema)`
      （`listAccessibleTables`のテーブル名一覧を`resolveEffectiveTablePermission`が`READ`以上と
      判定したものだけ`SchemaQueryService.listTables`のメタデータと組み合わせて`TableRef`化）、
      `List<ColumnRef> listSelectableColumns(Long userId, Long connectionId, String schema,
      String table)`（`SchemaQueryService.getTableDetail`のカラム一覧を
      `resolveEffectiveColumnPermissions`が`READ`以上と判定したものだけ`ColumnRef`化）を実装する
      （`business-rules.md` 1.1、フロー1）。
- [x] 2-5. `backend/src/main/java/cherry/mastermeister/querybuilder/SqlGenerationService.java`
      （`@Service`）: `GeneratedSql generate(Long connectionId, QueryBuilderModel model)`を
      実装する（`business-rules.md` 5節、フロー2）。`RdbmsConnectionRepository`+
      `DialectStrategyFactory.resolve`でその接続の`DialectStrategy`を解決（`masterdata`と
      同一パターン）。各リストの件数上限（`mm.app.query-builder.max-select-items`等6キー、
      `tech-stack-decisions.md` #5）超過は`ValidationException`。GROUP BY制約違反
      （`business-rules.md` 4.2）も`ValidationException`。FROM句・JOIN句・WHERE句（AND結合の
      み）・GROUP BY句・HAVING句・ORDER BY句（`DialectStrategy.buildNullsOrderingClause`）・
      LIMIT OFFSET句（`DialectStrategy.buildPagingClause`）を`StringBuilder`で順に組み立て、
      識別子は`DialectStrategy.quoteIdentifier`でクオート、値は`:paramN`連番プレースホルダに
      置換して`Map<String, Object>`に集約する（`business-rules.md` 5.1-5.2）。
- [x] 2-6. `backend/src/main/java/cherry/mastermeister/querybuilder/AggregateExpressionVisitor.java`
      （`ExpressionVisitorAdapter`継承、上記「Code Generation時点で確定する事項」3）: 単一の
      `Column`または単一引数の集計関数`Function`（COUNT/SUM/AVG/MIN/MAX）式を`(tableAlias,
      columnName, AggregateFunction)`に変換する。それ以外の式形状（算術式・CASE式・
      サブクエリ等）に遭遇した場合は変換失敗フラグを立てる。
- [x] 2-7. `backend/src/main/java/cherry/mastermeister/querybuilder/WhereConditionVisitor.java`
      （`ExpressionVisitorAdapter`継承）: WHERE/HAVING共通の条件式木を`List<Condition>`に変換する。
      `AndExpression`は再帰的に両辺を展開、比較演算子（`EqualsTo`/`NotEqualsTo`/`GreaterThan`/
      `MinorThan`/`GreaterThanEquals`/`MinorThanEquals`/`LikeExpression`/`IsNullExpression`）の
      左辺は`AggregateExpressionVisitor`で変換する。`OrExpression`/`Parenthesis`/その他の式に
      遭遇した場合は変換失敗フラグを立て、以降の変換を打ち切る（`nfr-design-patterns.md` 1.2）。
- [x] 2-8. `backend/src/main/java/cherry/mastermeister/querybuilder/SqlParsingService.java`
      （`@Service`）: `ParseResult parse(Long userId, Long connectionId, String rawSql)`を
      実装する（`business-rules.md` 6節、フロー3）。`mm.app.query-builder.parse-max-length`
      超過は`fullyParsed=false`（上記確定事項5）。固定サイズ共有`ExecutorService`
      （`@PreDestroy`で`shutdown`、`mm.app.query-builder.parse-executor-pool-size`）に
      `CCJSqlParserUtil.parse(rawSql)`を`submit`し、`Future.get(parseTimeoutSeconds,
      TimeUnit.SECONDS)`でタイムアウト制御（`mm.app.query-builder.parse-timeout`）、
      `TimeoutException`時は`future.cancel(true)`後`fullyParsed=false`
      （`nfr-design-patterns.md` 2.1）。パース成功後、`PlainSelect`から`FromItem`/`JoinItem`
      （`getJoins`のJOIN種別を`JoinType`にマッピング、非対応種別=`fullyParsed=false`）/
      `groupByColumns`/`limit`/`offset`を直接抽出し、SELECT項目・WHERE・HAVING・ORDER BYは
      Step 2-6/2-7のVisitorで変換する。件数上限超過（上記確定事項4）・非対応構文検出
      （`business-rules.md` 6.2）はいずれも`fullyParsed=false`。変換成功後、参照する全
      テーブル/カラムについて`resolveEffectiveTablePermission`/`resolveEffectiveColumnPermissions`
      で`READ`以上か検証し、満たさない場合は`fullyParsed=false`（`business-rules.md` 1.2）。
- [x] 2-9. `backend/src/main/java/cherry/mastermeister/querybuilder/QueryBuilderMetadataService.java`
      （既存、ブラウンフィールド修正）に`List<ConnectionSummary>
      listSelectableConnections(Long userId)`を追加する（U5「ブラウンフィールド発見事項」5と
      同種の問題——`frontend-components.md`のQueryBuilderPageは接続選択を内包する設計だが、
      `querybuilder`パッケージには接続一覧を列挙する手段がない——Step 11着手時に判明、
      ユーザ指示によりU5と同一パターンで解決する）。`RdbmsConnectionRepository`を新規注入し、
      全接続のうち`effectivePermissionResolver.listAccessibleSchemas(userId, connectionId)`が
      非空のものだけを`ConnectionSummary`（`rdbmsconnection`パッケージ既存、新規DTOなし）へ
      マッピングして返す。

### Step 3: ビジネスロジック単体テスト（PBT-01〜PBT-08, PBT-10）
`business-logic-model.md`のP1〜P10に対応する`@Property`テストをjqwikで生成する。対象RDBMSへの
実アクセスを伴うため、U3/U4/U5と同じ手法（`org.h2.tools.Server`によるH2 TCPサーバを対象RDBMS役と
して起動、`@SpringBootTest` `@JqwikSpringSupport`）を用いる。`EffectivePermissionResolver`/
`SchemaQueryService`はMockitoでモック化し、`querybuilder`固有ロジックを独立して検証する。
- [x] 3-1. **P1**（`listSelectableColumns`のREAD未満カラム除外Invariant）:
      `QueryBuilderMetadataServiceTest`に`@Property`テストを生成する。
- [x] 3-2. **P2**（JOIN句キーワード限定Invariant）・**P3**（GROUP BY制約違反時の
      `ValidationException`Invariant）・**P4**（WHERE/HAVING句AND結合限定Invariant）・
      **P5**（プレースホルダ/paramsキー一致Invariant）・**P6**（識別子クオートInvariant）・
      **P10**（LIMIT OFFSET句の有無Invariant）: `SqlGenerationServiceTest`に`@Property`テストを
      生成する。
- [x] 3-3. **P7**（非対応構文検出Invariant）・**P9**（parse側の権限フィルタInvariant）:
      `SqlParsingServiceTest`に`@Property`テストを生成する。
- [x] 3-4. **P8**（`generate`→`parse`ラウンドトリップInvariant）: `SqlParsingServiceTest`
      （`SqlGenerationService`と`SqlParsingService`の両方を利用するため同ファイルに追加、
      または`QueryBuilderRoundTripTest`として独立させるかはStep 3-4実装時に判断する）に
      `@Property`テストを生成する。→ `QueryBuilderRoundTripTest`として独立させた
      （`SqlParsingServiceTest`はparse単体の関心事に留める設計判断）。

### Step 4: ビジネスロジックサマリ
- [x] 4-1. `aidlc-docs/construction/u6-query-builder/code/business-logic-summary.md`を生成し、
      Step 2・Step 3で生成したクラス一覧とP1〜P10の対応関係を表形式で記載する
      （U1〜U5の`business-logic-summary.md`と同一構成）。

### Step 5: APIレイヤ生成
- [x] 5-1. `backend/src/main/java/cherry/mastermeister/querybuilder/SqlParseRequest.java`
      （record: `String rawSql`、リクエストボディ用）を生成する。
- [x] 5-2. `backend/src/main/java/cherry/mastermeister/querybuilder/QueryBuilderController.java`
      （`@RestController @RequestMapping("/api/query-builder/{connectionId}")`）: `GET
      "/schemas"`（`listSelectableSchemas`）、`GET "/schemas/{schema}/tables"`
      （`listSelectableTables`）、`GET "/schemas/{schema}/tables/{table}/columns"`
      （`listSelectableColumns`）、`POST "/generate"`（`QueryBuilderModel`をボディで受け
      `generate`→`GeneratedSql`）、`POST "/parse"`（`SqlParseRequest`をボディで受け`parse`→
      `ParseResult`）を生成する（上記「Code Generation時点で確定する事項」1、
      `business-rules.md` 8節）。`userId`は`Authentication#getPrincipal()`キャスト取得
      （U2〜U5のコントローラと同一パターン）。
- [x] 5-3. `backend/src/main/java/cherry/mastermeister/security/SecurityConfig.java`
      （既存、ブラウンフィールド修正）に`.requestMatchers("/api/query-builder/**")
      .authenticated()`を、`.requestMatchers("/api/master-data/**").authenticated()`と同様の
      場所に追記する（`business-rules.md` 8節「認証済みユーザ全員」）。
- [x] 5-4. `backend/src/main/java/cherry/mastermeister/querybuilder/QueryBuilderController.java`
      （既存、ブラウンフィールド修正）に`GET "/connections"`（`listSelectableConnections`）を
      追加する（U5「ブラウンフィールド発見事項」5と同種の対応、item 2-9参照）。クラスレベルの
      `@RequestMapping`を`"/api/query-builder/{connectionId}"`から`"/api/query-builder"`へ
      変更し、`{connectionId}`を既存5メソッドの`@GetMapping`/`@PostMapping`側へ移す
      （解決後のURLは不変）。

### Step 6: APIレイヤ単体テスト
- [x] 6-1. `QueryBuilderControllerTest`（`@WebMvcTest` + `spring-security-test`）: 5エンドポイント
      それぞれについて認証済みユーザ成功系・未認証401をexample-basedテストで検証する
      （U2〜U5のControllerTestパターンを踏襲、本ユニットは管理者ロール制約がないため403系
      テストは不要——`business-rules.md` 8節）。
- [x] 6-2. `QueryBuilderControllerTest`に`GET /connections`の成功系・未認証401テストを追加する
      （item 2-9・5-4参照）。

### Step 7: APIレイヤサマリ
- [x] 7-1. `aidlc-docs/construction/u6-query-builder/code/api-layer-summary.md`を生成し、
      エンドポイント一覧（パス・メソッド・認可要件・リクエスト/レスポンス形状）を記載する。
- [x] 7-2. `api-layer-summary.md`に`GET /api/query-builder/connections`（item 2-9・5-4参照）を
      追記する。

### Step 8〜10: リポジトリレイヤ
- [x] 8/9/10-1. **該当なし（N/A）**: `domain-entities.md`確定（Q1 = A）のとおり本ユニットは
      内部DBエンティティを一切持たない。リポジトリ生成・単体テスト・サマリのいずれも対象外。

### Step 11: フロントエンドコンポーネント生成
- [x] 11-1. `frontend/src/features/queryBuilder/types.ts`: `frontend-components.md`・
      `domain-entities.md`のDTOに対応するTypeScript型（`TableRef`/`ColumnRef`/`FromItem`/
      `JoinItem`/`JoinType`/`SelectItem`/`AggregateFunction`/`Condition`/`Operator`/
      `OrderByItem`/`QueryBuilderModel`/`GeneratedSql`/`ParseResult`等）を定義する。
      `SortDirection`は`masterdata`/`schema`等と同様、本feature内にローカル再定義する
      （他feature非依存の方針、`frontend-components.md`）。
- [x] 11-2. `frontend/src/features/queryBuilder/api.ts`: `listSelectableSchemas`/
      `listSelectableTables`/`listSelectableColumns`/`generateSql`/`parseSql`（Step 5-2確定の
      実パスに対応）を実装する。U1の`apiClient`を再利用する。
- [x] 11-3. `frontend/src/features/queryBuilder/QueryBuilderPage.tsx`・`FromJoinTab.tsx`:
      接続・スキーマ選択、タブ切り替えコンテナ（`QueryBuilderPage`、`model`状態管理）と、
      アクセス可能テーブルのみを選択肢とするベーステーブル/JOINテーブル・エイリアス指定UI
      （`FromJoinTab`、GEN-6 AC）を生成する（`frontend-components.md`、フロー1手順1・3）。
- [x] 11-4. `frontend/src/features/queryBuilder/SelectTab.tsx`: アクセス可能カラムのみを
      選択肢とするSELECT項目（カラム・集計関数・出力エイリアス）指定UI、および
      「このテーブルの全カラムを追加」一括追加ボタン（`mm.app.query-builder.max-select-items`
      超過時はフロントエンド側でエラー表示）を生成する（GEN-7 AC、`frontend-components.md`）。
- [x] 11-5. `frontend/src/features/queryBuilder/WhereHavingTab.tsx`・`GroupByOrderByTab.tsx`・
      `LimitOffsetTab.tsx`: AND結合のみの条件リスト組み立てUI（WHERE/HAVING共通、`target`
      propsで切替）、GROUP BY/ORDER BY選択UI（`target`propsで切替）、LIMIT/OFFSET数値入力を
      生成する（GEN-7 AC、`frontend-components.md`）。
- [x] 11-6. `frontend/src/features/queryBuilder/GeneratedSqlPanel.tsx`: 「SQL生成」ボタン→
      `generateSql`呼び出し→生成SQL・パラメータ表示・コピーボタン、`ValidationException`の
      エラー表示、「保存」「実行」ボタン（`onNavigateToSave`/`onNavigateToExecute`props、U6
      時点では未実装）を生成する（GEN-8、`business-rules.md` 7節、`frontend-components.md`）。
- [x] 11-7. `frontend/src/features/queryBuilder/SqlReverseParsePanel.tsx`: 手入力SQL貼り付け→
      `parseSql`呼び出し→`fullyParsed=true`ならタブへ反映（`onApply`props）、`false`なら
      `notice`表示を生成する（GEN-9、`frontend-components.md`）。
- [x] 11-8. `frontend/src/routes/AppRouter.tsx`（既存、ブラウンフィールド修正）に
      `/query-builder`（`QueryBuilderPage`、`ProtectedRoute`・`requiredRole`指定なし）を追加する。
      `frontend/src/components/AppLayout.tsx`（既存、ブラウンフィールド修正）に
      「クエリビルダー」ナビゲーションリンクを全ユーザ表示で追加する
      （`frontend-components.md` AppRouter.tsxへの追加）。

### Step 12: フロントエンドコンポーネント単体テスト
- [x] 12-1. `QueryBuilderPage.test.tsx`・`FromJoinTab.test.tsx`・`SelectTab.test.tsx`・
      `WhereHavingTab.test.tsx`・`GroupByOrderByTab.test.tsx`・`LimitOffsetTab.test.tsx`・
      `GeneratedSqlPanel.test.tsx`・`SqlReverseParsePanel.test.tsx`（vitest + Testing
      Library、U4/U5の各featureテストパターンを踏襲）を生成する。

### Step 13: フロントエンドコンポーネントサマリ
- [ ] 13-1. `aidlc-docs/construction/u6-query-builder/code/frontend-summary.md`を生成する。

### Step 14: データベースマイグレーションスクリプト
- [ ] 14-1. **該当なし（N/A）**: 本ユニットは内部DBエンティティを持たないため対象外。

### Step 15: ドキュメント生成
- [ ] 15-1. `aidlc-docs/construction/u6-query-builder/code/testing-summary.md`（P1〜P10と
      テストクラスの対応表、example-basedテスト一覧、実行確認状況）を生成する。リポジトリ層が
      N/Aのため`repository-layer-summary.md`は生成しない旨を冒頭に明記する。

### Step 16: デプロイ成果物生成
- [ ] 16-1. `backend/src/main/resources/application.yml`（既存、ブラウンフィールド修正）に
      `mm.app.query-builder.parse-max-length`（既定`10000`）、
      `mm.app.query-builder.parse-timeout`（既定`5`秒）、
      `mm.app.query-builder.parse-executor-pool-size`（既定`4`）、
      `mm.app.query-builder.max-select-items`（既定`100`）、
      `mm.app.query-builder.max-join-items`（既定`10`）、
      `mm.app.query-builder.max-where-conditions`（既定`30`）、
      `mm.app.query-builder.max-group-by-columns`（既定`30`）、
      `mm.app.query-builder.max-having-conditions`（既定`20`）、
      `mm.app.query-builder.max-order-by-items`（既定`20`）を追記する
      （`nfr-requirements.md`・`nfr-design/logical-components.md` 4節確定）。

---

## 完了基準
- 上記全ステップの生成物がワークスペースルート配下に作成され、対応する単体テストが生成されて
  いること（実行・グリーン確認はBuild and Testステージで行う）。
- P1〜P10全ての性質にjqwik `@Property`テストが対応していること（PBT-02〜PBT-08準拠）。
- `aidlc-docs/construction/u6-query-builder/code/`配下に4つのサマリドキュメント
  （business-logic-summary.md, api-layer-summary.md, frontend-summary.md,
  testing-summary.md）が生成されていること（リポジトリ層はN/Aのため
  repository-layer-summary.mdは生成しない）。