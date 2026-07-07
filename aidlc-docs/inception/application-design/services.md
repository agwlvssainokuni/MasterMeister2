# services.md — サービス定義・オーケストレーション

`components.md` / `component-methods.md` で定義した各サービスが、主要な業務フロー
（ユーザストーリーのまとまり）の中でどう連携するかを整理する。個々のメソッドの
詳細仕様ではなく、「どのサービスが、どの順で、何を呼ぶか」というオーケストレーション
の観点に絞る。

---

## フロー1: ユーザ登録〜承認〜ログイン（MVP-1〜MVP-6）

1. `UserRegistrationService.requestRegistration(email)`
   - 内部で `RegistrationTokenService.issueToken(email, expiry)` を呼び出し（新規メールアドレスの
     場合のみ）
   - `MailService.sendRegistrationConfirmation(email, token)` を呼び出す
   - 呼び出し結果はメールアドレスの新規/既存を問わず同一（列挙攻撃対策）
2. `UserRegistrationService.completeRegistration(token, rawPassword)`
   - `RegistrationTokenService.validate(token)` でトークンを検証
   - パスワードをハッシュ化してユーザを「承認待ち」状態で永続化
3. 管理者操作: `UserRegistrationService.listPendingUsers()` → 一覧表示
4. 管理者操作: `UserRegistrationService.approveUser(...)` / `rejectUser(...)`
   - 完了後 `MailService.sendApprovalResult(email, approved)` を呼び出す
   - `AuditLogService.record(...)` を明示的に呼び出す（管理操作の記録）
5. `AuthenticationService.login(email, rawPassword)`
   - 承認済みユーザのみ認証成功。失敗時は `AuditLogService.record(...)` でログイン失敗を記録
   - 成功時 `JwtTokenProvider.generateToken(...)` でJWT発行

---

## フロー2: 対象RDBMS接続登録〜スキーマ取り込み〜権限設定（MVP-7〜MVP-9, ADM-1〜ADM-5）

1. `RdbmsConnectionService.createConnection(config)`
   - `AuditLogService.record(...)` で接続設定変更を記録
2. `SchemaImportService.importSchema(connectionId)`
   - `ConnectionPoolRegistry.getDataSource(connectionId)` で対象RDBMSへの接続を取得
   - `DialectStrategyFactory.resolve(dbType)` で方言差異を吸収しつつメタデータを読み取る
   - 結果を内部DBへ保存し、`AuditLogService.record(...)` で取り込み結果（成功/失敗）を記録
3. 管理者操作: `GroupService.createGroup(...)` / `addUserToGroup(...)`（ADM-1）
4. 管理者操作: `PermissionAssignmentService.setPermission(...)` /
   `setAuxPermission(...)`（ユーザ or グループ単位、MVP-9 / ADM-2）
   - `SchemaQueryService.getTableDetail(...)` で対象テーブル/カラムの妥当性を検証してから設定
   - `AuditLogService.record(...)` で権限変更を記録
5. `PermissionAssignmentService.exportPermissionsAsYaml(...)` /
   `importPermissionsFromYaml(...)`（ADM-4, ADM-5）
   - インポート時は形式検証に失敗したら反映せずエラー、成功時のみ `AuditLogService.record(...)`

---

## フロー3: マスタデータ閲覧・絞り込み（MVP-10, MVP-11, GEN-1, GEN-2）

1. `MasterDataQueryService.listAccessibleTables(userId, connectionId, schema)`
   - 内部で `EffectivePermissionResolver.listAccessibleTables(userId, connectionId, schema)` を
     呼び出し、`SchemaQueryService.listTables(connectionId, schema)` の結果をフィルタする
2. `MasterDataQueryService.listRecords(userId, connectionId, schema, table, criteria, page)`
   - UI組立条件の場合: `EffectivePermissionResolver.resolveEffectiveColumnPermissions(...)` で
     R以上のカラムのみ絞り込み・ソート対象にする
   - 手入力WHERE/ORDER BYの場合: カラム権限フィルタを適用せずそのまま実行
     （GEN-2、設計上の意図的例外。表示列自体は読み取り権限フィルタの対象のまま）
   - 大量データ取得時（閾値超過）は `AuditLogService.record(...)` を呼び出す

---

