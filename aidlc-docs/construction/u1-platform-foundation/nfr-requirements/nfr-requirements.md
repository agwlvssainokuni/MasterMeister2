# nfr-requirements.md — U1: Platform Foundation

`u1-platform-foundation-nfr-requirements-plan.md`（Q1〜Q8、およびQ1のFollow-up 1-a〜1-d）
の回答に基づく非機能要件。

---

## 1. Security Requirements

### 1.1 JWT検証・トークン方式（Q1, 1-a, 1-b, 1-c, 1-d）

- **署名アルゴリズム**: HS256（対称鍵）。シークレットは環境変数で注入する（12-factor準拠）。
- **アクセストークン**: 有効期限10分。ステートレス（内部DB照会なしで検証可能）。
  `SecurityConfig`（U1）のフィルタチェーンが検証する。
- **リフレッシュトークン**: 有効期限24時間。内部DB（H2/JPA）に永続化し、失効管理を行う
  （ステートフル。ログアウト時に即時無効化できる）。
- **ローテーション方針**: リフレッシュトークン使用時に新しいリフレッシュトークンを発行し、
  古いものを無効化する（single-use rotating）。無効化済みトークンの再使用を検知した場合、
  そのユーザの全セッション（すべてのリフレッシュトークン）を強制的に無効化する
  （不正利用・トークン漏洩対策）。
- **U1/U2の責務境界**:
  - U1（`SecurityConfig`）: アクセストークンの検証フィルタチェーンのみを担当する。
    HS256署名検証・有効期限チェック・認可（エンドポイントごとのロールベースアクセス制御）を行う。
  - U2（`AuthenticationService`/`JwtTokenProvider`、今後のFunctional Design/NFR Requirementsで
    詳細確定）: アクセストークン・リフレッシュトークン双方の発行、リフレッシュトークンの
    内部DB永続化・検証・ローテーション・失効ロジックを担当する。リフレッシュトークンの
    エンティティ定義（`domain-entities.md`相当）はU2側で行う。
  - U1のNFR Designでは、`SecurityConfig`のフィルタチェームがリフレッシュ関連エンドポイント
    （例: `/api/auth/refresh`）をアクセストークン検証の対象外として扱えるよう設計する
    （具体的なエンドポイントパスはU2のFunctional Designで確定）。

### 1.2 CORS方針（Q2）

- 本番プロファイルではCORS設定は不要（フロントエンドはWARの`static/`に同梱され同一オリジン配信、
  `docs/PROJECT_STRUCTURE.md`）。
- 開発プロファイル（`dev`等）でのみ、Viteの開発サーバ（`localhost:5173`等）からのオリジンを
  許可するCORS設定を`WebConfig`に追加する。

---

## 2. Tech Stack Selection

### 2.1 DialectStrategyの実装方式（Q3）

- Strategyパターンで実装する: `DialectStrategy`インタフェース + 対象RDBMS種別ごとの実装クラス
  （`MySqlDialectStrategy`, `MariaDbDialectStrategy`, `PostgreSqlDialectStrategy`,
  `H2DialectStrategy`） + `DialectStrategyFactory`（接続設定の種別で選択）。
- 外部のSQL方言抽象化ライブラリ（jOOQ等）は導入しない。ページング構文・識別子クォート等、
  必要な差異のみ自前実装する。

### 2.2 内部DB（H2）の永続化方式（Q4）

- ファイルベース永続化モード（`jdbc:h2:file:...`）を使用する。アプリケーション再起動後も
  データを保持する。
- 接続プールはSpring Boot既定のHikariCPをそのまま使用する。プールサイズ等は
  Spring Boot既定値から出発し、明示的なチューニングが必要になった時点で見直す。

### 2.3 PBT-09: フレームワーク選定確定（Q8）

- `jqwik`（JUnit 5統合）を採用する。詳細は`tech-stack-decisions.md`参照。

---

## 3. Scalability Requirements

### 3.1 監査ログのインデックス・保持方針（Q5）

- `AuditLog`テーブルに以下のインデックスを付与する:
  - `occurredAt`（降順ソート・範囲検索に使用）
  - `userId`
  - `eventCategory` + `eventType`（複合インデックス）
- データの自動削除・アーカイブ（保持期間ポリシー）は本フェーズでは実装しない。
  **将来の運用課題**: 監査ログは無期限に蓄積されるため、データ量が増大した場合は
  保持期間ポリシー（例: N年経過後アーカイブ/削除）の導入を将来的に検討する必要がある。

---

## 4. Reliability Requirements

### 4.1 メール送信のタイムアウト・リトライ方針（Q6）

- SMTP接続/読み取りタイムアウトを短め（5秒程度）に設定する。
- リトライは行わない。送信失敗は即座にアプリケーションログにエラー出力し、主業務処理
  （`business-rules.md` 2.3）はブロックしない。

### 4.2 リフレッシュトークンの不正利用対策（Q1, 1-c）

- 無効化済み（ローテーション済み）リフレッシュトークンの再使用を検知した場合、対象ユーザの
  全セッションを強制的に失効させる（1.1参照）。

---

## 5. Maintainability Requirements

### 5.1 ロギング方式（Q7）

- Spring Boot既定のログ機構（Logback）をそのまま使用し、標準出力へのプレーンテキスト出力とする。
  構造化ログ（JSON）は導入しない。
- ただし、正規表現によるログ監視・パースがしやすいよう、ログの各行を一貫したレイアウトに統一する。
  推奨パターン（Code Generation時に`logback-spring.xml`で確定）:
  ```
  %d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} [%thread] %-5level %logger{36} - %msg%n
  ```
  （ISO 8601日時、スレッド名、ログレベル固定幅、ロガー名、メッセージの順で1行1レコード）。

---

## 6. PBT Compliance（property-based-testing拡張）

- **PBT-09（フレームワーク選定）**: `jqwik`を採用。`tech-stack-decisions.md`に記録（対応済み）。
- 他のPBTルール（PBT-01〜PBT-08, PBT-10）はCode Generation Planning/Code Generation/Build and Test
  ステージで適用される（`property-based-testing.md`のEnforcement Integration表参照）。本ステージでの
  対象はPBT-09のみ。