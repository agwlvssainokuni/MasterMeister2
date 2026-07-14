# User Stories Assessment（変更要求: 接続コンテキストのグローバル化 + クエリ実行時スキーマ指定）

## Request Analysis
- **Original Request**: `/saved-queries`等への直接アクセス時に接続を指定する手段がない不具合を
  発端に、(1) 接続選択のグローバルコンテキスト化、(2) クエリ実行時のスキーマ指定と実行履歴への
  スキーマ記録、を行う（`aidlc-docs/inception/requirements/requirements.md` §9）。
- **User Impact**: Direct — 一般ユーザ・管理者ともに日常的に使う画面（マスタデータ、クエリ
  ビルダー、保存クエリ、クエリ実行、クエリ履歴）のナビゲーション・操作手順が変わる。
- **Complexity Level**: Medium〜Complex — 4ユニット（U3/U5/U6/U7）にまたがり、既存の承認済み
  UIコンポーネント（ページ内接続セレクタ）の削除を伴う。
- **Stakeholders**: プロダクトオーナー（ユーザー本人）のみ（単独開発者によるMVP開発）。

## Assessment Criteria Met
- [x] High Priority: **User Experience Changes** — 既存のユーザワークフロー（接続選択の場所、
  クエリ実行前の入力項目）そのものを変更する。
- [x] High Priority: **Complex Business Logic** — スキーマ解決（ダイアレクトによる
  `SET search_path` 要否の分岐）は複数シナリオを持つ。
- [x] Medium Priority: **Scope** — マスタデータ／クエリビルダー／保存クエリ／クエリ実行／
  クエリ履歴の5画面・4ユニットにまたがる。
- [x] Benefits: 既存の`stories.md`にはMVP-10・ADM-3・GEN-6/8/9/10/11/13/15/16など、今回の
  変更で受け入れ基準が変わる/増える既存ストーリーが多数あり、変更点を明示的にストーリー
  レベルで反映しておくことで、後続のFunctional Design・Code Generationでの解釈のブレを防げる。

## Decision
**Execute User Stories**: Yes
**Reasoning**: 単純なバグ修正1件ではなく、複数の既存ユーザワークフローを横断的に変更する
UX変更であるため、High Priority基準（User Experience Changes）に明確に該当する。ただし、
本セッションでの議論により受け入れ基準に相当する詳細（`requirements.md` §9.2）は既に
確定しているため、新規ペルソナ策定や大規模な質問収集は不要と判断し、既存ペルソナ
（管理者／一般ユーザ、`personas.md`）を再利用し、影響を受ける既存ストーリーの改訂＋
新規ストーリーの追加という軽量な形で進める。

## Expected Outcomes
- 既存ストーリー（MVP-10、ADM-3、GEN-6、GEN-8、GEN-9、GEN-10、GEN-11、GEN-13、GEN-15、
  GEN-16）の受け入れ基準を、グローバル接続コンテキストおよびスキーマ指定を反映した内容に更新する。
- 新規ストーリー（グローバル接続セレクタでの切り替え、クエリ実行時のスキーマ選択、クエリ履歴の
  スキーマ表示）を追加し、Functional Design以降で参照できる形にする。