# Functional Design Plan — U1: Platform Foundation

## ユニット適用可否の判定

`construction/functional-design.md` の実行/スキップ判定基準に基づき、U1は **実行（EXECUTE）** と判定。

- `common`（`DialectStrategy`等）・`config`（`SecurityConfig`等）は技術的関心事のみで
  業務ドメインモデルを持たないため、これらに関する Functional Design は不要。
- `audit`（`AuditLogService`）は監査イベントのドメインモデル（種別・記録項目）・記録/検索の
  業務ルールを要する。
- `mail`（`MailService`）は通知トリガー・テンプレート選択の業務ルールを要する。
- → Functional Design のスコープは **`audit` + `mail` の業務ロジック**、および
  U1が提供するフロントエンド共通基盤 + `auditLog/`画面のコンポーネント設計に限定する。

---

## Step 1: ユニットコンテキストの確認

- [x] `unit-of-work.md` U1節を確認（責務・主要コンポーネント: `AuditLogService`, `MailService`,
      `DialectStrategy`系, `PageRequest`/`PageResult<T>`, 共通例外群, `SecurityConfig`等）
- [x] `unit-of-work-story-map.md` U1節を確認（対応ストーリー: ADM-6。加えて全ユニット横断の
      「監査ログに記録される」AC群がU1提供の`AuditLogService.record`に依存）

---

## 成果物生成タスク（`construction/functional-design.md` Step 6）

- [x] `aidlc-docs/construction/u1-platform-foundation/functional-design/business-logic-model.md`
- [x] `aidlc-docs/construction/u1-platform-foundation/functional-design/business-rules.md`
- [x] `aidlc-docs/construction/u1-platform-foundation/functional-design/domain-entities.md`
- [x] `aidlc-docs/construction/u1-platform-foundation/functional-design/frontend-components.md`
      （共通基盤: APIクライアント・認証状態管理・レイアウト/ナビゲーション、および `auditLog/` 画面）

---

## Question 1: Business Logic Modeling — 監査イベントの種別体系

ADM-6のACは「認証イベント・管理操作・データアクセスイベント」の3分類での一覧表示を要求している。
各種別に属する具体的なイベント（例: 認証イベント=ログイン成功/失敗・ログアウト、管理操作=登録承認/却下・
接続設定変更・スキーマ取り込み・権限変更・YAMLインポート、データアクセスイベント=大量データ取得・
マスタ更新・クエリ実行）を固定enumとして定義し、`AuditLogService.record(eventType, ...)` の
`eventType` として扱ってよいか。

A. 固定enum（`AuthenticationEvent` / `AdminOperation` / `DataAccessEvent` の3カテゴリ、
   カテゴリ内はさらに具体的な操作種別のサブenum）として定義する（推奨）
B. カテゴリのみ固定enumとし、具体的な操作種別は自由文字列（呼び出し側が任意の文字列を渡す）とする
C. その他（自由記述で分類方針を指定）

[Answer]:

A

---

## Question 2: Business Rules — 監査記録の失敗時挙動

`AuditLogService.record(...)` の呼び出し自体が失敗した場合（内部DB障害等）、呼び出し元の
主業務処理（ユーザ登録・権限変更・マスタ更新等）に影響を与えてよいか。

A. 監査記録の失敗は主業務処理に影響させない（例外を握りつぶし、ログにエラー出力するのみ。
   監査記録の可用性より主業務の可用性を優先）（推奨）
B. 監査記録の失敗は主業務処理全体を失敗させる（監査記録も含めて単一トランザクションとして扱う）
C. その他（具体的な挙動を指定）

[Answer]:

A

---

## Question 3: Data Flow — 監査記録のトランザクション境界

`component-dependency.md` のデータフロー図2（統一マスタ更新API）では、対象RDBMSへの実行が
失敗しロールバックした後に `AuditLogService.record(失敗)` を呼び出す流れが示されている。
これは「対象RDBMS操作のトランザクション」と「監査記録（内部DB）」が別トランザクションである
ことを意味するが、この理解でよいか。

A. その理解で正しい。対象RDBMSと内部DBは別接続・別トランザクションであり、監査記録は
   対象RDBMS操作の成否に関わらず独立して内部DBにコミットされる（推奨、Question 2 = Aとも整合）
B. 異なる（具体的なトランザクション境界を指定）

[Answer]:

A

---

## Question 4: Business Scenarios — 監査ログ検索のデフォルト挙動

ADM-6は「日時・ユーザ・操作種別で絞り込める」とあるが、フィルタ未指定時のデフォルト表示件数・
並び順・ページングについて確認したい。

A. デフォルトは直近分から新しい順（降順）に、`common.PageRequest`/`PageResult`の共通ページング機構で
   表示する（既定ページサイズはUnit全体で他の一覧系APIと同じ既定値を踏襲）（推奨）
B. その他（具体的なデフォルト値・並び順を指定）

[Answer]:

A

ページサイズの選択肢、規定値はapplication.ymlで定義する。

---

## Question 5: Integration Points — メール通知のテンプレート方式

`MailService` が送る2種類のメール（登録確認・承認/却下結果通知）のテンプレート管理方式を
確認したい。

A. アプリケーション内にテキスト/HTMLテンプレートを直接保持し、シンプルな変数埋め込み
   （宛先名・リンクURL等）で生成する。外部テンプレートエンジンの導入は不要（推奨、
   本プロジェクトのメール種別は2つのみで複雑なテンプレート管理は過剰と判断）
B. Thymeleaf等のテンプレートエンジンを導入する
C. その他（具体的な方式を指定）

[Answer]:

B

テンプレートエンジンを提案して欲しい。

---

## Question 6: Frontend Components — 共通基盤の範囲

U1は`features/`外の共通基盤（`components/`, `api/`, `hooks/`, `store/`, `routes/`）を含む。
これには以下が含まれると理解してよいか。

- APIクライアント（JWT付与・401時の自動ログアウト等の共通インターセプタ）
- 認証状態管理（ログイン中ユーザ情報・ロールの保持）
- 共通レイアウト（ヘッダー/ナビゲーション、管理者専用メニューの出し分け）
- ルーティング基盤（未認証時のログイン画面リダイレクト等）
- 共通UIコンポーネント（テーブル・ページング・トースト通知等、他ユニットの画面から再利用される部品）

A. 上記すべてをU1のフロントエンド共通基盤に含める（推奨。他の全ユニットの画面実装が
   これらに依存するため、最初期に確立する必要がある）
B. 一部を他ユニットに委ねる、または対象を絞る（具体的に指定）

[Answer]:

A

デザインシステムの作成にあたっては、配色、サイズのカスタマイズがしやすくなるよう、CSS変数を別ファイルで定義すること。

---

## Question 7: Error Handling — 共通例外のHTTPステータスマッピング

`common`パッケージの共通例外群（`PermissionDeniedException`, `EntityNotFoundException`,
`ValidationException`等）を`@ControllerAdvice`で共通レスポンス形式に変換する際の
HTTPステータスコード対応を確認したい。

A. 標準的なマッピングとする（`PermissionDeniedException`→403, `EntityNotFoundException`→404,
   `ValidationException`→400, 未捕捉の例外→500、レスポンスボディは
   `{ "error": "...", "message": "..." }` 形式の共通エラーDTO）（推奨）
B. その他（具体的なマッピング・レスポンス形式を指定）

[Answer]:

A

---

## 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください。全ての質問に回答後、その旨を伝えてください。