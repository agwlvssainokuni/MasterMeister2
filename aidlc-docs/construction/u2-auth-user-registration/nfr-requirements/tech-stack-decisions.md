# tech-stack-decisions.md — U2: Auth & User Registration

`nfr-requirements.md`に基づく技術選定の決定事項一覧。

| # | 決定事項 | 選定 | 根拠 |
|---|---|---|---|
| 1 | パスワードハッシュ化のコストファクタ | BCrypt strength=10（既定値）。`mm.app.security.password-encoder-strength`（デフォルト`10`）で設定可能にする | Question 1 = A |
| 2 | クライアント側トークン保存方式 | `sessionStorage`（アクセストークン・リフレッシュトークンとも） | Question 2 = B。ページリロード時に再ログインを要求しないUXを優先 |
| 3 | ログイン失敗時のブルートフォース対策 | 本フェーズでは実装しない（監査ログ記録のみ） | Question 3 = A。`security-baseline`拡張opt-in時に改めて設計 |
| 4 | 初期管理者ブートストラップのパスワード取り扱い | 起動時にbcryptハッシュ化して保存。初回ログイン時のパスワード変更強制は未実装 | Question 4 = A |
| 5 | 不透明トークンのバイト長 | 32バイト（256ビット）、URL-safe base64エンコード（`RegistrationToken`・`RefreshToken`共通） | Question 5 = A |
| 6 | トークン検索のインデックス方針 | `tokenHash`列のunique制約（暗黙的インデックス）のみ。外部キャッシュ層は不採用 | Question 6 = A |
| 7 | PBT（Property-Based Testing）フレームワーク | `jqwik`（U1で確定済み、再選定なし） | U1 `tech-stack-decisions.md` #16を踏襲 |

---

## クライアント側トークン保存方式の補足（Question 2 = B、Q2 = Aからの変更点）

- 当初の推奨案（Question 2 = A: メモリ内保存のみ、`localStorage`/`sessionStorage`不使用）は、XSS
  発生時のトークン窃取リスクを最小化する一方、ページリロード時に必ず再ログインを要求するUX上の
  コストがあった。
- ユーザの判断により、`sessionStorage`（Question 2 = B）を採用する。タブを閉じるまでは永続化される
  ため、ページリロードには耐えるが、ブラウザ/タブを閉じると消える（`localStorage`ほどの永続性は
  持たない）。
- Code Generationでの実装方針: `authStore`（フロントエンド）が、ログイン成功時に
  `sessionStorage.setItem`でトークンを保存し、アプリ起動時（`authStore`初期化時）に
  `sessionStorage.getItem`で復元し、ログアウト時・リフレッシュ失敗時（reuse detection含む）に
  `sessionStorage.removeItem`でクリアする。