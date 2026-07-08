# nfr-requirements.md — U2: Auth & User Registration

`u2-auth-user-registration-nfr-requirements-plan.md`（Question 1〜6）の回答に基づく非機能要件。

---

## 1. Security Requirements

### 1.1 パスワードハッシュ化のコストファクタ（Question 1 = A）

- BCrypt（`BCryptPasswordEncoder`）を使用する。コストファクタ（`strength`）はSpring Security既定値
  10とし、`mm.app.security.password-encoder-strength`（デフォルト`10`）として設定可能にする。
- 小〜中規模の内部利用が前提であり、既定値で処理時間・セキュリティのバランスは十分と判断する。

### 1.2 クライアント側でのトークン保存方式（Question 2 = B）

- フロントエンド（`authStore`）は、アクセストークン・リフレッシュトークンともに`sessionStorage`に
  保存する（タブを閉じるまで永続化、ページリロードには耐える）。
- `localStorage`（ブラウザを閉じても永続化）は不採用。XSS発生時の窃取リスクをより低く抑えるため、
  タブを閉じれば消える`sessionStorage`を選択する。
- メモリ内保存のみ（`authStore`のReact状態のみ、Question 2 = A案）と比較すると、XSSリスクは
  やや高くなるが、ページリロード時に再ログインを要求しないUXを優先する（ユーザの明示的な選択）。
- Code Generationでは、`authStore`が`sessionStorage`との同期（初期化時の読み込み、更新時の書き込み、
  ログアウト時のクリア）を担う実装とする。

### 1.3 ログイン失敗のブルートフォース対策（Question 3 = A）

- 本フェーズでは能動的な保護機構（アカウントロック・レート制限）は実装しない。
- ログイン失敗は`business-rules.md` 2.1のとおり監査ログ（`AuditLogService`）に記録するのみとする。
- `security-baseline`拡張（現在無効、コア機能実装後にopt-in予定、`aidlc-state.md`参照）が有効化
  された際に、改めてアカウントロック等の保護方式を設計する。

### 1.4 初期管理者ブートストラップの認証情報取り扱い（Question 4 = A）

- 起動時（`ApplicationRunner`）に`mm.app.admin.bootstrap.password`（平文）をbcryptハッシュ化してから
  `User.passwordHash`に保存する。設定ファイル/環境変数上の平文パスワードそのものは永続化しない。
- 初回ログイン後のパスワード変更強制機構は本ユニットのスコープでは実装しない（パスワード変更機能
  自体が本ユニットの対象story外のため）。設定値の平文パスワードの管理責任は運用者側にあるものと
  割り切る。

### 1.5 不透明トークンのエントロピー（Question 5 = A）

- `RegistrationToken`・`RefreshToken`はいずれも、`SecureRandom`で生成した32バイト（256ビット）の
  ランダムバイト列をURL-safe base64エンコードした文字列とする。
- 総当たり攻撃に対して十分なエントロピーを持ち、業界標準的なトークン長と同等である。
- DBには平文トークンを保存せず、SHA-256ハッシュ値（`tokenHash`）のみを保存する
  （`domain-entities.md`既定）。

---

## 2. Scalability Requirements

### 2.1 トークン検索のインデックス方針（Question 6 = A）

- `RegistrationToken.tokenHash`・`RefreshToken.tokenHash`列にunique制約を付与し、これにより
  暗黙的に張られるインデックスのみでトークン検索を賄う。
- 外部キャッシュ層（Redis等）は導入しない。想定利用規模（内部マスタデータ管理システム、同時
  ログインユーザ数は限定的）を踏まえ、この構成で十分と判断する。

---

## 3. PBT Compliance（property-based-testing拡張）

- U1のNFR Requirementsで確定済みの`jqwik`採用（PBT-09）をそのまま踏襲する。本ユニットでの
  再確認・再質問は行わない。
- 他のPBTルール（PBT-01〜PBT-08, PBT-10）はCode Generation Planning/Code Generation/Build and Test
  ステージで適用される。