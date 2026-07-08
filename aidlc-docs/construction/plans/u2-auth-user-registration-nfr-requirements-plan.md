# NFR Requirements Plan — U2: Auth & User Registration

## ユニット適用可否の判定

`construction/nfr-requirements.md` の実行/スキップ判定基準（top-level `.claude/CLAUDE.md`）に
基づき、U2は **実行（EXECUTE）** と判定。

- **Security Requirements**: パスワードハッシュ化のコストファクタ、トークンのエントロピー、
  クライアント側でのトークン保存方式、ログイン失敗時のブルートフォース対策、初期管理者
  ブートストラップ用パスワードの取り扱いなど、U1のNFR Requirements（JWT検証方式・
  リフレッシュトークンのローテーション方針まで）ではカバーされていない、U2固有の
  セキュリティ判断が複数残っている。
- **Tech Stack Selection**: `RegistrationToken`/`RefreshToken`の不透明トークン生成（`SecureRandom`
  由来）のバイト長・エントロピーが未確定。`PasswordEncoder`実装（BCrypt）のコストファクタも未確定。
- **Reliability**: ログイン失敗の連続発生に対する保護方針（アカウントロック等）が未確定。
- **Scalability/Performance**: 登録・ログインエンドポイントの想定負荷、トークン検索のための
  インデックス方針を確認する必要がある（ただし小規模内部利用が前提のため大きな懸念ではない
  想定）。
- **Availability/Usability**: 本ユニット固有の新規論点は見当たらない（U1のNFR Requirements・
  Functional Designでカバー済み。個別質問は設けない）。

→ 上記の中から6問を構成する。

---

## Step 1: Functional Design成果物の分析

- [x] `domain-entities.md`（`User`/`Role`/`UserStatus`、`RegistrationToken`、`RefreshToken`
      〈`familyId`によるローテーション連鎖〉、管理者ブートストラップ設計判断）確認
- [x] `business-rules.md`（列挙攻撃対策、登録/承認/却下フロー、ログイン、リフレッシュの
      reuse detection、設定キー、パスワードハッシュ化、API認可パスパターン）確認
- [x] `business-logic-model.md`（フロー1-6、Testable Properties P1-P11）確認
- [x] `frontend-components.md`（`auth/`・`userRegistration/`のコンポーネント構成、ルーティング）確認
- [x] U1 `nfr-requirements.md`/`tech-stack-decisions.md`（JWT方式、リフレッシュトークン
      ローテーション方針、jjwt/jqwikの技術選定）を前提として参照し、重複質問を避ける

---

## 成果物生成タスク（`construction/nfr-requirements.md` Step 6）

- [ ] `aidlc-docs/construction/u2-auth-user-registration/nfr-requirements/nfr-requirements.md`
- [ ] `aidlc-docs/construction/u2-auth-user-registration/nfr-requirements/tech-stack-decisions.md`

---

## Question 1: Security — パスワードハッシュ化のコストファクタ

`business-rules.md` 4節でBCrypt使用は決定済み（強度ポリシーなし）。具体的なコストファクタ
（BCryptの`strength`パラメータ）を確認したい。

A. Spring Security既定値（`BCryptPasswordEncoder()`のデフォルト、strength=10）をそのまま使用する。
   将来的な調整の余地を残すため、`mm.app.security.password-encoder-strength`
   （デフォルト`10`）として設定可能にする（推奨。小〜中規模の内部利用が前提であり、
   既定値で処理時間・セキュリティのバランスは十分）
B. より高いstrength値（12等）を明示的に指定する
C. その他（具体的な値を指定）

[Answer]:

---

## Question 2: Security — クライアント側でのトークン保存方式

`business-rules.md` 2.1でアクセストークン・リフレッシュトークンはJSONレスポンスボディで
返却することは決定済み（Cookie不使用）。フロントエンド（`authStore`）での保存方式を
確認したい。

