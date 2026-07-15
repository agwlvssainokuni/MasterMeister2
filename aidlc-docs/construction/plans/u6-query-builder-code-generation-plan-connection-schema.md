# U6 Code Generation Plan（変更要求: 接続コンテキストのグローバル化）

## 対象
`frontend-components.md`（U6 Functional Design改訂）に基づく、`QueryBuilderPage`のグローバル
接続コンテキスト参照への切替・接続切替時のモデルリセット・スキーマのURLパラメータ引き継ぎ
（受信/送信）、および重複実装だった`QueryBuilderMetadataService.listSelectableConnections`/
`QueryBuilderController#listSelectableConnections`（`GET /api/query-builder/connections`）の
削除（`ConnectionAccessService`へ一本化済み、U5と同様）。

## ステップ

- [x] Step 1: バックエンド削除 — `QueryBuilderController.java`・`QueryBuilderMetadataService.java`
      から`listSelectableConnections`/`GET /connections`を削除した
      （`rdbmsConnectionRepository`フィールド自体も削除）。
- [x] Step 2: バックエンドテスト削除 — `QueryBuilderControllerTest.java`から2テストケース、
      `QueryBuilderMetadataServiceTest.java`のコンストラクタ呼び出しを修正した。
      `./gradlew test --tests "cherry.mastermeister.querybuilder.*"`成功。
- [x] Step 3: フロントエンド改修（接続コンテキスト） — `QueryBuilderPage.tsx`を`useConnection()`
      参照に改修した。
- [x] Step 4: フロントエンド改修（接続切替時のモデルリセット） — `useRef`による初回マウント
      判定を用いた`useEffect`を実装した。
- [x] Step 5: フロントエンド改修（スキーマのURL引き継ぎ） — 受信（初回マウント時プリフィル）・
      送信（`handleNavigateToSave`/`handleNavigateToExecute`）両方向を実装した。
- [x] Step 6: フロントエンドAPI/型削除 — `api.ts`/`types.ts`から該当項目を削除した。
- [x] Step 7: フロントエンドテスト改修 — `QueryBuilderPage.test.tsx`を改訂した（6件→11件）。
- [x] Step 8: Documentation Generation — `api-layer-summary.md`・`frontend-summary.md`・
      `testing-summary.md`を更新した。

全テスト実行確認: `npx vitest run`（フロントエンド全体）274/274件成功、
`./gradlew test --tests "cherry.mastermeister.querybuilder.*"`成功、
`./gradlew build -x test`・`npx tsc -b`・`npx oxlint`いずれもエラーなし。
