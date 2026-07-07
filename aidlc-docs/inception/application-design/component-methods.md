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
DbType getDbType()
String quoteIdentifier(String rawName)
String buildPagingClause(int limit, int offset)
String buildNullsOrderingClause(SortDirection direction, NullsOrder nullsOrder)
SchemaResolutionMode getSchemaResolutionMode()   // DB種別ごとのスキーマ/カタログ解釈差異
```

### `DialectStrategyFactory`
```
DialectStrategy resolve(DbType dbType)
```

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
List<TableMetadata> listTables(Long connectionId)
TableDetail getTableDetail(Long connectionId, Long tableId)
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
void setPrimaryPermission(Long connectionId, PrincipalRef principal, PermissionScope scope, PrimaryLevel level)
   // PermissionScope = (SCHEMA|TABLE|COLUMN, scopeRefId)
   // PrimaryLevel = NONE | R | RU
void setAuxiliaryPermission(Long connectionId, PrincipalRef principal, PermissionScope scope, AuxType auxType, boolean granted)
   // PermissionScope は SCHEMA|TABLE のみ許可（COLUMNは不可）
   // AuxType = CREATE | DELETE
byte[] exportPermissionsAsYaml(Long connectionId)
ImportResult importPermissionsFromYaml(Long connectionId, byte[] yamlContent)
   // 形式不正時: PermissionYamlFormatException（反映せずエラー表示、監査ログに記録）
```
`PrincipalRef` = `(PrincipalType[USER|GROUP], principalId)`

### `EffectivePermissionResolver`
```
PrimaryLevel resolveEffectiveTableLevel(Long userId, Long connectionId, Long tableId)
Map<Long, PrimaryLevel> resolveEffectiveColumnLevels(Long userId, Long connectionId, Long tableId)
boolean canCreate(Long userId, Long connectionId, Long tableId)
boolean canDelete(Long userId, Long connectionId, Long tableId)
Set<Long> listAccessibleTableIds(Long userId, Long connectionId)
   // resolveEffectiveTableLevel != NONE となるテーブルの集合
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
List<TableSummary> listAccessibleTables(Long connectionId, Long userId)
PageResult<RecordDto> listRecords(Long connectionId, Long tableId, Long userId, FilterCriteria criteria, PageRequest page)
   // criteria は UI組立条件（読み取り権限のあるカラムのみ選択可）と
   // 手入力WHERE/ORDER BY（権限フィルタ対象外、GEN-2）の両対応
```

### `MasterDataMutationService`
```
MutationResult applyChanges(Long connectionId, Long tableId, Long userId, MutationRequest request)
   // MutationRequest = { List<RecordCreate>, List<RecordUpdate>, List<RecordDelete> }
   // 単一トランザクションで実行し、いずれか1件でも失敗した場合は全体ロールバック
   // 失敗時、MutationResult.errorMessage には SQLException 由来の概要メッセージのみを設定
   //（行・カラム単位の詳細特定は行わない、Question 8 = B）
```

---

## querybuilder

### `QueryBuilderMetadataService`
```
List<TableRef> listSelectableTables(Long connectionId, Long userId)
List<ColumnRef> listSelectableColumns(Long connectionId, Long userId, String tableAlias)
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
Long saveQuery(Long connectionId, Long userId, String name, String sql, Visibility visibility)
void updateQuery(Long savedQueryId, Long userId, String name, String sql, Visibility visibility)
   // 作成者以外の場合: PermissionDeniedException（GEN-12）
List<SavedQuerySummary> listQueries(Long connectionId, Long userId)
   // Public全件 + 自分のPrivateのみ
SavedQueryDetail getQuery(Long savedQueryId, Long userId)
   // Private かつ作成者以外の場合: PermissionDeniedException
```

---

## queryexecution

### `QueryExecutionService`
```
QueryResult executeAdhocSql(Long connectionId, Long userId, String sql, Map<String, Object> params, PagingOption paging)
   // 読み取り専用チェックに失敗した場合: ReadOnlyViolationException
QueryResult executeSavedQuery(Long connectionId, Long userId, Long savedQueryId, Map<String, Object> params, PagingOption paging)
   // 実行時はSQL編集不可（GEN-11）。SavedQueryService経由でSQL本体を取得する
```
両メソッドとも内部で `QueryHistoryService.recordExecution(...)` と
`AuditLogService.record(...)` を明示的に呼び出す。

---

## queryhistory

### `QueryHistoryService`
```
void recordExecution(ExecutionRecord record)
   // ExecutionRecord = { connectionId, userId, sql, params, resultCount, elapsedMillis,
   //                      executedAt, Optional<savedQueryId>, Optional<executionCount> }
PageResult<HistoryEntry> listHistory(Long connectionId, Long userId, HistoryFilterCriteria criteria, PageRequest page)
   // HistoryFilterCriteria = { dateRange, executorScope[ALL|SELF], sqlTextSearch }
```

---

## audit

### `AuditLogService`
```
void record(AuditEventType type, Long actorUserId, String targetResource, AuditResult result, Map<String, Object> details)
PageResult<AuditLogEntry> search(AuditLogFilterCriteria criteria, PageRequest page)
   // 呼び出しはController層で管理者ロールチェック済みであることを前提とする
```

---

## mail

### `MailService`
```
void sendRegistrationConfirmation(String email, String registrationToken)
void sendApprovalResult(String email, boolean approved)
```