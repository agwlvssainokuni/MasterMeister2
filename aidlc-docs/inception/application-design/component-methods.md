# component-methods.md — メソッドシグネチャ（入出力型レベル）

`components.md` に列挙した各サービスの主要メソッドを、入出力の型レベルで定義する。
詳細な業務ロジック・検証条件の実装（例外種別の細分化、バリデーション項目の網羅等）は
CONSTRUCTION フェーズの Functional Design（ユニット単位）で確定する。ただし
`EffectivePermissionResolver` の判定ロジックは Application Design の確認質問（Question 1, 2, 9）
で確定した中核ルールのため、本書でも判定条件の要旨を記載する。

型表記は Java 風の疑似シグネチャとする（`Optional<T>` は「値が存在しない場合がある」ことを表す）。

---

## common

### `DialectStrategy`（インターフェース）
```
RdbmsType getRdbmsType()
String quoteIdentifier(String rawName)
String buildPagingClause(int limit, int offset)
String buildNullsOrderingClause(SortDirection direction, NullsOrder nullsOrder)
SchemaResolutionMode getSchemaResolutionMode()   // DB種別ごとのスキーマ/カタログ解釈差異
```

### `DialectStrategyFactory`
```
DialectStrategy resolve(RdbmsType rdbmsType)
```
（`RdbmsType`は対象RDBMS種別を表すenum（`MYSQL`/`MARIADB`/`POSTGRESQL`/`H2`）。
U1のNFR Design（`nfr-design-patterns.md` 2.1、`logical-components.md`）で確定した
名称に統一——旧`DbType`/`DbmsType`は同一ファイル内で名称が揺れていた暫定表記）

---

## auth

### `AuthenticationService`
```
AuthToken login(String email, String rawPassword)
   // 失敗時: AuthenticationFailedException（監査ログにログイン失敗を記録）
void logout(String rawToken)
   // JWTはステートレスのため、明示的な失効はせず監査ログ記録のみ行う
```

### `JwtTokenProvider`
```
String generateToken(Long userId, Role role, Duration expiry)
JwtClaims parseAndValidate(String rawToken)   // 失敗時: InvalidTokenException
```

---

## userregistration

### `UserRegistrationService`
```
void requestRegistration(String email)
   // 既存/未登録を問わず常に同一の呼び出し結果（例外を投げない）とし、
   // 列挙攻撃対策をコントローラ層のレスポンス生成でも一貫させる
void completeRegistration(String registrationToken, String rawPassword)
   // 失敗時: TokenExpiredException / TokenNotFoundException
void approveUser(Long adminUserId, Long targetUserId)
void rejectUser(Long adminUserId, Long targetUserId)
List<PendingUserSummary> listPendingUsers()
```

### `RegistrationTokenService`
```
String issueToken(String email, Duration expiry)
RegistrationTokenStatus validate(String token)   // VALID / EXPIRED / NOT_FOUND
```

---

## rdbmsconnection

### `RdbmsConnectionService`
```
Long createConnection(ConnectionConfig config)
void updateConnection(Long connectionId, ConnectionConfig config)
ConnectionTestResult testConnection(ConnectionConfig config)
List<ConnectionSummary> listConnections()
ConnectionDetail getConnection(Long connectionId)
```

### `ConnectionPoolRegistry`
```
DataSource getDataSource(Long connectionId)
NamedParameterJdbcTemplate getJdbcTemplate(Long connectionId)
void invalidate(Long connectionId)   // 接続設定変更時にプールを再構築
```

### `ConnectionAccessService`（2026-07-15変更要求で追加）
```
List<ConnectionSummary> listAccessibleConnections(Long userId)
   // 全接続のうち、EffectivePermissionResolver.listAccessibleSchemas(userId, connectionId)が
   // 1件以上返す接続のみを返す（masterdata/querybuilderが個別に重複実装していたロジックを移設）
```

---

## schema

### `SchemaImportService`
```
SchemaImportResult importSchema(Long connectionId)
   // DialectStrategy を用いて対象RDBMSのメタデータを読み取り、内部DBへ保存する
   // 結果（成功/失敗、取り込んだテーブル数等）は audit.AuditLogService へも明示連携される
```

### `SchemaQueryService`
```
List<String> listSchemas(Long connectionId)
List<TableMetadata> listTables(Long connectionId, String schema)
TableDetail getTableDetail(Long connectionId, String schema, String table)
   // カラム一覧・型・コメント・主キー構成（複合主キー対応）を含む
```

---

## permission

