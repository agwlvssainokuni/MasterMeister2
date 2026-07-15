# U5 Code Generation Plan（変更要求: 接続コンテキストのグローバル化）

## 対象
`frontend-components.md`（U5 Functional Design改訂）に基づく、`SchemaTableListPage`の
グローバル接続コンテキスト参照への切替、および重複実装だった
`MasterDataQueryService.listAccessibleConnections`/`MasterDataController#listAccessibleConnections`
（`GET /api/master-data/connections`）の削除（`ConnectionAccessService`へ一本化済み、
Application Design Q3=B）。

## ステップ

- [ ] Step 1: バックエンド削除 — `MasterDataController.java`から`listAccessibleConnections`
      メソッドと`GET /connections`エンドポイントを削除する。`MasterDataQueryService.java`から
      `listAccessibleConnections`メソッドと、それに伴い不要になる`ConnectionSummary`のimportを
      削除する（`rdbmsConnectionRepository`/`effectivePermissionResolver`フィールド自体は
      他メソッドで使用中のため維持）。
- [ ] Step 2: バックエンドテスト削除 — `MasterDataControllerTest.java`から
      `listAccessibleConnectionsReturnsOkForAuthenticatedUser`・
      `listAccessibleConnectionsReturnsUnauthorizedWhenNotAuthenticated`の2テストケースと、
      不要になった`ConnectionSummary`/`RdbmsType`のimportを削除する。
- [ ] Step 3: フロントエンド改修 — `SchemaTableListPage.tsx`を改修する。`connectionId`/
      `connections`のページ内stateと接続選択`<select>`を削除し、`useConnection()`
      （U1）から`connectionId`を取得する。`connectionId`が`null`の場合は
      「接続が指定されていません。」を表示する。`connectionId`変化時に`schema`/`tables`を
      リセットして`listAccessibleSchemas`を呼び出す`useEffect`を追加する。
- [ ] Step 4: フロントエンドAPI/型削除 — `masterData/api.ts`から`listAccessibleConnections`を、
      `masterData/types.ts`から`ConnectionSummary`/`RdbmsType`（他で未使用）を削除する。
- [ ] Step 5: フロントエンドテスト改修 — `SchemaTableListPage.test.tsx`を改修する。
      `listAccessibleConnections`のモックを削除し、`useConnectionStore.setState(...)`で
      `connectionId`を直接設定する形に変更する。「`connectionId`が`null`の場合のメッセージ
      表示」テストケースを追加する。
- [ ] Step 6: Documentation Generation — `aidlc-docs/construction/u5-master-data-maintenance/
      code/`配下の該当ドキュメントに本変更を追記する。
