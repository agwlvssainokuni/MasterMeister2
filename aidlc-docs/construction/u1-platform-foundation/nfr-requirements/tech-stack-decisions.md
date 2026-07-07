# tech-stack-decisions.md — U1: Platform Foundation

`nfr-requirements.md`に基づく技術選定の決定事項一覧。

| # | 決定事項 | 選定 | 根拠 |
|---|---|---|---|
| 1 | JWT署名アルゴリズム | HS256（対称鍵、シークレットは環境変数注入） | Q1。ユーザ規模・運用体制を考慮するとシンプルな構成で十分 |
| 2 | JWTライブラリ | `io.jsonwebtoken:jjwt`（`jjwt-api` + `jjwt-impl` + `jjwt-jackson`、実行時スコープ） | HS256署名・検証、有効期限クレームの標準的な実装として広く使われる。Spring Authorization Server等の外部IdP連携は本要件では不要（自前でトークン発行・検証するため） |
| 3 | アクセストークン有効期限 | 10分 | Q1-b |
| 4 | リフレッシュトークン有効期限 | 24時間 | Q1-b |
| 5 | リフレッシュトークンの保存方式 | 内部DB（H2/JPA）に永続化（ステートフル） | Q1-a。ログアウト時の即時失効を可能にするため |
| 6 | リフレッシュトークンのローテーション | 使用毎に新規発行・旧トークン無効化（single-use rotating）、再使用検知時は全セッション強制失効 | Q1-c |
| 7 | CORS設定 | 本番: 設定不要（同一オリジン）。開発プロファイルのみ`WebConfig`で許可 | Q2。`docs/PROJECT_STRUCTURE.md`によりフロントエンドはWARの`static/`に同梱 |
| 8 | 対象RDBMS方言吸収 | Strategyパターン（`DialectStrategy`インタフェース + 方言別実装クラス + `DialectStrategyFactory`）。外部SQL方言抽象化ライブラリ（jOOQ等）は不採用 | Q3。要件が限定的なため軽量な自前実装で十分 |
| 9 | 内部DB（H2）動作モード | ファイルベース永続化モード（`jdbc:h2:file:...`） | Q4。再起動後もデータを保持する必要があるため |
| 10 | 内部DB接続プール | Spring Boot既定のHikariCP、既定プールサイズから開始 | Q4。明示的なチューニングが必要になった時点で見直す |
| 11 | 監査ログのインデックス | `occurredAt`（単一）、`userId`（単一）、`eventCategory`+`eventType`（複合） | Q5 |
| 12 | 監査ログの保持期間ポリシー | 本フェーズでは実装しない（将来の運用課題として記録のみ） | Q5。MVPスコープでは要件が明確でない |
| 13 | メール送信タイムアウト | SMTP接続/読み取り: 5秒程度 | Q6 |
| 14 | メール送信リトライ | なし（失敗は即座にログ記録） | Q6。本番SMTPの遅延で主処理を長時間ブロックしないことを優先 |
| 15 | アプリケーションログ形式 | Spring Boot既定のLogback、標準出力へのプレーンテキスト出力。ログ行は正規表現でパースしやすい一貫レイアウト（`%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} [%thread] %-5level %logger{36} - %msg%n`）で統一する | Q7 |
| 16 | PBT（Property-Based Testing）フレームワーク（PBT-09） | `jqwik`（`net.jqwik:jqwik`、テストスコープのGradle依存として追加、JUnit 5統合） | Q8。Java実装であり`business-logic-model.md`のTestable Properties節（P1〜P8）の検証に使用する |

---

## U1/U2 責務境界の記録（Q1-d）

- **U1（`SecurityConfig`）の責務**: アクセストークンの検証フィルタチェーン（HS256署名検証、
  有効期限チェック、エンドポイント単位の認可）のみ。リフレッシュ関連エンドポイントは
  アクセストークン検証の対象外として扱えるようフィルタチェームを設計する。
- **U2（`AuthenticationService`/`JwtTokenProvider`、次ユニット）の責務**: アクセストークン・
  リフレッシュトークンの発行、リフレッシュトークンの内部DB永続化・検証・ローテーション・
  失効ロジック、およびリフレッシュトークンのエンティティ定義。これらはU2自身の
  Functional Design/NFR Requirementsで確定する。

この決定により、U1のCode Generationでは「上記2〜16」の決定事項に基づく実装（`SecurityConfig`の
アクセストークン検証フィルタ、`DialectStrategy`系、H2ファイルモード設定、監査ログの
インデックス定義、`MailConfig`のタイムアウト設定、`logback-spring.xml`、jqwik依存追加）を行う。
リフレッシュトークンの永続化エンティティ・発行ロジックはU1のスコープ外（U2で実装）とする。