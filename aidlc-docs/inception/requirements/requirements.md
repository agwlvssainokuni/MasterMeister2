# 要件定義（Requirements Analysis）

## インテント分析サマリ

- **ユーザリクエスト**: `docs/REQUIREMENTS.md` に定義された MasterMeister（マスタデータメンテナンスアプリケーション）プロジェクト全体を対象に、AI-DLC のプロセスに沿って要件を確定する
- **リクエスト種別**: New Project（新規プロジェクト、ただしスキャフォールドのみ既存＝Brownfield扱い）
- **スコープ見積り**: System-wide（バックエンド・フロントエンド・内部DB・対象RDBMS接続・権限モデル全体に影響）
- **複雑度見積り**: Complex（複数の対象RDBMS方言対応、テーブル/カラム二階層の権限モデル、クエリビルダー、監査ログなど、相互依存する機能が多い）
- **深度**: Comprehensive

本ドキュメントは `docs/REQUIREMENTS.md`（原本要件）を正としつつ、そこで未確定だった事項を
`requirement-verification-questions.md` への回答で確定し、両者を統合したものである。

---

## 1. プロジェクト概要（確定事項）

RDBMS に格納されたマスタデータをメンテナンスするための Web アプリケーション（SPA）。
Spring Boot バックエンド + React フロントエンド。単独開発者による MVP ファースト開発。
実装優先順位: ユーザ管理 → 対象 RDBMS セットアップ → アクセス制御 → データ表示。

## 2. 技術スタック（確定事項、変更なし）

`docs/REQUIREMENTS.md` セクション2の内容をそのまま踏襲する（Java 25 / Spring Boot 4.1 / Gradle 9.6、
React 19 / Vite、内部DBはH2+JPA、対象RDBMSはNamedParameterJdbcTemplate、対象RDBMSは
MySQL/MariaDB/PostgreSQL/H2 をサポート）。

## 3. 今回確定した事項（要確認質問への回答）

### 3.1 ユーザ認証方式 → JWT
- **決定**: JWT（ステートレストークン、Authorizationヘッダーで送信）
- **影響**: `auth/` パッケージはセッション状態をサーバ側に保持しない。トークンの発行・検証・
  リフレッシュの仕組みが必要。監査ログ要件（6.1 認証イベント）のログアウト記録は、
  JWTの性質上「明示的なログアウトAPI呼び出し」または「トークン失効」の記録として実装する。

### 3.2 ロールモデル → 管理者／一般ユーザの2種類 + 一般ユーザのグルーピング
- **決定**: ロールは「管理者」「一般ユーザ」の2種類のみ。
- **追加要件**: 一般ユーザをグループ化し、グループ単位でテーブル/カラムレベルのアクセス権限を
  まとめて設定できるようにする。
- **影響**: 権限モデル（5.2）は「ユーザ単位」だけでなく「グループ単位」の権限設定を持つ。
  グループとユーザの対応（多対多、または多対1）、グループ権限とユーザ個別権限が両方存在する
  場合の優先順位・合成ルールは、Application Design 段階で詳細設計する。

### 3.3 対象RDBMS接続 → 複数接続、接続ごとに独立管理
- **決定**: 複数の対象RDBMS接続を登録可能とし、ユーザ・権限は接続ごとに独立して管理する。
- **影響**: これは `docs/REQUIREMENTS.md` 5.2 の記述（単一の「接続情報」を前提にした書きぶり）を
  拡張する重要な決定。スキーマ取り込み・権限設定・マスタメンテナンス・クエリビルダー・
  保存クエリ・クエリ履歴のすべてが「対象RDBMS接続」を主キーの一部として扱う必要がある。
  ユーザと権限の対応関係も「ユーザ × 接続 × テーブル/カラム」の3軸になる。

### 3.4 パスワードポリシー → 標準的なハッシュ化のみ
- **決定**: bcrypt等の標準的なハッシュ化のみを要件とし、パスワード強度ポリシー
  （最低文字数・文字種混在等）は設けない。

### 3.5 ログインセッション（トークン）有効期限 → 設定可能
- **決定**: ユーザ登録トークン（`mm.app.user-registration.token-expiry-hours`）と同様に、
  環境変数/設定ファイルで有効期限を設定可能にする。デフォルト値は実装時に提案する。
- **設定項目案**: `mm.app.auth.token-expiry-*`（詳細キー名は Application Design で確定）。

