# U3 Code Generation Plan（変更要求: 接続コンテキストのグローバル化）

## 対象
`business-rules.md` 1.7・`business-logic-model.md` フロー6（U3 Functional Design改訂）に基づく、
`ConnectionAccessService`の新規実装・エンドポイント追加・フロントエンドapi.ts追加。
`masterdata`/`querybuilder`の既存重複コード（`listAccessibleConnections`・専用エンドポイント）の
削除は、それぞれのユニット（U5/U6）のCode Generationで行う（本ステップの対象外）。

## ステップ

- [x] Step 1: Business Logic Generation — `ConnectionAccessService.java`を新規作成した。
- [x] Step 2: Business Logic Unit Testing — `ConnectionAccessServiceTest.java`を新規作成した
      （PBT性質P13、既存P12との番号衝突を避けて採番）。テスト成功を確認済み。
- [x] Step 3: API Layer Generation — `RdbmsConnectionController.java`に`GET /accessible`を
      追加した。`SecurityConfig.java`に`requestMatchers(HttpMethod.GET,
      "/api/rdbms-connections/accessible").authenticated()`を、既存の
      `hasRole("ADMIN")`ルールより前に追加した。
- [x] Step 4: API Layer Unit Testing — `RdbmsConnectionControllerTest.java`に2件追加した
      （一般ユーザー200、未認証401）。テスト成功を確認済み（計20件）。
- [x] Step 5: Frontend Components Generation — `frontend/src/features/rdbmsConnection/api.ts`に
      `listAccessibleConnections()`を追加した。
- [x] Step 6: Frontend Components Unit Testing — `api.test.ts`に1件追加した。テスト成功を
      確認済み（計7件）。
- [x] Step 7: Documentation Generation — `business-logic-summary.md`・`api-layer-summary.md`・
      `frontend-summary.md`・`testing-summary.md`に本変更を追記した。