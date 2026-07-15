# U1 Code Generation Plan（変更要求: 接続コンテキストのグローバル化）

## 対象
`business-logic-model.md` フロー5・`frontend-components.md`（U1 Functional Design改訂）に基づく、
`connectionStore`・`useConnection`・`AppLayout`のグローバル接続セレクタ実装。バックエンド変更は
U1になし（U3で完了済み）。

## ステップ

- [ ] Step 1: `frontend/src/store/connectionStore.ts`を新規作成する。`authStore.ts`と同じ
      zustand + `persist`（`sessionStorage`）パターン。`ConnectionSummary`型は
      `features/rdbmsConnection/types.ts`からimportする（重複定義しない）。
- [ ] Step 2: `frontend/src/hooks/useConnection.ts`を新規作成する（`useAuth.ts`と同じ形の
      薄いラッパーフック）。
- [ ] Step 3: `frontend/src/components/AppLayout.tsx`を改修する。マウント時、
      `connections.length === 0`かつ認証済みなら`listAccessibleConnections()`
      （`features/rdbmsConnection/api.ts`、U3で追加済み）を呼び出し`setConnections`する。
      常設の接続選択`<select>`をナビゲーション内に追加する。接続選択変更時は
      `setConnectionId`を呼び出す。ログアウト時は`connectionStore`もクリアする。
- [ ] Step 4: `AppLayout.tsx`に、接続切替時のナビゲーション（現在のパスが
      `/master-data/:connectionId/:schema/:table`パターンに一致する場合のみ`/master-data`へ
      遷移）を実装する。初回マウント時（ページリロード等でsessionStorageから復元された場合）は
      発火しないよう、`useRef`でスキップする。
- [ ] Step 5: `AppLayout.test.tsx`にテストケースを追加する（接続一覧のマウント時取得・表示、
      選択変更、詳細画面パターンでのナビゲーション発火・非発火、ログアウト時クリア）。
- [ ] Step 6: `connectionStore.test.ts`・`useConnection.test.ts`を新規作成する
      （`authStore`/`useAuth`の既存テストパターンに準拠）。
- [ ] Step 7: Documentation Generation — `aidlc-docs/construction/u1-platform-foundation/code/`
      配下の該当ドキュメントに本変更を追記する。