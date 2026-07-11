# U5: Master Data Maintenance — Functional Design Plan

## Step 1: ユニットコンテキスト分析

- **ユニット定義**（`unit-of-work.md`）: バックエンドパッケージ `masterdata`。フロントエンド
  `features/masterData/`。対応ストーリー MVP-10, MVP-11, GEN-1, GEN-2, GEN-3, GEN-4, GEN-5。
  責務: 一般ユーザ向けのマスタデータ閲覧・編集。
  - アクセス可能スキーマ/テーブル一覧、レコード一覧（絞り込み・ソート・ページング、UI操作/
    手入力WHERE・ORDER BY両対応、手入力時はカラム権限フィルタ対象外＝GEN-2の設計上の
    意図的例外）
  - 作成・更新・削除を単一トランザクションで処理する統一API（GEN-4、`MasterDataMutationService`）
  - 実行前の権限検証（更新: 対象カラムRU以上、作成: 補助権限C+主キー列RU、削除: 補助権限D+
    主キー列R以上）、失敗時ロールバック・概要メッセージのみ返却
  - 主要コンポーネント: `MasterDataQueryService`, `MasterDataMutationService`
  - U1（`common`, `audit`）, U3（`rdbmsconnection`, `schema`）, U4（`permission`）に依存。
- **対応ストーリー**（`stories.md`）:
  - MVP-10: アクセス可能なテーブル/ビュー一覧の表示（Allow権限のあるテーブル/ビューのみ表示）
  - MVP-11: レコード一覧の閲覧（ページング、R以上のカラムのみ表示、大量データ取得は監査記録
    ——閾値は設定可能、既定100件以上）
  - GEN-1: レコードの絞り込み・並び替え（UI操作）——読み取り権限以上のカラムのみ選択可、
    複数条件の組み合わせ、ページング付き結果
  - GEN-2: WHERE句・ORDER BY句の手入力——カラムレベル読み取り権限フィルタ対象外（設計上の
    意図的例外）
  - GEN-3: レコードの編集——RU以上のカラムのみ編集可能、それ以外は読み取り専用/非表示
  - GEN-4: 変更内容の単一トランザクション反映——「反映」ボタンで作成/更新/削除すべてを単一API
    エンドポイントへ送信、単一トランザクション、一部失敗で全体ロールバック、監査記録
  - GEN-5: レコードの作成・削除——フルアクセス（CRUD、全カラム更新可能）権限があるテーブルのみ
    作成・削除操作可能、権限がなければUI非表示