### `GroupService`
```
Long createGroup(String name)
void addUserToGroup(Long groupId, Long userId)
void removeUserFromGroup(Long groupId, Long userId)
List<GroupSummary> listGroups()
List<UserSummary> listGroupMembers(Long groupId)
```

### `PermissionAssignmentService`
```
void setPermission(PrincipalRef principal, Long connectionId, String schema, Optional<String> table, Optional<String> column, Permission permission)
   // Permission = NONE | READ | UPDATE
void setAuxPermission(PrincipalRef principal, Long connectionId, String schema, Optional<String> table, AuxPermissionType auxType, boolean granted)
   // AuxPermissionType = CREATE | DELETE
byte[] exportPermissionsAsYaml(Long connectionId)
ImportResult importPermissionsFromYaml(Long connectionId, byte[] yamlContent)
   // 形式不正時: PermissionYamlFormatException（反映せずエラー表示、監査ログに記録）
```
`PrincipalRef` = `(PrincipalType[USER|GROUP], principalId)`

### `EffectivePermissionResolver`
```
Permission resolveEffectiveTablePermission(Long userId, Long connectionId, String schema, String table)
Map<String, Permission> resolveEffectiveColumnPermissions(Long userId, Long connectionId, String schema, String table)
boolean canCreate(Long userId, Long connectionId, String schema, String table)
boolean canDelete(Long userId, Long connectionId, String schema, String table)
List<String> listAccessibleSchemas(Long userId, Long connectionId)
   // 配下に1つ以上アクセス可能テーブル（resolveEffectiveTablePermission != NONE）を持つ
   // スキーマのリスト
List<String> listAccessibleTables(Long userId, Long connectionId, String schema)
   // resolveEffectiveTablePermission != NONE となるテーブルのリスト
```

**判定ロジックの要旨（Question 1, 2, 9 で確定）**:
1. 主権限はスキーマ→テーブル→カラムの順に継承され、下位の明示設定が上位を上書きする
   （下位に設定がなければ上位の値をそのまま継承）。
2. 補助権限（C/D）も同じ継承・上書き規則をスキーマ→テーブルの2階層で適用する。
3. 複数グループに所属する場合、グループごとに1.の解決を行った上で、グループ間は
   「最も緩い権限」（主権限は なし＜R＜RUのうち最大、補助権限はOR）で合成する。
4. ユーザ個別設定が存在する階層は、3.で合成したグループ結果を上書きする
   （個別設定がない階層は合成結果を継続適用＝カラム単位までの部分上書きが起こり得る）。
5. `canCreate`: 対象テーブルの補助権限Cが有効、かつ主キーを構成する**全カラム**の主権限が
   RU（複合主キーはAND条件）。ただし主キーを持たないテーブルは、補助権限Cが有効であれば
   主キー列の条件を課さず作成のみ許可する（例外規定）。
6. `canDelete`: 対象テーブルの補助権限Dが有効、かつ主キーを構成する全カラムの主権限が
   R以上（RまたはRU、AND条件）。主キーを持たないテーブルは常に不可。

---

## masterdata

### `MasterDataQueryService`
```
List<String> listAccessibleSchemas(Long userId, Long connectionId)
List<TableSummary> listAccessibleTables(Long userId, Long connectionId, String schema)
PageResult<RecordDto> listRecords(Long userId, Long connectionId, String schema, String table, FilterCriteria criteria, PageRequest page)
   // criteria は UI組立条件（読み取り権限のあるカラムのみ選択可）と
   // 手入力WHERE/ORDER BY（権限フィルタ対象外、GEN-2）の両対応
```

### `MasterDataMutationService`
```
MutationResult applyChanges(Long userId, Long connectionId, String schema, String table, MutationRequest request)
   // MutationRequest = { List<RecordCreate>, List<RecordUpdate>, List<RecordDelete> }
   // 単一トランザクションで実行し、いずれか1件でも失敗した場合は全体ロールバック
   // 失敗時、MutationResult.errorMessage には SQLException 由来の概要メッセージのみを設定
   //（行・カラム単位の詳細特定は行わない、Question 8 = B）
```

---

## querybuilder

### `QueryBuilderMetadataService`
```
List<String> listSelectableSchemas(Long userId, Long connectionId)
List<TableRef> listSelectableTables(Long userId, Long connectionId, String schema)
List<ColumnRef> listSelectableColumns(Long userId, Long connectionId, String schema, String table)
```

### `SqlGenerationService`（抽象境界、実装方式はNFR Designで決定）
```
GeneratedSql generate(QueryBuilderModel model)
   // QueryBuilderModel = SELECT/FROM/JOIN/WHERE/GROUP BY/HAVING/ORDER BY/LIMIT OFFSET の
   // タブ構成を表す入力モデル
```

