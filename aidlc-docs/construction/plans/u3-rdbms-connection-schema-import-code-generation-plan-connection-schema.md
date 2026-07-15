# U3 Code Generation Plan（変更要求: 接続コンテキストのグローバル化）

## 対象
`business-rules.md` 1.7・`business-logic-model.md` フロー6（U3 Functional Design改訂）に基づく、
`ConnectionAccessService`の新規実装・エンドポイント追加・フロントエンドapi.ts追加。
`masterdata`/`querybuilder`の既存重複コード（`listAccessibleConnections`・専用エンドポイント）の
削除は、それぞれのユニット（U5/U6）のCode Generationで行う（本ステップの対象外）。

## ステップ

- [ ] Step 1: Business Logic Generation — `backend/src/main/java/cherry/mastermeister/
      rdbmsconnection/ConnectionAccessService.java`を新規作成する。`EffectivePermissionResolver`
      （`permission`パッケージ）と`RdbmsConnectionRepository`に依存し、`listAccessibleConnections
      (Long userId)`を実装する（`masterdata.MasterDataQueryService.listAccessibleConnections`と
      同一ロジック）。
- [ ] Step 2: Business Logic Unit Testing — `backend/src/test/java/cherry/mastermeister/
      rdbmsconnection/ConnectionAccessServiceTest.java`を新規作成する（PBT性質P12の検証を含む）。
- [ ] Step 3: API Layer Generation — `RdbmsConnectionController.java`に`GET /accessible`
      （`listAccessibleConnections`）を追加する。`SecurityConfig.java`に
      `requestMatchers("/api/rdbms-connections/accessible").authenticated()`を、既存の
      `requestMatchers("/api/rdbms-connections/**").hasRole("ADMIN")`より前に追加する
      （Spring Securityは最初にマッチしたルールを採用するため順序が重要）。
- [ ] Step 4: API Layer Unit Testing — `RdbmsConnectionControllerTest.java`に、一般ユーザーの
      認証情報でも200が返ること・管理者ロール不要であることを検証するテストケースを追加する。
- [ ] Step 5: Frontend Components Generation — `frontend/src/features/rdbmsConnection/api.ts`に
      `listAccessibleConnections(): Promise<ConnectionSummary[]>`を追加する
      （`GET /api/rdbms-connections/accessible`）。
- [ ] Step 6: Frontend Components Unit Testing — `frontend/src/features/rdbmsConnection/
      api.test.ts`にテストケースを追加する。
- [ ] Step 7: Documentation Generation — `aidlc-docs/construction/u3-rdbms-connection-schema-
      import/code/backend-summary.md`・`frontend-summary.md`に本変更（新規ファイル・変更ファイル）
      を追記する。