### 3.6 i18n（多言語対応） → 日本語デフォルト、将来のi18nを見据えた設計
- **決定**: UI・メッセージは日本語をデフォルトとする。ただし、将来的な多言語化を見据え、
  文言をハードコードせずリソース化するなど、i18n対応がしやすい設計にする。
- **影響**: フロントエンドは i18n ライブラリ導入を前提とした文言管理（例: `react-i18next` 等の
  技術選定は NFR Design で検討）、バックエンドはエラーメッセージ等をメッセージキー化する。

## 4. 拡張機能（Extension）の適用方針

| Extension | 判定 | 備考 |
|---|---|---|
| security-baseline | **見送り**（後日追加予定） | 一通りの機能を実装できた段階でオプトインする方針。現時点ではセキュリティルールをブロッキング制約としては強制しない。 |
| resiliency-baseline | **見送り**（後日追加予定） | 同上。単独開発者によるMVP開発の初期段階では、可用性/回復性の設計ガイドは優先度を下げる。 |
| property-based-testing | **有効化**（全ルールをブロッキング制約として適用） | データ変換・シリアライズ・権限合成ロジックなど、性質ベーステストが有効な箇所が多いため、PBT-01〜PBT-10 を Functional Design / Code Generation / Build and Test の各段階で適用する。 |

（`aidlc-docs/aidlc-state.md` の `## Extension Configuration` に記録済み）

## 5. 機能要件（`docs/REQUIREMENTS.md` セクション5を、3章の決定で補強したもの）

### 5.1 ユーザ登録
`docs/REQUIREMENTS.md` 5.1 のとおり（2段階メールアドレス先行登録、管理者承認ワークフロー、
メールアドレス列挙攻撃対策、設定可能なトークン有効期限）。変更なし。

### 5.2 対象RDBMSセットアップ・アクセス権限
- 管理者は**複数**の対象RDBMS接続を登録・管理できる（3.3参照）。
- スキーマ取り込みは接続ごとに実施し、取り込んだスキーマ情報は接続に紐づけて内部DBに保持する。
- アクセス権限は「接続 ×（ユーザ or ユーザグループ）×（スキーマ/テーブル/カラム）」単位で
  設定する（3.2, 3.3参照、Application Designで確定）:
  - **主権限**（なし / 読み取りのみ R / 読み取り＋更新 RU）をスキーマ・テーブル・カラムの
    3階層で設定可能。下位階層への設定は上位階層の設定を上書きする（未設定の下位階層は
    上位階層の設定を継承する）。
  - **補助権限**（作成 C / 削除 D）を主権限とは独立に、スキーマ・テーブルの2階層で設定可能
    （下位優先の継承・上書きは主権限と同様）。
  - レコード作成は、対象テーブルの補助権限Cが有効、かつ主キーを構成する全カラムの主権限が
    RU（複合主キーはAND条件）である場合に許可する。主キーを持たないテーブルは例外的に、
    補助権限Cのみで作成を許可する（削除は常に不可）。
  - レコード削除は、対象テーブルの補助権限Dが有効、かつ主キーを構成する全カラムの主権限が
    R以上である場合に許可する。
  - 複数グループに所属するユーザは、グループ間で最も緩い権限を採用して合成する。
  - ユーザ個別設定が存在する階層は、グループ合成結果を上書きする。
  - 詳細な判定ロジックは `aidlc-docs/inception/application-design/component-methods.md`
    （`EffectivePermissionResolver`）を参照。
- 権限設定のYAMLエクスポート/インポートは接続単位で行う。

### 5.3 ユーザ認証
JWTベースの認証に変更（3.1参照）。管理者含む全ユーザはログイン（トークン取得）後に
各機能を利用する。

### 5.4 マスタメンテナンス機能
`docs/REQUIREMENTS.md` 5.4 のとおり。ただし対象は「選択中の対象RDBMS接続」配下の
テーブル/ビューであり、権限判定は3.3のとおり接続ごとに独立して行う。

### 5.5 クエリビルダー機能 / 5.6 クエリ保存機能 / 5.7 クエリ実行機能 / 5.8 クエリ履歴機能
`docs/REQUIREMENTS.md` の記載のとおり。保存クエリ・実行履歴は、どの対象RDBMS接続に対する
ものかを識別できるようにする（3.3の帰結）。