### `SqlParsingService`（抽象境界、実装方式はNFR Designで決定）
```
ParseResult parse(String rawSql)
   // ParseResult = { boolean fullyParsed, Optional<QueryBuilderModel> model, Optional<String> notice }
   // 解析不能な複雑SQLの場合は fullyParsed=false, notice にその旨を設定（GEN-9）
```

---

## savedquery

### `SavedQueryService`
```
Long saveQuery(Long userId, Long connectionId, String name, String sql, Visibility visibility)
void updateQuery(Long userId, Long savedQueryId, String name, String sql, Visibility visibility)
   // 作成者以外の場合: PermissionDeniedException（GEN-12）
List<SavedQuerySummary> listQueries(Long userId, Long connectionId)
   // Public全件 + 自分のPrivateのみ
SavedQueryDetail getQuery(Long userId, Long savedQueryId)
   // Private かつ作成者以外の場合: PermissionDeniedException
```
（2026-07-15変更要求: スキーマは保存対象に含まない。SQLはスキーマ非依存で再利用可能という
設計を維持するため）

---

## queryexecution

### `QueryExecutionService`
```
QueryResult executeAdhocSql(Long userId, Long connectionId, String schema, String sql, Map<String, Object> params, PagingOption paging)
   // 読み取り専用チェックに失敗した場合: ReadOnlyViolationException
   // schemaがEffectivePermissionResolver.listAccessibleSchemas(userId, connectionId)に
   // 含まれない場合: PermissionDeniedException（2026-07-15変更要求）
QueryResult executeSavedQuery(Long userId, Long connectionId, String schema, Long savedQueryId, Map<String, Object> params, PagingOption paging)
   // 実行時はSQL編集不可（GEN-11）。SavedQueryService経由でSQL本体を取得する
   // schemaの検証はexecuteAdhocSqlと同様（2026-07-15変更要求）
```
両メソッドとも内部で `QueryHistoryService.recordExecution(...)` と
`AuditLogService.record(...)` を明示的に呼び出す。
（2026-07-15変更要求）`schema`検証後、`SCHEMA_BASED`方言（PostgreSQL/H2）の場合のみ、
検証済みスキーマ名を用いて実行直前に`SET search_path`を発行してからSQLを実行する。
`CATALOG_BASED`方言（MySQL/MariaDB）はスキーマが`databaseName`で既に固定されているため
`SET`は発行しない。

---

## queryhistory

### `QueryHistoryService`
```
void recordExecution(ExecutionRecord record)
   // ExecutionRecord = { userId, connectionId, schema, sql, params, resultCount, elapsedMillis,
   //                      executedAt, Optional<savedQueryId>, Optional<executionCount> }
   // schema: 2026-07-15変更要求で追加（NOT NULL、実行時に実際に対象としたスキーマを記録）
PageResult<HistoryEntry> listHistory(Long userId, Long connectionId, HistoryFilterCriteria criteria, PageRequest page)
   // HistoryFilterCriteria = { dateRange, executorScope[ALL|SELF], sqlTextSearch }
   // HistoryEntry には schema フィールドを含む（2026-07-15変更要求）
```

---

## audit

### `AuditLogService`
```
void record(EventCategory eventCategory, EventType eventType, Long userId, Long connectionId, Result result, String targetDescription, String summaryMessage)
PageResult<AuditLog> search(AuditLogFilterCriteria criteria, PageRequest page)
   // 呼び出しはController層で管理者ロールチェック済みであることを前提とする
```
（`EventCategory`/`EventType`/`Result`/`AuditLog`は`u1-platform-foundation/functional-design/domain-entities.md`
参照。U1のFunctional Designで確定した実フィールドに合わせてシグネチャを更新——旧`AuditEventType`/
`AuditResult`/`AuditLogEntry`型は未定義のプレースホルダだったため置き換え）

---

## mail

### `MailService`
```
void send(MailNotificationType type, String email, Map<String, Object> variables)
   // MailNotificationType = REGISTRATION_CONFIRMATION | REGISTRATION_APPROVED | REGISTRATION_REJECTED
   // variables はThymeleafテンプレートへ埋め込む変数（宛先名、リンクURL、有効期限等）
```
（`MailNotificationType`は`u1-platform-foundation/functional-design/domain-entities.md`参照。
U1のFunctional Design（Thymeleafテンプレート方式、`business-logic-model.md`フロー3）に
合わせて汎用`send(type, ...)`メソッドへ置き換え——旧`sendRegistrationConfirmation`/
`sendApprovalResult`はテンプレート方式決定前の型別メソッド案だった）