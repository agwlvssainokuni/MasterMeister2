# U1 Code Generation Plan（変更要求: 接続コンテキストのグローバル化）

## 対象
`business-logic-model.md` フロー5・`frontend-components.md`（U1 Functional Design改訂）に基づく、
`connectionStore`・`useConnection`・`AppLayout`のグローバル接続セレクタ実装。バックエンド変更は
U1になし（U3で完了済み）。

## ステップ

- [x] Step 1: `frontend/src/store/connectionStore.ts`を新規作成した。
- [x] Step 2: `frontend/src/hooks/useConnection.ts`を新規作成した。
- [x] Step 3: `AppLayout.tsx`を改修した（マウント時取得、接続選択`<select>`、
      `handleLogout`での`clearConnection`呼び出し）。
- [x] Step 4: 接続切替時のナビゲーション（`MASTER_DATA_DETAIL_PATTERN`正規表現、
      `useRef`による初回マウント時のスキップ）を実装した。
- [x] Step 5: `AppLayout.test.tsx`に7件追加した（既存3件→10件）。
      `AppRouter.test.tsx`も`listAccessibleConnections`のモック化に対応した。
- [x] Step 6: `connectionStore.test.ts`（6件）・`useConnection.test.ts`（3件）を新規作成した。
- [x] Step 7: `business-logic-summary.md`・`frontend-summary.md`・`testing-summary.md`に
      本変更を追記した。PBT性質はP10・P11として計画したが、既存の`DialectStrategy`系
      （Code Generation時新規識別）と衝突するためP13・P14に訂正した。

全テスト実行確認: `npx vitest run`（フロントエンド全体）271/271件成功、`npx tsc -b`・
`npx oxlint`ともエラーなし。