# business-rules.md — U1: Platform Foundation

`u1-platform-foundation-functional-design-plan.md` の回答（Q1〜Q7）に基づく業務ルール定義。

---

## 1. 監査記録（audit）

### 1.1 記録失敗時の扱い（Question 2 = A）
`AuditLogService.record(...)` の呼び出しが失敗（内部DB障害等）しても、呼び出し元の主業務処理
（ユーザ登録・権限変更・マスタ更新等）は失敗させない。例外は`AuditLogService`内で捕捉し、
アプリケーションログにエラー出力するのみとする。監査記録の可用性より主業務の可用性を優先する。

### 1.2 トランザクション境界（Question 3 = A）
対象RDBMSへの操作（`masterdata`/`queryexecution`等）と、監査記録（内部DBへの`AuditLog`書き込み）は
別接続・別トランザクションとする。対象RDBMS操作が成功・失敗・ロールバックのいずれであっても、
監査記録は独立して内部DBにコミットされる（1.1の「記録失敗が主処理をブロックしない」ことと対で、
「主処理の失敗が監査記録をブロックしない」ことも保証する）。

### 1.3 分類体系（Question 1 = A）
`EventCategory`（`AUTHENTICATION`/`ADMIN_OPERATION`/`DATA_ACCESS`）を固定enumとし、
`EventType`をそのサブ区分として固定enumで定義する（`domain-entities.md`参照）。
呼び出し側は自由文字列ではなく定義済みenumのいずれかを渡す。新規イベント種別が必要になった場合は
`EventType`への追加として扱う（コンパイル時検証を優先する設計判断）。

### 1.4 検索・一覧のデフォルト挙動（Question 4 = A + 追記）
- 既定の並び順は`occurredAt`降順（新しい順）。
- ページングは`common.PageRequest`/`PageResult<T>`の共通機構を使用する。
- **ページサイズの選択肢・既定値は`application.yml`で設定可能とする**（Question 4回答の追記事項）。
  例: `mm.app.audit.page-size-options: [20, 50, 100]`、`mm.app.audit.default-page-size: 20`
  （プロパティキー名は Code Generation 段階で確定）。
- 絞り込み条件: 日時範囲・ユーザ・操作種別（`eventCategory`/`eventType`）。ADM-6のAC通り。

### 1.5 閲覧権限
監査ログ閲覧（`AuditLogService.search`）は管理者ロールに限定する（アプリケーションレベルの
RBAC、対象RDBMSの権限モデルとは別軸）。一般ユーザは`auditLog/`画面にアクセスできない（ADM-6 AC）。

---

## 2. メール通知（mail）

### 2.1 テンプレート方式（Question 5 = B、AI提案）
Question 5でテンプレートエンジン導入（選択肢B）が指定され、具体案の提案が求められた。
**Thymeleaf**（`spring-boot-starter-thymeleaf` + `spring-boot-starter-mail`の組合せ）を提案する。

**提案理由**:
- Spring Bootとの統合が標準的で追加設定が少ない（`MailConfig`に`TemplateEngine` Beanを
  追加する程度）。
- HTMLメール（承認/却下通知等の可読性向上）とプレーンテキストメールの両方をテンプレートとして
  一元管理できる。
- 変数埋め込み（宛先名、確認リンクURL等）が`Context`オブジェクトへの変数設定のみで完結し、
  本プロジェクトの2種類のメール（登録確認・承認/却下結果通知）程度の規模であれば
  過剰な複雑性を持ち込まない。
- 開発環境（MailPit）・本番環境（SMTP環境変数）のいずれでも同一のテンプレート生成ロジックを
  再利用できる。

テンプレートファイルは `src/main/resources/templates/mail/` 配下に種別ごとに配置する想定
（`registration-confirmation.html`, `registration-approved.html`, `registration-rejected.html`）。
実際のファイル配置・命名規則はCode Generation段階で確定する。

### 2.2 送信トリガー
| `MailNotificationType` | トリガー | 対応ストーリー |
|---|---|---|
| `REGISTRATION_CONFIRMATION` | 新規メールアドレスでの登録申請受付時（`UserRegistrationService`） | MVP-1 |
| `REGISTRATION_APPROVED` | 管理者による承認操作時（`UserRegistrationService`） | MVP-4, MVP-5 |
| `REGISTRATION_REJECTED` | 管理者による却下操作時（`UserRegistrationService`） | MVP-4, MVP-5 |

### 2.3 送信失敗時の扱い（設計判断、Q2の方針を準用）
メール送信はベストエフォートとし、送信失敗（SMTP接続エラー等）は呼び出し元の主業務処理
（登録申請受付・承認/却下処理）を失敗させない。1.1と同様の理由（外部I/Oの可用性が主業務の
可用性を左右すべきではない）による設計判断であり、Functional Designの場で明示的に判断した
拡張ルールである（Question票では直接問うていない）。送信失敗は`AuditLogService`ではなく
アプリケーションログにのみ記録する（監査ログの対象は業務イベントであり、メール配送の
インフラ的失敗は対象外と整理する）。

---

## 3. 共通例外処理

### 3.1 HTTPステータスマッピング（Question 7 = A）
`@ControllerAdvice`による共通レスポンス変換規則:

| 例外 | HTTPステータス |
|---|---|
| `PermissionDeniedException` | 403 |
| `EntityNotFoundException` | 404 |
| `ValidationException` | 400 |
| 未捕捉の例外 | 500 |

レスポンスボディは共通エラーDTO形式: `{ "error": "<エラーコード>", "message": "<概要メッセージ>" }`

---

## 4. フロントエンド共通基盤の適用範囲（Question 6 = A + 追記）

U1のフロントエンド共通基盤には以下を含める。詳細なコンポーネント構造は
`frontend-components.md`を参照。

- APIクライアント（JWT付与・401時の自動ログアウト等の共通インターセプタ）
- 認証状態管理（ログイン中ユーザ情報・ロールの保持）
- 共通レイアウト（ヘッダー/ナビゲーション、管理者専用メニューの出し分け）
- ルーティング基盤（未認証時のログイン画面リダイレクト等）
- 共通UIコンポーネント（テーブル・ページング・トースト通知等）
- **デザインシステム**: 配色・サイズをCSS変数として別ファイルに定義し、カスタマイズを
  容易にする（Question 6回答の追記事項。詳細は`frontend-components.md`参照）