## 6. 監査ログ要件
`docs/REQUIREMENTS.md` セクション6のとおり。ログイン/ログアウトの記録方法はJWT前提に
読み替える（3.1参照）。対象RDBMS接続に関する操作ログは、どの接続に対する操作かを記録する。

## 7. 非機能要件
`docs/REQUIREMENTS.md` セクション7のとおり（同時利用者数約10名、WAR + Docker デプロイ、
MailPit/SMTP）。加えて：
- **i18n**: 3.6のとおり、日本語デフォルト・将来のi18n拡張を見据えた設計とする。
- **拡張ルール**: 4章のとおり、PBTルールを設計・実装・テストの各段階で適用する。

## 8. 未解決・後続フェーズで検討する事項
- ~~グループ権限とユーザ個別権限の優先順位・合成ルールの詳細（3.2）~~ →
  **Application Designで確定**（5.2、`aidlc-docs/inception/application-design/` 参照）
- 認証トークンの設定キー名・デフォルト有効期限（3.5） → キー名は
  `mm.app.auth.token-expiry-hours` に確定、デフォルト値（8時間を提案）はNFR Designで最終確定
- i18nライブラリの技術選定（3.6） → NFR Design
- ~~複数RDBMS接続を跨いだ管理UI/UXの詳細（3.3）~~ →
  **APIレベルは Application Design で確定**（`connectionId` を全APIパスで明示するステートレス
  設計）。画面遷移・UI詳細はUser Stories/Functional Designで具体化する

## 9. 変更要求: 接続コンテキストのグローバル化 + クエリ実行時スキーマ指定（2026-07-15）

CONSTRUCTION PHASE完了後、`/saved-queries`等へのナビゲーション直接アクセス時に接続を指定する
手段がない不具合報告を発端に、既存4ユニット（U3 RDBMS Connection & Schema Import、
U5 Master Data Maintenance、U6 Query Builder、U7 Saved Query / Execution / History）を横断する
変更要求として以下を確定した。新しいドメインの追加ではないため、新規ユニットは起こさず
既存ユニットへの変更として扱う（詳細な経緯は`aidlc-docs/audit.md`の該当エントリを参照）。

### 9.1 背景・動機
- `savedQuery`/`queryExecution`/`queryHistory`の3フロントエンド機能は、`connectionId`を
  URLクエリパラメータ経由でのみ受け取る実装だった（U7実装時の判断）。`AppLayout`のナビ
  リンクはこれらへ`connectionId`なしの裸のパスを指すため、ナビ経由で直接アクセスすると
  「接続が指定されていません」で行き止まりになっていた。
- 調査の結果、`masterData`（`/master-data`）と`queryBuilder`（`/query-builder`）は各ページ内に
  独立した接続選択UIを持つことで同じ制約（`connectionId`が全API必須というステートレス設計、
  3.3参照）に対応していたが、この2機能だけがそのパターンを踏襲していなかったことが
  根本原因と判明した。
- 議論の過程で、接続選択はページ単位ではなくユーザーセッション単位の関心事であるという分析に
  至り、「グローバル接続コンテキスト」の導入を決定した。
- 並行して、クエリビルダーが意図的にスキーマ非修飾のSQL（例: `SELECT * FROM customers`、
  再利用性を高めるための設計）を生成する一方、クエリ実行時にどのスキーマに対して実行するかを
  指定する手段がなく、対象RDBMSの接続プール設定（PostgreSQL/H2は`search_path`任せで実質
  `public`固定）に暗黙に依存してしまっていた問題も発見された（U6 `business-rules.md`で
  「リスクを許容する」と記載されていたが、U7で対処されていなかった）。これも本変更要求に
  含める。

### 9.2 決定事項

**接続コンテキストのグローバル化**:
- 接続選択は`AppLayout`に常設するグローバルセレクタで行う。値の永続化は`sessionStorage`
  （既存の`authStore`と同じ流儀、ログインセッション中のみ有効）とする（Q1=A）。
- グローバル接続を切り替えた際、詳細な作業状態（テーブル詳細画面、クエリビルダー編集中の
  モデル等）を持つ画面にいる場合は、各機能のトップページへ自動的に戻す（Q2=A）。