- **既存の確定事項**（Application Design / U1・U3・U4から継承、再検討不要）:
  - `component-methods.md`に以下のメソッドシグネチャが定義済み:
    ```
    MasterDataQueryService:
      List<String> listAccessibleSchemas(Long userId, Long connectionId)
      List<TableSummary> listAccessibleTables(Long userId, Long connectionId, String schema)
      PageResult<RecordDto> listRecords(Long userId, Long connectionId, String schema, String table,
                                         FilterCriteria criteria, PageRequest page)
        // criteria は UI組立条件（読み取り権限のあるカラムのみ選択可）と
        // 手入力WHERE/ORDER BY（権限フィルタ対象外、GEN-2）の両対応

    MasterDataMutationService:
      MutationResult applyChanges(Long userId, Long connectionId, String schema, String table,
                                   MutationRequest request)
        // MutationRequest = { List<RecordCreate>, List<RecordUpdate>, List<RecordDelete> }
        // 単一トランザクションで実行し、いずれか1件でも失敗した場合は全体ロールバック
        // 失敗時、MutationResult.errorMessage には SQLException 由来の概要メッセージのみを設定
        //（行・カラム単位の詳細特定は行わない、Application Design Question 8 = B）
    ```
  - `components.md`の責務記述: `MasterDataQueryService`は`EffectivePermissionResolver`による
    スキーマ/テーブル/カラム読み取り権限フィルタを適用し、手入力WHERE/ORDER BYはカラム権限
    フィルタの対象外（GEN-2）。`MasterDataMutationService`は実行前に`EffectivePermissionResolver`
    で操作可否（更新: RU以上、作成: `canCreate`、削除: `canDelete`）を検証する。
  - `services.md`フロー3・フロー4に確定済みの処理順序が記載されている（Step 1で参照確認済み、
    変更不要）。
  - `EffectivePermissionResolver`（U4, `permission`）: `resolveEffectiveTablePermission`,
    `resolveEffectiveColumnPermissions`, `canCreate`, `canDelete`, `listAccessibleSchemas`,
    `listAccessibleTables`。U5はこれらのAPIのみを呼び出し、`permission`の内部エンティティには
    直接アクセスしない。
  - `SchemaQueryService`（U3, `schema`）: `listSchemas`/`listTables`/`getTableDetail`で
    取り込み済みメタデータ（カラム名・データ型・`nullable`・`primaryKeySequence`・`stale`等）を
    参照する。権限フィルタを持たない生の一覧であり、`masterdata`側で`EffectivePermissionResolver`の
    判定結果によりフィルタする。
  - `ConnectionPoolRegistry`（U3, `rdbmsconnection`）: `connectionId`ごとの`DataSource`/
    `NamedParameterJdbcTemplate`を提供。`DriverManager.getConnection()`は使用しない。
  - `common/dialect/DialectStrategy`（U1）: `quoteIdentifier`, `buildPagingClause`,
    `buildNullsOrderingClause`, `buildJdbcUrl`等、対象RDBMS（MySQL/MariaDB/PostgreSQL/H2）の
    方言差異を吸収する既存Strategyを利用する（新規実装不要、`masterdata`は`schema`/
    `querybuilder`/`queryexecution`と同様の利用者側）。
  - 監査ログ（U1, `audit`）: `EventType`に本ユニット向けの値が既に予約済み: `LARGE_RECORD_READ`
    （`DATA_ACCESS`カテゴリ、MVP-11、既定閾値100件以上）、`MASTER_DATA_MUTATION`
    （`DATA_ACCESS`カテゴリ、GEN-4）。新規`EventType`追加は不要。
  - `component-dependency.md`の依存マトリクスで`masterdata → common, audit, rdbmsconnection,
    schema, permission`が確定済み（`querybuilder`/`queryexecution`への依存はなし）。
  - **既知の設計論点（`component-dependency.md`注記）**: `queryexecution`（U7）は要件上
    「読み取り専用SQLのみ」という制約のみが明記され、テーブル/カラム単位の読み取り権限フィルタは
    課されない設計とされている。本ユニット（U5）の手入力WHERE/ORDER BY（GEN-2）も同様に
    カラム権限フィルタの対象外という設計上の例外だが、対象テーブル自体は
    `listRecords(..., schema, table, ...)`で固定されるため、`queryexecution`ほど広範な影響は
    ない。ただし手入力文字列をSQLに直接連結する実装は、UNION SELECT等による他テーブルへの
    アクセス拡大（対象RDBMS接続の共有DB認証情報の権限範囲内での情報漏洩）のリスクがあり、
    Functional Designで安全性方針を確定する必要がある（Q4参照）。

## Step 2-4: 計画・質問

以下8問について回答をお願いします。各質問には推奨案（A）を用意していますが、
別の選択肢や自由記述でも構いません。

---

### Q1. Domain Model — 内部DBエンティティの要否

`masterdata`パッケージは対象RDBMS上のマスタデータそのものを扱う（`NamedParameterJdbcTemplate`
経由）。内部DB(H2, JPA)に本ユニット固有の新規エンティティが必要か。

- **A（推奨）**: 内部DBエンティティは追加しない。`masterdata`が保持する状態は一切なく、すべて
  対象RDBMSへの都度アクセス（`SchemaQueryService`のメタデータ参照＋`ConnectionPoolRegistry`の
  `NamedParameterJdbcTemplate`によるデータアクセス）と、`permission`/`audit`への委譲のみで
  完結する。`RecordDto`/`FilterCriteria`/`MutationRequest`等はすべてJPA非対応の純粋なDTO
  （service/dto層のPOJO・record）とする。
