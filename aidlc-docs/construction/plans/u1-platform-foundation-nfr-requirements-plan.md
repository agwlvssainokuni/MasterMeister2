# NFR Requirements Plan — U1: Platform Foundation

## ユニット適用可否の判定

`construction/nfr-requirements.md` の実行/スキップ判定基準（top-level `.claude/CLAUDE.md`）に
基づき、U1は **実行（EXECUTE）** と判定。

- **Security Requirements**: `SecurityConfig`（フィルタチェーン、JWT検証、CORS、エンドポイント
  認可ルール）はU1の責務であり、具体的な設計判断を要する。
- **Tech Stack Selection**: `DialectStrategy`系（対象RDBMS方言吸収）の実装方式、内部DB（H2/JPA）の
  永続化方式、PBT-09（フレームワーク選定）の確定が必要。
- **Scalability/Performance**: 監査ログ（`AuditLog`）は全ユニット横断で書き込まれ続けるため、
  データ量増加時の検索性能（インデックス方針）を検討する必要がある。
- **Reliability**: メール送信（外部SMTP依存）のタイムアウト・リトライ方針が未確定。

→ 全カテゴリの中から上記4点を中心に質問を構成する。

---

## Step 1: Functional Design成果物の分析

- [x] `domain-entities.md`（`AuditLog`, `EventCategory`/`EventType`, `MailNotificationType`）確認
- [x] `business-rules.md`（監査記録の分離トランザクション・失敗非伝播、メール送信ベストエフォート、
      共通例外マッピング、CSS変数デザインシステム）確認
- [x] `business-logic-model.md`（フロー1-4、および追加した Testable Properties P1-P9）確認
- [x] `frontend-components.md`（共通基盤コンポーネント、`auditLog/`画面）確認

---

## 成果物生成タスク（`construction/nfr-requirements.md` Step 6）

- [x] `aidlc-docs/construction/u1-platform-foundation/nfr-requirements/nfr-requirements.md`
- [x] `aidlc-docs/construction/u1-platform-foundation/nfr-requirements/tech-stack-decisions.md`

---

## Question 1: Security — JWT検証・トークン方式

`SecurityConfig`（U1）はJWTベースのステートレス認証フィルタチェーンを構成する
（発行自体は`AuthenticationService`/`JwtTokenProvider`、U2の責務）。検証方式について確認したい。

A. 対称鍵署名（HS256）、シークレットは環境変数で注入（12-factor準拠）。トークン有効期限は
   アクセストークンのみ（リフレッシュトークンなし、期限切れ時は再ログインを要求）とする
   （推奨。ユーザ規模・運用体制を考慮するとシンプルな構成で十分と判断）
B. 非対称鍵署名（RS256）を採用する
C. リフレッシュトークン機構を導入する
D. その他（具体的な方式を指定）

[Answer]:

Aを基本として、リフレッシュトークン機構を追加。

---

## Question 2: Security — CORS方針

`docs/PROJECT_STRUCTURE.md`によりフロントエンドビルド成果物はWARの`static/`に組み込まれ、
本番運用は同一オリジン配信となる。開発時（Viteの別ポート起動時）のCORS設定について確認したい。

A. 本番プロファイルではCORS設定不要（同一オリジン）。開発プロファイル（`dev`等）でのみ
   `localhost:5173`等からのオリジンを許可するCORS設定を`WebConfig`に追加する（推奨）
B. 本番でも別オリジン配信を想定し、常時CORS設定を有効にする
C. その他（具体的な方針を指定）

[Answer]:

A

---

## Question 3: Tech Stack — DialectStrategyの実装方式

対象RDBMS（MySQL/MariaDB/PostgreSQL/H2）の方言差異吸収（ページング構文、識別子クォート等）の
実装アプローチを確認したい。

A. Strategyパターン：`DialectStrategy`インタフェース + 方言ごとの実装クラス
   （`MySqlDialectStrategy`等）+ `DialectStrategyFactory`（対象RDBMS種別で選択）。
   外部のSQL方言抽象化ライブラリは導入せず、必要な差異のみ自前実装する
   （推奨。要件が限定的なため軽量な自前実装で十分と判断）
B. 外部ライブラリ（jOOQ等）のDialect機構を導入する
C. その他（具体的な方式を指定）

[Answer]:

A

---

## Question 4: Tech Stack — 内部DB（H2）の永続化方式

内部DB（ユーザ・接続設定・権限・監査ログ等）は`REQUIREMENTS.md`/`CLAUDE.md`によりH2（JPA）と
決まっているが、動作モードを確認したい。

A. ファイルベース永続化モード（`jdbc:h2:file:...`）を使用し、アプリケーション再起動後もデータを
   保持する。接続プールはSpring Boot既定のHikariCPをそのまま使用し、プールサイズ等は
   Spring Boot既定値から出発する（推奨。明示的なチューニングが必要になった時点で見直す）
B. インメモリモード（`jdbc:h2:mem:...`）を使用する（再起動でデータ消失）
C. その他（具体的な設定を指定）

[Answer]:

A

---

## Question 5: Scalability — 監査ログのインデックス・保持方針

監査ログ（`AuditLog`）は全ユニットから継続的に書き込まれ、ADM-6の検索機能（日時・ユーザ・
操作種別での絞り込み）はデータ量増加後も一定の性能を保つ必要がある。