- 接続一覧取得APIは`rdbmsconnection`パッケージに1本化する。現状`masterdata`と`querybuilder`が
  同一ロジックをそれぞれ専用エンドポイントとして重複実装しているが、グローバル化により
  接続一覧を取得する箇所は実質グローバルセレクタ1箇所のみになるため、この機会に重複を解消する
  （Q3=B）。`aidlc-docs/inception/application-design/component-dependency.md`のマトリクス
  更新が必要。
- 既存の`/master-data`・`/query-builder`のページ内接続セレクタは廃止し、グローバルセレクタの
  みで操作する（ページ側は選択済みの`connectionId`を表示するのみ）（Q8=B）。U5/U6の
  Functional Design時点では接続選択のグローバル化までは見通せておらず、アプリ全体としての
  整合性を優先してページ内セレクタを廃止する判断とした。

**クエリ実行時のスキーマ指定・履歴記録**:
- `SavedQuery`エンティティにはスキーマを保存しない（SQLがスキーマ非依存で再利用可能という
  クエリビルダーの設計意図を維持するため）。
- 実行API（`POST /api/query-execution/adhoc`、`POST /api/query-execution/saved/{id}`）に
  `schema`を必須パラメータとして新規追加する。同じ保存クエリ・同じ手入力SQLを、実行のたびに
  異なるスキーマへ向けて実行できるようにする。
- `QueryHistory`エンティティに`schema`列を追加し、実行時に実際にどのスキーマへ向けて実行
  したかを記録する。本プロジェクトは未リリースで実データがないため、`NOT NULL`列として追加し、
  マイグレーション互換は考慮しない（Q7=A）。
- スキーマ選択UIは、クエリ実行画面に接続選択と同様の`<select>`を追加する。MySQL/MariaDB等
  スキーマが実質1つしかない接続（`CATALOG_BASED`）でも、選択肢が1件のセレクタを表示し
  ダイアレクトによるUI分岐は行わない（Q5=A、既存の`schema`/`masterData`/`queryBuilder`と
  同じ流儀）。
- クエリビルダーで選択中のスキーマは、実行画面・保存フォームへの遷移時にURLクエリパラメータで
  プリフィルし、遷移先で上書き可能にする（`connectionId`と同じ扱い）（Q6=A）。
- バックエンドの実行時解決: PostgreSQL/H2（`SCHEMA_BASED`）は実行直前に検証済みスキーマ名で
  `SET search_path`を発行する。MySQL/MariaDB（`CATALOG_BASED`）はスキーマが接続の
  `databaseName`で既に固定されているため対応不要。スキーマ名は識別子でありバインド
  パラメータ化できないため、`queryexecution`パッケージが`schema`パッケージの既存サービスに
  新規依存し、実行前にアクセス可能なスキーマの許可リストと突き合わせて検証する
  （Q4=A、`component-dependency.md`のマトリクス更新: `queryexecution → schema`追加）。

### 9.3 影響範囲
- **U3 RDBMS Connection & Schema Import**: `rdbmsconnection`パッケージへの接続一覧取得
  共通サービス/エンドポイントの新設。
- **U5 Master Data Maintenance**: `SchemaTableListPage`のページ内接続セレクタを廃止し、
  グローバルコンテキストから`connectionId`を取得する形に変更。
- **U6 Query Builder**: `QueryBuilderPage`のページ内接続セレクタを廃止（同上）。実行/保存への
  遷移時にスキーマもURLパラメータで引き継ぐよう変更。
- **U7 Saved Query / Execution / History**: `SavedQueryListPage`/`QueryExecutionPage`/
  `QueryHistoryListPage`をグローバル接続コンテキスト参照に変更。`QueryExecutionPage`に
  スキーマ選択UIを追加。`QueryHistory`エンティティへの`schema`列追加（DBスキーマ変更）。
  実行APIへの`schema`パラメータ追加。`QueryHistoryListPage`にスキーマ列を追加。
- **`aidlc-docs/inception/application-design/component-dependency.md`**: マトリクス更新
  （`queryexecution → schema`追加、接続一覧取得の依存関係整理）。

### 9.4 進め方
新規ユニットは起こさず、既存ユニット（U3/U5/U6/U7）への変更要求として、Requirements
Analysis（本節）→ User Stories要否判定 → Application Design（component-dependency.md改訂）
→ 影響ユニットのFunctional Design改訂・Code Generation再実施 → Build and Test再実行、という
軽量な再入場ルートで進める。