- **B**: 何らかの内部DB状態（例: UI側の一時的な絞り込み条件の保存等）を持たせる。
- **C**: その他（自由記述）

[Answer]: 

---

### Q2. Business Logic Modeling — `RecordDto`（汎用レコード表現）のデータ構造

対象テーブルは接続ごとに任意のカラム構成を持つため、固定フィールドのJavaクラスでは表現できない。
`RecordDto`をどう設計するか。

- **A（推奨）**: `RecordDto`はカラム名→値の順序付きマップ（`LinkedHashMap<String, Object>`、
  `SchemaColumn.ordinalPosition`順）を保持するrecordとする。
  ```
  record RecordDto(Map<String, Object> values)
  ```
  値の型は`java.time.LocalDate`/`LocalDateTime`/`OffsetDateTime`（CLAUDE.md規約の`java.time`
  使用方針に準拠）、`BigDecimal`、`String`、`Boolean`等、JDBC標準の型マッピングをそのまま用いる
  （`SchemaColumn.dataType`から対象RDBMS方言ごとのJava型への変換ルールは`common/dialect`層の
  既存責務に含めず、`NamedParameterJdbcTemplate`のデフォルトの型マッピングに委ねる）。主キー値は
  `values`マップの一部として含まれる（別フィールドに分離しない——`RecordUpdate`/`RecordDelete`側で
  主キー列を明示的に指定する設計、Q6参照）。
- **B**: 型安全性を優先し、カラムごとに`ColumnValue(String columnName, String dataType, Object
  value)`のリストとして保持する（マップよりも冗長だがカラムのデータ型情報を都度携行できる）。
- **C**: その他（自由記述）

[Answer]: 

---

### Q3. Business Rules — `FilterCriteria`（絞り込み・ソート条件）のデータモデル

GEN-1（UI組立条件）とGEN-2（手入力WHERE/ORDER BY）の両方を`listRecords`の`criteria`パラメータで
表現する必要がある。

- **A（推奨）**: `FilterCriteria`をUIモードと手入力モードの排他的な2種類として設計する
  （画面上もトグルで切り替える想定、Q8参照）。
  ```
  record FilterCriteria(Mode mode, List<UiCondition> uiConditions, List<UiSort> uiSorts,
                         String rawWhere, String rawOrderBy)
    // Mode = UI | RAW
    // UI: uiConditions/uiSorts を使用（rawWhere/rawOrderByはnull）
    // RAW: rawWhere/rawOrderByを使用（uiConditions/uiSortsは空）

  record UiCondition(String columnName, Operator operator, Object value)
    // Operator = EQ, NE, GT, LT, GE, LE, LIKE, IS_NULL, IS_NOT_NULL
    // 複数条件はAND結合のみ（OR・括弧によるグルーピングはMVPスコープ外——GEN-1のACは
    //「複数条件を組み合わせて絞り込める」のみで、AND/OR混在や優先順位までは要求していない）

  record UiSort(String columnName, SortDirection direction)
    // 複数カラムでのソートを許容（優先順位はリスト順）
  ```
  `MasterDataQueryService.listRecords`は、UIモードの場合`resolveEffectiveColumnPermissions`で
  R以上のカラムのみが`uiConditions`/`uiSorts`の対象として許可されていることを検証し（許可外の
  カラムが指定された場合は`IllegalArgumentException`相当のエラー）、RAWモードの場合はこの検証を
  スキップする（GEN-2の意図的例外）。
- **B**: AND/ORの組み合わせや括弧によるグルーピングまで含めた汎用条件木（`querybuilder`の
  WHERE表現に近い設計）をこの時点で導入する。
- **C**: その他（自由記述）

[Answer]: 

---

### Q4. Business Rules / Security — 手入力WHERE/ORDER BY（GEN-2）の安全性方針

Step 1で洗い出した論点: 手入力`rawWhere`/`rawOrderBy`をSQLに直接連結する実装は、対象RDBMS接続の
共有DB認証情報の権限範囲内で、UNION SELECT等により他テーブル（本来アクセス権限のないテーブルを
含む）のデータを取得できてしまうリスクがある。`component-dependency.md`は`queryexecution`
（U7）についても同種の論点を「要件上未確定、`security-baseline`拡張または後続NFR Requirementsで
要否確認」として先送りする方針を既に採用している。

