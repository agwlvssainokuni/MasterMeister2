# プロジェクトディレクトリ構成

[docs/REQUIREMENTS.md](./REQUIREMENTS.md) の要件に基づくディレクトリ構成。U1〜U7 の全ユニット実装が完了した現在の as-built 構成を反映する。

## トップレベル構成

要件（4章）で指定されている3分割を採用する。

```
MasterMeister2/
├── backend/        # Spring Boot アプリケーション (Java 25, Gradle 9.6)
├── frontend/       # React アプリケーション (Vite)
├── devenv/         # 開発環境 (Docker Compose)
├── docs/           # ドキュメント（要件定義など）
├── LICENSE
└── README.md
```

## backend/ 構成

機能数が多く（登録/承認、RDBMS接続管理、スキーマ取込、権限、マスタメンテ、クエリビルダー、保存クエリ、クエリ実行/履歴、監査ログ）、単独開発かつMVP段階的リリースという方針のため、レイヤー別ではなく**機能（ドメイン）別パッケージ**を採用し、機能単位で追加・変更しやすくする。

```
backend/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/ , gradlew, gradlew.bat
└── src/
    ├── main/
    │   ├── java/cherry/mastermeister/
    │   │   ├── MasterMeisterApplication.java
    │   │   ├── config/           # 共通設定 (JpaConfig, DataSourceConfig 等。MailはSpring Boot自動設定に委ねる)
    │   │   ├── common/           # 共通レスポンス, ページング等のユーティリティ
    │   │   │   ├── dialect/      # 対象RDBMS方言吸収 (DialectStrategy, DB種別ごとの実装)
    │   │   │   └── exception/    # 共通例外 (ValidationException, EntityNotFoundException 等)
    │   │   ├── security/         # JWT認証 (SecurityConfig, JwtAuthenticationFilter, JwtTokenValidator,
    │   │   │                     #  RestAuthenticationEntryPoint, RestAccessDeniedHandler)
    │   │   │                     #  U1 NFR Design（logical-components.md）で確定した専用パッケージ
    │   │   ├── auth/             # 5.3 ユーザ認証（ログイン/セッション or JWT）
    │   │   ├── userregistration/ # 5.1 ユーザ登録（申請→メール→承認/却下）
    │   │   ├── rdbmsconnection/  # 5.2 対象RDBMS接続情報管理
    │   │   ├── schema/           # 5.2 スキーマ取込（テーブル/ビュー/カラム構造の読取）
    │   │   ├── group/            # 5.2 ユーザグループの作成・所属管理（userregistrationのUser/
    │   │   │                     #  UserRepositoryに依存。所属ユーザの実体はuserregistrationが
    │   │   │                     #  所有し、groupは所属関係のみを保持する一方向依存）
    │   │   ├── permission/       # 5.2 テーブル/カラム権限、YAMLエクスポート/インポート（groupに依存、
    │   │   │                     #  PrincipalType.GROUP解決のためlistGroupsを一方向で参照）
    │   │   ├── masterdata/       # 5.4 マスタ一覧・絞込・編集・作成/削除の統一API
    │   │   ├── querybuilder/     # 5.5 クエリビルダー（SQL生成/逆解析）
    │   │   ├── savedquery/       # 5.6 クエリ保存（公開/非公開、実行、編集権限）
    │   │   ├── queryexecution/   # 5.7 クエリ実行（パラメータ化, ページング）
    │   │   ├── queryhistory/     # 5.8 クエリ履歴（一覧・絞込）
    │   │   ├── audit/            # 6章 監査ログ記録・参照（管理者のみ）
    │   │   └── mail/             # メール送信（登録確認, 承認結果通知）
    │   └── resources/
    │       ├── application.yml
    │       ├── logback-spring.xml
    │       └── static/           # frontendビルド成果物の組込先（実行可能WAR化）
    │       # 内部DB(H2)のスキーマ管理はFlyway/Liquibase等のマイグレーションツールを
    │       # 導入せず、JPAの自動DDL生成（spring.jpa.hibernate.ddl-auto）に委ねる
    │       # （U1 NFR Design Question 5 = A で確定。db/migration/ は使用しない）
    └── test/
        └── java/cherry/mastermeister/...  # 各機能パッケージに対応
```

- 各機能パッケージ内部はレイヤー別サブディレクトリを作らず、フラットに `XxxController` / `XxxService` / `XxxRepository` / エンティティ / DTO のファイルを直接配置する（機能ごとに縦割り、パッケージ内はファイル単位）。フロントエンドの `features/xxx/` と同じ「ディレクトリ名がすでにフィーチャーを表すため、単一目的のサブディレクトリは作らない」方針をバックエンドにも適用したもの。
- 対象RDBMS（MySQL/MariaDB/PostgreSQL/H2）の方言差異（識別子クォート、ページング句、NULLソート順、スキーマ/カタログ解釈等）は、パッケージごとに個別実装せず `common/dialect/` に `DialectStrategy`（Strategyパターン）として一元化する（Application Designで確定。`rdbmsconnection` / `schema` / `masterdata` / `querybuilder` / `queryexecution` が共通で参照する）。

## frontend/ 構成

バックエンドの機能単位に対応させ、featureごとに画面・APIクライアント・状態管理をまとめる。

```
frontend/
├── package.json, vite.config.ts, tsconfig.json
└── src/
    ├── main.tsx, App.tsx
    ├── routes/                  # 画面遷移定義
    ├── features/
    │   ├── auth/                # ログイン
    │   ├── userRegistration/    # 登録申請〜承認画面（一般/管理者）
    │   ├── rdbmsConnection/     # RDBMS接続設定（管理者）
    │   ├── schema/              # スキーマ取込（管理者）
    │   ├── group/               # グループ作成・所属管理（管理者）
    │   ├── permission/          # 権限設定・YAML入出力（管理者）
    │   ├── masterData/          # テーブル一覧・レコード一覧編集
    │   ├── queryBuilder/        # クエリビルダー
    │   ├── savedQuery/          # 保存クエリ管理
    │   ├── queryExecution/      # クエリ実行
    │   ├── queryHistory/        # クエリ履歴
    │   └── auditLog/            # 監査ログ閲覧（管理者）
    ├── components/              # 共通UIコンポーネント
    ├── api/                     # 共通APIクライアント（axios/fetch設定, 型付きエンドポイント）
    ├── hooks/                   # 共通カスタムフック（useConnection: 選択中RDBMS接続の参照等）
    ├── store/                   # グローバル状態管理（authStore: 認証状態, connectionStore: 選択中RDBMS接続。sessionStorageに永続化）
    ├── styles/                  # 共通デザイントークン・プリミティブCSS（design-tokens.css, app.css）
    ├── test/                    # テストセットアップ（setup.ts）
    └── types/                   # API型定義（バックエンドDTOに対応）
```

- 各 `features/xxx/` 配下はフラット構成を基本とする（`api.ts`, `types.ts`, 各コンポーネント/フックのファイルを `features/xxx/` 直下に配置）。ディレクトリ名がすでにフィーチャーを表すため、`api/xxxApi.ts` のような単一ファイルだけのサブディレクトリは作らない。ファイル数が増えて分割が必要になった場合のみ、`components/` や `hooks/` などのサブディレクトリを導入する（U1〜U7完了時点では全フィーチャーがフラット構成のまま）。

## devenv/ 構成

```
devenv/
├── docker-compose.yml   # MailPit, MySQL, MariaDB, PostgreSQL をまとめて起動
├── mysql/init/          # 初期化SQL（任意）
├── mariadb/init/
└── postgres/init/
```

H2はアプリ組込のためコンテナ不要（要件どおり）。