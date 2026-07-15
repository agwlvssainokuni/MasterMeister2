# U1 Functional Design 改訂プラン（変更要求: 接続コンテキストのグローバル化）

## 対象
`requirements.md` §9・`stories.md` CHG-1/CHG-2/CHG-3・Application Design（フロー8
「グローバル接続コンテキストの解決」）を受けた、U1のFunctional Design（`frontend-components.md`、
`business-logic-model.md`）への反映。`AppLayout`に常設のグローバル接続セレクタを追加する。
`business-rules.md`・`domain-entities.md`はバックエンド変更を伴わない（U1はフロントエンドのみの
プラットフォーム基盤）ため対象外。

## 質問要否の判定
Application Design・Requirements Analysisで大半が確定済みのため、新たな確認質問は発生しないと
判断した。唯一のFunctional Designレベルの新規判断事項は以下の2点で、いずれも既存の類似実装
（`authStore`、`SchemaTableListPage`の接続セレクタ）との一貫性から自明に導けるため、質問とせず
本プランに直接記載する。

- **ストア実装**: 既存`authStore`（`store/authStore.ts`、zustand、`sessionStorage`永続化）と
  同じパターンで`connectionStore`を新設する（Q1=A、sessionStorage）。
- **接続一覧が1件のみの場合の自動選択**: 行わない。既存`SchemaTableListPage`/`QueryBuilderPage`の
  接続セレクタも選択肢が1件でも自動選択せず、ユーザーの明示的な選択を必須としており、この
  流儀に合わせる。

## 実行チェックリスト
- [x] Step A: `frontend-components.md`の`store/`表に`connectionStore`を追加した
- [x] Step B: `frontend-components.md`の`hooks/`表に`useConnection`を追加した
- [x] Step C: `frontend-components.md`の`AppLayout`の責務記述を更新した
- [x] Step D: `business-logic-model.md`に新規フロー5「グローバル接続コンテキストの解決」と
      PBT性質P13・P14を追加した（既存のP10〜P12はDialectStrategy関連で衝突するためP13起番に訂正）