- **A（推奨、`queryexecution`と同一方針を踏襲）**: GEN-2の受け入れ基準どおり、`rawWhere`/
  `rawOrderBy`はカラム権限フィルタの対象外という設計を維持し、追加のSQL解析・ホワイトリスト検証は
  本ユニットのFunctional Designでは導入しない（複雑なSQL構文解析はGEN-9のリバースエンジニアリング
  機能＝U6の責務であり、U5に重複実装しない）。ただし最低限の防御として、複数ステートメント注入
  （セミコロン区切り）のみ簡易チェックで拒否する（`NamedParameterJdbcTemplate`は本来単一
  ステートメントしか実行しないため実害は限定的だが、コメントアウト等を用いた分断攻撃に対する
  最低限の防御として明示的に拒否する）。UNION SELECT等による他テーブルへのアクセス拡大リスクは
  「対象RDBMS接続の管理者（Admin）が許可した機能であり、一般ユーザの手入力SQL機能は要件上
  意図的にカラム権限フィルタの対象外とされている」既知のリスクとして`business-rules.md`に明記し、
  `security-baseline`拡張のオプトイン時に改めて要否確認する対象とする（`queryexecution`と同じ
  取り扱い）。
- **B**: `rawWhere`/`rawOrderBy`に対しても`EffectivePermissionResolver`でアクセス可能テーブル一覧
  との突合を行うSQL構文解析（簡易パーサ）をこの時点で導入する。
- **C**: その他（自由記述）

[Answer]: 

---

### Q5. Business Rules — `MutationRequest`の権限検証失敗時の挙動

`services.md`フロー4は「検証を通過した操作のみ、対象RDBMSへの単一トランザクション内でまとめて
実行する」と記載しているが、リクエスト内の一部操作が権限検証（RU以上/`canCreate`/`canDelete`）に
失敗した場合の挙動が未確定。

- **A（推奨）**: リクエスト内の**いずれか1件でも**権限検証に失敗した場合、対象RDBMSへの
  問い合わせを一切行わずリクエスト全体を拒否する（400相当、`MutationResult.errorMessage`に
  権限検証失敗の概要を設定）。GEN-4の「単一トランザクション」「一部失敗で全体ロールバック」という
  要件と一貫させ、「検証段階の失敗」と「実行段階（`SQLException`）の失敗」を同じ
  all-or-nothing方針で統一する。権限検証失敗も`AuditLogService.record(...)`で
  `MASTER_DATA_MUTATION`・`Result.FAILURE`として記録する。
- **B**: 権限検証に失敗した操作のみをスキップし、成功した操作のみをトランザクション内で実行する
  （部分成功を許容、`MutationResult`にスキップ件数・成功件数を含める）。
- **C**: その他（自由記述）

[Answer]: 

---

### Q6. Business Rules — 主キーなしテーブルの更新・削除の扱い

`EffectivePermissionResolver.canDelete`は主キーなしテーブルで常に`false`（U4で確定済み）。更新
（`RecordUpdate`）については、対象行を一意に再特定する手段（主キー値によるWHERE句組立て）が
主キーなしテーブルには存在しない。

- **A（推奨）**: `RecordUpdate`は主キー値による行の再特定を前提とし、主キーなしテーブルに対する
  `RecordUpdate`操作はQ5と同じ全体拒否方針の対象とする（`MasterDataMutationService`が
  `SchemaTable`のメタデータから主キー構成の有無を判定し、主キーなしテーブルへの`RecordUpdate`が
  1件でも含まれていれば、対象カラムの権限に関わらずリクエスト全体を拒否する）。`RecordCreate`
  （`INSERT`、行の再特定が不要）は主キーなしテーブルでも`canCreate`の例外規定
  （補助権限Cのみで許可、U4確定済み）どおり許可する。`RecordDelete`は`canDelete`が常に`false`に
  なることで自然に拒否される（追加のチェック不要）。結果として、主キーなしテーブルは
  「作成のみ可能・更新/削除は不可」という一貫した扱いになる。