## フロー4: マスタデータ編集・作成・削除（統一API、GEN-3〜GEN-5）

1. フロントエンドは編集・作成・削除の全操作をまとめて
   `MasterDataMutationService.applyChanges(userId, connectionId, schema, table, request)` へ送信する
2. `MasterDataMutationService` は実行前に、リクエスト内の全操作について
   `EffectivePermissionResolver` を用いて可否を検証する:
   - 更新対象カラムは `resolveEffectiveColumnPermissions(...)` がRU以上であること
   - 作成操作は `canCreate(...)` がtrueであること
   - 削除操作は `canDelete(...)` がtrueであること
3. 検証を通過した操作のみ、対象RDBMSへの単一トランザクション内でまとめて実行する
   （`ConnectionPoolRegistry` から取得した `NamedParameterJdbcTemplate` を使用）
4. いずれか1件でも失敗した場合は全体をロールバックし、`SQLException` 由来の概要
   メッセージのみを `MutationResult.errorMessage` に設定する（Question 8 = B）
5. 成功・失敗いずれの場合も `AuditLogService.record(...)` でデータ更新操作を記録する

---

## フロー5: クエリビルダーでのSQL作成・保存・実行（GEN-6〜GEN-12）

1. `QueryBuilderMetadataService.listSelectableTables/Columns(...)`
   - `EffectivePermissionResolver` による読み取り権限フィルタを適用
2. `SqlGenerationService.generate(model)` でUI入力からSQLを生成
3. 生成したSQLは以下のいずれかに連携する:
   - `SavedQueryService.saveQuery(...)` へ渡して保存（GEN-10）
   - `QueryExecutionService.executeAdhocSql(...)` へ渡して直接実行（GEN-13経由）
4. 他画面（クエリ実行・クエリ履歴）からクエリビルダーへ遷移する場合:
   - `SqlParsingService.parse(rawSql)` でSQLを解析し、`QueryBuilderModel` に変換して
     各タブへ反映する（GEN-9）。解析不能な場合は `ParseResult.notice` をUIに表示する

---

## フロー6: クエリ実行・履歴（GEN-13〜GEN-16）

1. `QueryExecutionService.executeAdhocSql(...)` または `executeSavedQuery(...)`
   - 読み取り専用チェック（DML/DDLキーワード検知等の簡易検証）
   - `:param` 形式パラメータの自動検出、`NamedParameterJdbcTemplate` へのバインド
   - `ConnectionPoolRegistry` から取得したJdbcTemplateで実行
2. 実行完了後、同一トランザクション外で以下を明示的に呼び出す（順不同）:
   - `QueryHistoryService.recordExecution(...)`（履歴機能向け、GEN-14のAC）
   - `AuditLogService.record(...)`（監査ログ向け、6.1のAC）
3. `QueryHistoryService.listHistory(...)` で履歴一覧・絞り込みを提供（GEN-15）
4. 履歴の1件から `QueryExecutionService` / `SavedQueryService` / クエリビルダー（`SqlParsingService`
   経由）への遷移をフロントエンドが行う（GEN-16、SQL・パラメータを引き継ぐ）

---

## フロー7: 監査ログ閲覧（ADM-6）

1. `AuditLogService.search(criteria, page)`
   - Controller層で管理者ロールチェック（アプリケーションレベルRBAC、対象RDBMSの
     権限モデルとは別軸）を行った上で呼び出す
   - 日時・ユーザ・操作種別での絞り込みに対応

---

## サービス間依存の原則

- 各機能サービスは、他パッケージの機能に対しては原則として当該パッケージの
  Facade的サービス（`EffectivePermissionResolver`, `SchemaQueryService`,
  `ConnectionPoolRegistry`, `AuditLogService`, `MailService` 等）のみを呼び出す。
  他パッケージのRepository/Entityへ直接アクセスしない。
- トランザクション境界は各サービスのpublicメソッド単位（Spring `@Transactional`）を
  基本とする。ただし `MasterDataMutationService.applyChanges` は対象RDBMS側のトランザクションを
  明示的に制御する（内部DBのJPAトランザクションとは別管理）。
- 監査ログ・クエリ履歴の記録は、対象操作のトランザクションとは独立して（成功/失敗いずれの
  結果に対しても）明示的に呼び出す。