A. `occurredAt`（降順ソート・範囲検索）、`userId`、`eventCategory`+`eventType`に
   複合/単一インデックスを付与する。データの自動削除・アーカイブ（保持期間ポリシー）は
   本フェーズでは実装せず、将来の運用課題として`nfr-requirements.md`に明記するに留める
   （推奨。MVPスコープでは保持期間ポリシーの要件が明確でないため）
B. 保持期間ポリシー（例: N年経過後自動削除）をこのフェーズで設計・実装する
C. その他（具体的なインデックス方針・保持方針を指定）

[Answer]:

A

---

## Question 6: Reliability — メール送信のタイムアウト・リトライ方針

`business-rules.md` 2.3で送信失敗は主業務処理をブロックしないことは決定済みだが、
具体的なタイムアウト値・リトライ有無を確認したい。

A. SMTP接続/読み取りタイムアウトを短め（例: 5秒程度）に設定し、リトライは行わない
   （失敗は即座にログ記録し諦める。開発時のMailPitはローカル接続のため問題にならず、
   本番SMTPの遅延で主処理を長時間ブロックしないことを優先）（推奨）
B. リトライ機構（例: 3回まで指数バックオフ）を導入する
C. その他（具体的な値・方針を指定）

[Answer]:

A

---

## Question 7: Maintainability — ロギング方式

障害調査・監査ログとは別の、アプリケーション運用ログ（`business-rules.md`各所で言及される
「アプリケーションログにエラー出力」等）の形式を確認したい。

A. Spring Boot既定のログ機構（Logback）をそのまま使用し、標準出力へのプレーンテキスト出力とする
   （コンテナ/WAR双方の運用を考慮し、ログ集約基盤側でのパース設定を前提にしない
   シンプルな構成とする）（推奨）
B. 構造化ログ（JSON形式）を導入する
C. その他（具体的な方式を指定）

[Answer]:

A

ただし、ログのレイアウトはパースのしやすさ(正規表現によるログ監視のしやすさ)を考慮する。

---

## Question 8: Tech Stack — PBT-09 フレームワーク選定確定

`business-logic-model.md`のTestable Properties節でPBT-09候補として`jqwik`（JUnit 5統合）を
提示した。これを本ユニット（および他ユニット共通）のPBTフレームワークとして確定してよいか。

A. `jqwik`を採用する。Gradle依存として`net.jqwik:jqwik`をテストスコープに追加する（推奨）
B. 他のフレームワークを指定する

[Answer]:

A

---

## Follow-up Questions (Round 2) — Question 1 の詳細化

Question 1の回答「Aを基本として、リフレッシュトークン機構を追加」により、当初の推奨案
（アクセストークンのみ・リフレッシュトークンなし）から設計方針が変わった。リフレッシュ
トークンは`domain-entities.md`に永続化モデルとして存在しないため、`SecurityConfig`（U1）に
直接影響する以下の点を確定させたい。

### Question 1-a: リフレッシュトークンの保存・検証方式

A. 内部DB（H2/JPA）にリフレッシュトークンを永続化し、失効管理を行う（ログアウト時の
   即時無効化が可能）。`SecurityConfig`（U1）はアクセストークンの検証のみを担当し、
   リフレッシュトークンの発行・検証・失効はU2の`AuthenticationService`が内部DBを介して
   行う（推奨。ログアウト時の即時失効を可能にするにはステートフルな管理が必要なため）
B. リフレッシュトークンもステートレス（署名検証のみ、DB照会なし）とし、失効は有効期限切れを
   待つのみとする（ログアウト時の即時無効化はできない）
C. その他（具体的な方式を指定）

[Answer]:

A


### Question 1-b: 有効期限

A. アクセストークン: 15分程度、リフレッシュトークン: 7日〜30日程度とする
   （具体的な値は`nfr-requirements.md`に記録し、最終的な数値はNFR Design/Code Generationで
   `application.yml`の設定値として確定する）（推奨）
B. その他の値を指定

[Answer]:

アクセストークン: 10分
リフレッシュトークン: 24時間


### Question 1-c: ローテーション方針

A. リフレッシュトークン使用時に新しいリフレッシュトークンを発行し、古いものを無効化する
   （single-use rotating）。無効化済みトークンの再使用を検知した場合はそのユーザの
   全セッションを強制ログアウトさせる（不正利用対策）（推奨）
B. リフレッシュトークンは有効期限内であれば再利用可能とする（ローテーションなし）
C. その他（具体的な方針を指定）

[Answer]:

A


### Question 1-d: U1/U2の責務境界

A. `SecurityConfig`（U1）はアクセストークンの検証フィルタチェーンのみを担当する。
   リフレッシュトークンの発行・保存・ローテーション・失効ロジックはU2の
   `AuthenticationService`の責務とする。U1のNFR Requirementsでは「アクセストークン検証は
   HS256/JWT、かつリフレッシュトークン関連の内部DB問い合わせ経路をU2から利用できるよう
   `SecurityConfig`のフィルタチェーンを設計する」という指針のみを確定し、リフレッシュ
   トークンの詳細設計（エンティティ定義等）はU2自身のFunctional Design/NFR Requirementsで
   確定する（推奨。U1は最基盤ユニットでU2に依存されるが、リフレッシュトークンの業務ロジックは
   認証機能自体の一部でありU2の責務と整合する）
B. リフレッシュトークンの詳細設計もこのU1 NFR Requirementsで確定する

[Answer]:

A

---

## 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください。全ての質問に回答後、その旨を伝えてください。