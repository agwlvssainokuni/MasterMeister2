# Integration Test Instructions

## Purpose

U1〜U7の各ユニットは`component-dependency.md`で定義された一方向依存関係を持つ（例:
`queryexecution → common, audit, rdbmsconnection, queryhistory, savedquery`）。単体テストは
各ユニット境界内で依存先をMockito/実DBに置き換えて検証済みだが、実際のHTTPリクエスト〜
JPA〜対象RDBMS（JDBC）までを通しで検証する統合テストは未実施のため、本ドキュメントで手順を
定義する。

対象RDBMSは4種（MySQL・MariaDB・PostgreSQL・H2）をサポートするため、`DialectStrategy`
（方言差異の吸収層）が実際に機能することを、最低でもMySQL・PostgreSQLの2種で確認することを
推奨する（H2は単体テストで常用しているため個別の統合テストとしての価値は相対的に低い）。

## Setup Integration Test Environment

### 1. Start Required Services

```bash
cd devenv
docker compose up -d
docker compose ps   # mailpit / mysql / mariadb / postgres が Up であることを確認
```

本環境ではDockerデーモンが利用可能であることを確認済み（`docker compose version` →
Docker Compose version 5.3.0が応答）。`CLAUDE.md`記載の「Docker daemon未検証」は本ステージ
時点では解消している。

### 2. Configure Environment Variables

```bash
export MM_APP_JWT_SECRET="integration-test-secret-please-change-32bytes-min"
export MM_APP_RDBMS_CONNECTION_ENCRYPTION_KEY="$(openssl rand -base64 32)"
export MM_APP_ADMIN_BOOTSTRAP_EMAIL="admin@example.com"
export MM_APP_ADMIN_BOOTSTRAP_PASSWORD="ChangeMe123!"
```

### 3. Start the Application

```bash
cd backend
./gradlew bootRun
```

```bash
cd frontend
npm run dev
```

MailPit Web UI（`http://localhost:8025`）でメール送信内容（登録確認・パスワード設定リンク等）
を確認できる。

## Test Scenarios

### Scenario 1: ユーザ登録 → 承認 → ログイン（U1 ↔ U2）

- **Description**: メールファーストの2段階登録フロー全体と、内部DB（H2）＋MailPit連携の確認
- **Setup**: 上記アプリケーション起動済み、未登録メールアドレスを1つ用意
- **Test Steps**:
  1. フロントエンド`/register`で未登録メールアドレスを送信
  2. MailPit（`http://localhost:8025`）に確認メールが届くことを確認
  3. メール内リンクからパスワード設定画面（`/register/complete`）へ遷移しパスワードを設定
  4. 管理者（bootstrap admin）でログインし`/admin/pending-users`で承認待ちユーザーを承認
  5. 承認されたユーザーでログインできることを確認
  6. `/admin/audit-logs`で`USER_REGISTRATION_APPROVED`・`LOGIN_SUCCESS`イベントが記録されて
     いることを確認（U1監査ログとU2認証フローの連携確認）
- **Expected Results**: 各ステップが例外なく完了し、監査ログに一連のイベントが記録される
- **Cleanup**: 作成したテストユーザーは論理削除・物理削除いずれの機構も持たないため、
  再実行時は別メールアドレスを使用するか、H2ファイル（`backend/data/mastermeister.mv.db`）を
  削除してアプリケーションを再起動する

### Scenario 2: RDBMS接続設定 → スキーマ取り込み → 権限設定 → マスタデータ編集（U3 → U4 → U5）

- **Description**: 対象RDBMS接続の暗号化保存、スキーマメタデータ取り込み、テーブル/カラム
  単位権限、マスタデータCRUD（統一ミューテーションAPI）までの通しフロー
- **Setup**: `devenv`のMySQL（`localhost:3306`、db/user/password=`mastermeister`）を対象とする
- **Test Steps**:
  1. 管理者で`/admin/rdbms-connections/new`からMySQL接続を登録（接続情報はAES暗号化されて
     内部DBに保存される、`EncryptedStringConverter`）
  2. `/admin/schema/{connectionId}`でスキーマ取り込みを実行し、テーブル・カラムメタデータが
     内部DBにキャッシュされることを確認
  3. `/admin/permissions`で一般ユーザーグループに対象テーブルのREAD/UPDATE権限を付与
  4. 一般ユーザーでログインし`/master-data`から対象テーブルを開き、フィルタ・ページング・
     セル編集・新規行追加・削除チェック・「反映」（統一ミューテーションAPI、単一トランザクション）
     が正しく動作することを確認
  5. 権限がREADのみのカラムが編集不可（読み取り専用表示）であることを確認
  6. `/admin/audit-logs`で`RDBMS_CONNECTION_CHANGED`・`SCHEMA_IMPORTED`・`PERMISSION_CHANGED`・
     `MASTER_DATA_MUTATION`イベントが記録されていることを確認