A. メモリ内（`authStore`のReact状態のみ、`localStorage`/`sessionStorage`に永続化しない）に
   保存する。XSS発生時のトークン窃取リスクを最小化する。ページリロード時はトークンが
   失われるため、リフレッシュトークンで自動再認証はできず再ログインを要求する
   （推奨。セキュリティを優先し、リロード時の再ログインというUX上のコストを許容する）
B. `sessionStorage`に保存する（タブを閉じるまで永続化、ページリロードには耐える。
   XSSでの窃取リスクはメモリ内保存より高い）
C. `localStorage`に保存する（ブラウザを閉じても永続化。「ログイン状態を保持する」的な
   UXを実現できるが、XSSでの窃取リスクが最も高い）
D. その他（具体的な方式を指定）

[Answer]:

---

## Question 3: Reliability/Security — ログイン失敗のブルートフォース対策

`business-rules.md` 2.1でログイン失敗は監査ログに記録される（P8）ことは決定済みだが、
連続失敗に対する能動的な保護（アカウントロック・レート制限等）は未確定。

A. 本フェーズでは実装しない。監査ログへの記録のみとし、`security-baseline`拡張
   （現在無効、コア機能実装後に opt-in 予定）が有効化された際に改めて設計する
   （推奨。プロジェクトの拡張機能運用方針と整合する）
B. アカウントロック機構（例: 連続N回失敗で一定時間ログイン不可）を本フェーズで実装する
C. IPアドレスベースのレート制限を本フェーズで実装する
D. その他（具体的な方針を指定）

[Answer]:

---

## Question 4: Security — 初期管理者ブートストラップの認証情報取り扱い

`domain-entities.md`で承認済みの管理者ブートストラップ（`mm.app.admin.bootstrap.email`/
`mm.app.admin.bootstrap.password`から起動時に1件だけADMIN Userを作成）について、
設定値として渡される平文パスワードの取り扱い方針を確認したい。

A. 起動時にbcryptハッシュ化してから`User.passwordHash`に保存する（設定ファイル/環境変数の
   平文パスワードは永続化しない）。初回ログイン後のパスワード変更強制機構は本ユニットの
   スコープでは実装しない（パスワード変更機能自体が本ユニットの対象story外のため）
   （推奨。設定値の平文パスワードはデプロイ時点で運用者が管理する責任と割り切る）
B. 初回ログイン時にパスワード変更を強制する仕組みを本フェーズで追加実装する
C. その他（具体的な方針を指定）

[Answer]:

---

## Question 5: Tech Stack — 不透明トークンのエントロピー（バイト長）

`RegistrationToken`/`RefreshToken`はどちらも`SecureRandom`由来のURL-safe base64文字列
（Question 2 = A、Functional Design）と決定済みだが、具体的なバイト長（エントロピー）が
未確定。

A. 両トークンとも32バイト（256ビット）のランダムバイト列をURL-safe base64エンコードする
   （推奨。総当たり攻撃に対して十分なエントロピーを持ち、JWT等の業界標準的なトークン長と
   同等）
B. 別の長さを指定する

[Answer]:

---

## Question 6: Scalability — トークン検索・登録/ログインエンドポイントの負荷想定

`RegistrationToken.tokenHash`/`RefreshToken.tokenHash`は検証のたびにDB検索される
（`business-rules.md` 1.3, 2.2）。想定負荷・インデックス方針を確認したい。

A. `tokenHash`列にunique制約（暗黙的にインデックスが張られる）を付与するのみとし、
   追加のキャッシュ層は導入しない。想定利用規模（内部マスタデータ管理システム、
   同時ログインユーザ数は限定的）を踏まえ、この構成で十分と判断する（推奨）
B. Redis等の外部キャッシュ層を導入し、トークン検証をキャッシュ経由で行う
C. その他（具体的な方針を指定）

[Answer]:

---

## 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください。全ての質問に回答後、その旨を伝えてください。