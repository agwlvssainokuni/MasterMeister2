# U6 Code Generation Plan（変更要求: 接続コンテキストのグローバル化）

## 対象
`frontend-components.md`（U6 Functional Design改訂）に基づく、`QueryBuilderPage`のグローバル
接続コンテキスト参照への切替・接続切替時のモデルリセット・スキーマのURLパラメータ引き継ぎ
（受信/送信）、および重複実装だった`QueryBuilderMetadataService.listSelectableConnections`/
`QueryBuilderController#listSelectableConnections`（`GET /api/query-builder/connections`）の
削除（`ConnectionAccessService`へ一本化済み、U5と同様）。

## ステップ

- [ ] Step 1: バックエンド削除 — `QueryBuilderController.java`から`listSelectableConnections`
      メソッドと`GET /connections`エンドポイントを削除する。`QueryBuilderMetadataService.java`
      から`listSelectableConnections`メソッドと不要になる`ConnectionSummary`のimportを
      削除する。
- [ ] Step 2: バックエンドテスト削除 — `QueryBuilderControllerTest.java`から対応する2
      テストケースと不要importを削除する。
- [ ] Step 3: フロントエンド改修（接続コンテキスト） — `QueryBuilderPage.tsx`を改修する。
      `connectionId`/`connections`のページ内stateと接続選択`<select>`・
      `listSelectableConnections`呼び出しを削除し、`useConnection()`（U1）から
      `connectionId`を取得する。`connectionId`が`null`の場合は「接続が指定されていません。」を
      表示する。
- [ ] Step 4: フロントエンド改修（接続切替時のモデルリセット） — `connectionId`が初回マウント後に
      変化した場合、`schema`・`model`・`generatedSql`をリセットする`useEffect`を実装する
      （`useRef`で初回マウントかどうかを判定）。
- [ ] Step 5: フロントエンド改修（スキーマのURL引き継ぎ） — マウント時（初回のみ）、URL
      クエリパラメータ`schema`があれば`schema`の初期値としてプリフィルする。
      `handleNavigateToSave`/`handleNavigateToExecute`に`schema`パラメータを追加する。
- [ ] Step 6: フロントエンドAPI/型削除 — `queryBuilder/api.ts`から`listSelectableConnections`を、
      `queryBuilder/types.ts`から`ConnectionSummary`/`RdbmsType`（他で未使用の場合）を削除する。
- [ ] Step 7: フロントエンドテスト改修 — `QueryBuilderPage.test.tsx`を改修する。接続一覧取得
      モックを削除し`useConnectionStore.setState`ベースに変更する。接続切替時のリセット、
      スキーマのURLプリフィル、保存/実行遷移時のスキーマ引き継ぎのテストケースを追加する。
- [ ] Step 8: Documentation Generation — `aidlc-docs/construction/u6-query-builder/code/`配下の
      該当ドキュメントに本変更を追記する。