- **Expected Results**: MySQLへの実際のCRUDが成功し、対象RDBMS上のデータが変更される
- **Cleanup**: MySQL上のテストデータをロールバックまたは削除、内部DBの接続設定・権限設定・
  スキーマキャッシュを削除（管理画面から、または`docker compose down -v`でMySQLごと破棄して
  再作成）

### Scenario 2': 同一シナリオをPostgreSQL・MariaDBで実施（方言差異の確認）

- **Description**: Scenario 2をPostgreSQL（`localhost:5432`）・MariaDB（`localhost:3307`）
  それぞれに対して実施し、`DialectStrategy`によるSQL方言差異の吸収（識別子クオート文字、
  LIMIT/OFFSET構文等）が正しく機能することを確認する
- **Test Steps**: Scenario 2と同一（接続先のみ変更）
- **Expected Results**: いずれの対象RDBMSでもマスタデータCRUD・クエリビルダー生成SQLの実行が
  成功する

### Scenario 3: クエリビルダー → 保存 → 実行 → 履歴（U6 → U7）

- **Description**: SQL生成・保存クエリ・アドホック実行・実行履歴の一連の連携（U6↔U7連携、
  `business-rules.md` 6節のURLクエリパラメータ方式）
- **Setup**: Scenario 2で取り込み済みのMySQL接続・スキーマを利用
- **Test Steps**:
  1. `/query-builder`で対象接続・スキーマを選択し、FROM/JOIN・SELECT・WHERE等の各タブでSQLを
     組み立て「SQL生成」を実行
  2. 生成SQLパネルの「保存」ボタンから`/saved-queries/new`へ遷移し（`rawSql`・`connectionId`
     が引き継がれることを確認）、名前を付けて保存（Public/Privateいずれも確認）
  3. `/saved-queries`一覧から保存したクエリを「実行」し、`/query-execution`で
     パラメータ自動検出・実行結果表示・実行回数（`executionCount`）増分を確認
  4. `/query-history`で実行履歴が記録されていること、Public/Privateの可視性ルール・
     マスキング（他ユーザーのPrivate保存クエリ実行を別ユーザーが閲覧した場合）・
     「廃止済み」バッジが正しく表示されることを確認（別の一般ユーザーアカウントで検証）
  5. 履歴の1行から「再実行」「保存」「ビルダーで編集」の各ボタンで正しい画面に
     `rawSql`/`connectionId`付きで遷移することを確認
- **Expected Results**: 生成→保存→実行→履歴のサイクル全体が一貫して動作し、可視性・
  マスキングルールが`business-rules.md`どおりに機能する

### Scenario 4: 読み取り専用SQL検証・大量データ・タイムアウト（U6/U7横断の防御機構）

- **Description**: クエリビルダー（U6）とアドホックSQL実行（U7）双方の防御的機構
  （読み取り専用検証、パース/実行タイムアウト、件数上限）を実データで確認
- **Test Steps**:
  1. `/query-execution`に更新系SQL（`DELETE FROM ...`等）を直接入力して実行し、400エラー
     （`ValidationException`、`読み取り専用SQL（SELECT文）1件のみ実行できます`）が返る
     ことを確認
  2. スタックドクエリ（`SELECT 1; DROP TABLE x`）を入力し同様に拒否されることを確認
     （U7 Step 3で発見・修正済みのケース）
  3. `mm.app.query-execution.max-result-rows`（既定1000件）を超える結果を返すSQLを実行し、
     `truncated=true`で打ち切られることを確認
- **Expected Results**: いずれのケースも例外・エラーレスポンスが適切に返り、アプリケーションが
  異常終了しない

## Run Integration Tests

上記シナリオは自動化されたテストコードとしては未実装（本プロジェクトの単体テストは各ユニット
境界内で完結させる方針を貫いており、U1〜U7いずれのCode Generationステージでも自動統合テストの
生成は計画に含まれていない）。手動での実施、または将来的にPlaywright等でE2E化する場合は
`e2e-test-instructions.md`の方針を参照。

### 1. Execute Integration Test Suite

上記Scenario 1〜4を手動で（または自動化後は該当コマンドで）実行する。

### 2. Verify Service Interactions

- **Test Scenarios**: Scenario 1〜4（本ドキュメント記載の4シナリオ、うちScenario 2は
  MySQL/PostgreSQL/MariaDBで反復）
- **Expected Results**: 上記各シナリオの「Expected Results」参照
- **Logs Location**: バックエンドは`bootRun`の標準出力（SQLログは既定で無効、必要に応じ
  `logging.level.org.springframework.jdbc=DEBUG`等を追加）、対象RDBMSのログは
  `docker compose logs mysql`等で確認

### 3. Cleanup

```bash
cd devenv
docker compose down        # コンテナ停止（データボリュームは保持）
docker compose down -v     # データボリュームも含めて完全に破棄する場合
```