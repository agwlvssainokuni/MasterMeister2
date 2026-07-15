# U6 Functional Design 改訂プラン（変更要求: 接続コンテキストのグローバル化）

## 対象
`requirements.md` §9・`stories.md`（GEN-6/GEN-8/GEN-9改訂）・U1 Functional Design
（フロー5手順4: 接続切替時のモデルリセットは`QueryBuilderPage`自身の責務）を受けた、
U6のFunctional Design（`frontend-components.md`）への反映。`QueryBuilderPage`のページ内接続
セレクタを廃止し、グローバル接続コンテキスト参照に切替。実行/保存への遷移時にスキーマも
URLパラメータで引き継ぐ。

## 質問要否の判定
新たな確認質問は発生しないと判断した。ページ内セレクタ廃止はRequirements Analysis（Q8=B）、
スキーマ引き継ぎはRequirements Analysis（Q6=A）、接続切替時のモデルリセット責務は
U1 Functional Design（フロー5手順4）で確定済みであるため。

## 実行チェックリスト
- [x] Step A: `frontend-components.md`の`QueryBuilderPage`の状態・責務記述を改訂した
- [x] Step B: `GeneratedSqlPanel`の責務記述にスキーマのURL引き継ぎを追記した