- **B**: 主キーなしテーブルの更新は、`RecordUpdate`が保持する全カラムの現在値（更新前の全カラム
  スナップショット）をWHERE句に用いて行を特定する（同一内容の行が複数存在する場合は複数行が
  同時に更新される可能性を許容する）。
- **C**: その他（自由記述）

[Answer]: 

---

### Q7. Business Rules — 大量データ閲覧監査の閾値設定

MVP-11: 「大量データ取得（閾値は設定可能、デフォルト100件以上）は監査ログに記録される」
（`REQUIREMENTS.md` 7章にも同様の記載）。設定方式を確定する。

- **A（推奨）**: `application.yml`に`mm.app.master-data.large-record-threshold`
  （デフォルト`100`、環境変数`MM_APP_MASTER_DATA_LARGE_RECORD_THRESHOLD`でオーバーライド可能）を
  追加する（U1の`mm.app.audit.default-page-size`と同様の外部化設定パターンを踏襲）。
  `listRecords`が返す`PageResult<RecordDto>`の実件数（1回のレスポンスに含まれる件数、
  ページサイズ）がこの閾値以上の場合に`AuditLogService.record(EventType.LARGE_RECORD_READ, ...)`を
  呼び出す（ページング自体の閾値＝1ページあたりの件数で判定し、テーブル全体の総件数では
  判定しない——「取得」という操作単位での大量データ取得を検知する趣旨のため）。
- **B**: 1ページあたりの件数ではなく、同一ユーザ・同一テーブルへの累積アクセス件数（一定期間内の
  合計）で閾値判定する。
- **C**: その他（自由記述）

[Answer]: 

---

### Q8. Frontend Components — `masterData/`機能のコンポーネント構成

U1-U4の`frontend-components.md`と同様の粒度で、本ユニットのフロントエンド機能を設計する。

- **A（推奨）**: 以下の画面・コンポーネントを`features/masterData/`配下に設計する（一般ユーザ
  向け、`/master-data`配下）。
  - `SchemaTableListPage`（接続選択→アクセス可能スキーマ/テーブル一覧、`MVP-10`。`DataTable`
    再利用）
  - `RecordListPage`（テーブル選択後のレコード一覧、ページング付き、`DataTable`拡張——インライン
    編集セル、行選択チェックボックス（削除用）、新規行追加ボタン（`canCreate`時のみ表示）、
    「反映」ボタン（変更差分を`MutationRequest`に集約して送信））
  - `FilterPanel`（UIモード/RAWモードのトグル、UIモード時は`UiCondition`/`UiSort`の組み立てUI
    （Q3のOperator一覧に対応するプルダウン等）、RAWモード時は`rawWhere`/`rawOrderBy`の
    テキスト入力欄）
  - `MutationResultDialog`（「反映」実行後の結果表示、失敗時は`MutationResult.errorMessage`を
    表示）
  - `AppRouter.tsx`に`/master-data`、`/master-data/:schema/:table`等のルートを追加する。
  - 権限に応じたUI制御: 読み取り専用カラムは編集不可の表示（Q6背景のとおり主キーなし
    テーブルは更新操作自体を無効化）、`canCreate`/`canDelete`が`false`の場合は該当ボタンを
    非表示にする（バックエンドの検証（Q5）は前提としつつ、UIでも事前に制御し無駄な
    全体拒否レスポンスを避ける）。
- **B**: 別の粒度・構成を希望する（自由記述）。

[Answer]: 

---

## Step 6: 成果物生成チェックリスト

- [ ] `aidlc-docs/construction/u5-master-data-maintenance/functional-design/domain-entities.md`
- [ ] `aidlc-docs/construction/u5-master-data-maintenance/functional-design/business-rules.md`
- [ ] `aidlc-docs/construction/u5-master-data-maintenance/functional-design/business-logic-model.md`
      （PBT-01: テスト可能な性質セクションを含む）
- [ ] `aidlc-docs/construction/u5-master-data-maintenance/functional-design/frontend-components.md`