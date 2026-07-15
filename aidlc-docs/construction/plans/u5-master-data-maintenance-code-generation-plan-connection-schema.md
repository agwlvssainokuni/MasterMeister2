# U5 Code Generation Plan（変更要求: 接続コンテキストのグローバル化）

## 対象
`frontend-components.md`（U5 Functional Design改訂）に基づく、`SchemaTableListPage`の
グローバル接続コンテキスト参照への切替、および重複実装だった
`MasterDataQueryService.listAccessibleConnections`/`MasterDataController#listAccessibleConnections`
（`GET /api/master-data/connections`）の削除（`ConnectionAccessService`へ一本化済み、
Application Design Q3=B）。

## ステップ

- [x] Step 1: バックエンド削除 — `MasterDataController.java`・`MasterDataQueryService.java`から
      `listAccessibleConnections`/`GET /connections`を削除した。
- [x] Step 2: バックエンドテスト削除 — `MasterDataControllerTest.java`から2テストケースと
      不要importを削除した。`./gradlew test --tests "cherry.mastermeister.masterdata.*"`成功。
- [x] Step 3: フロントエンド改修 — `SchemaTableListPage.tsx`を`useConnection()`参照に改修した。
- [x] Step 4: フロントエンドAPI/型削除 — `api.ts`/`types.ts`から該当項目を削除した。
- [x] Step 5: フロントエンドテスト改修 — `SchemaTableListPage.test.tsx`を改訂した（4件）。
- [x] Step 6: Documentation Generation — `api-layer-summary.md`・`frontend-summary.md`・
      `testing-summary.md`を更新した。

全テスト実行確認: `npx vitest run`（フロントエンド全体）271/271件成功、
`./gradlew test --tests "cherry.mastermeister.masterdata.*"`成功、
`./gradlew build -x test`・`npx tsc -b`・`npx oxlint`いずれもエラーなし。
