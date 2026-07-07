# business-logic-model.md — U1: Platform Foundation

`domain-entities.md`・`business-rules.md`で定義したモデル・ルールに基づく業務フロー。
技術非依存（実装方式はNFR Design/Code Generationで確定）。

---

## フロー1: 監査記録（他ユニットから横断的に呼び出される）

**関与コンポーネント**: 任意ユニットのサービス（呼び出し元） → `AuditLogService`（U1）

1. 呼び出し元サービス（例: U2の`UserRegistrationService`、U5の`MasterDataMutationService`）が、
   自身の業務処理（成功・失敗を問わず）の完了時点で `AuditLogService.record(eventCategory,
   eventType, userId, connectionId, result, targetDescription, summaryMessage)` を明示的に
   呼び出す（AOP不使用、`application-design.md` Question 5 = Bを継承）。
2. `AuditLogService`は`AuditLog`エンティティを組み立て、内部DBへの書き込みを試みる。
   - 対象RDBMS操作のトランザクションとは別トランザクション（`business-rules.md` 1.2）。
   - 呼び出し元の主業務処理がロールバックしていても、監査記録は独立してコミットされる。
3. 書き込みが失敗した場合、`AuditLogService`内で例外を捕捉し、アプリケーションログに
   エラー出力する。呼び出し元へは例外を伝播させない（`business-rules.md` 1.1）。
4. 呼び出し元は`AuditLogService.record(...)`の戻りを待たず（または戻り値を無視して）、
   自身の処理結果（成功レスポンス／エラーレスポンス）をControllerに返す。

---

## フロー2: 監査ログの閲覧・絞り込み（ADM-6）

**関与コンポーネント**: フロントエンド`auditLog/` → `AuditLogService.search`（U1）

1. 管理者が`auditLog/`画面を開く（一般ユーザは`SecurityConfig`のエンドポイント認可により
   アクセス不可、`business-rules.md` 1.5）。
2. 画面初期表示時、絞り込み条件なしで`AuditLogService.search(pageRequest)`を呼び出す。
   既定は`occurredAt`降順、既定ページサイズ（`application.yml`設定値）でページング表示。
3. 管理者が日時範囲・ユーザ・操作種別（`eventCategory`/`eventType`）を指定して再検索すると、
   `AuditLogService.search(filter, pageRequest)`が呼び出され、条件に合致する`AuditLog`のみが
   `PageResult<AuditLog>`として返される。
4. 一覧は`eventCategory`（認証イベント/管理操作/データアクセスイベント）で視覚的に区別して
   表示する。

---

## フロー3: ユーザ登録に伴うメール通知（MVP-1, MVP-4, MVP-5）

**関与コンポーネント**: U2の`UserRegistrationService` → `MailService`（U1）

1. **登録申請受付時**（MVP-1）: `UserRegistrationService`が新規メールアドレスの登録申請を
   受け付けると、`RegistrationTokenService`で確認トークンを発行した後、
   `MailService.send(MailNotificationType.REGISTRATION_CONFIRMATION, 宛先, トークン付きリンク)`
   を呼び出す。
2. `MailService`はThymeleafテンプレート（`business-rules.md` 2.1）に変数（宛先名、リンクURL、
   有効期限）を埋め込みメール本文を生成し、設定済みのSMTP（開発: MailPit、本番: 環境変数、
   `MailConfig`）経由で送信する。
3. 送信が失敗しても`UserRegistrationService`の登録申請受付処理自体は成功として扱う
   （`business-rules.md` 2.3）。送信失敗はアプリケーションログにのみ記録する。
4. **承認/却下時**（MVP-4, MVP-5）: 管理者が承認/却下操作を行うと、`UserRegistrationService`が
   ユーザ状態を更新した後、`MailService.send(REGISTRATION_APPROVED または
   REGISTRATION_REJECTED, 宛先)`を呼び出す。文面はテンプレートにより種別ごとに異なる
   （`business-rules.md` 2.2）。
5. いずれの操作（登録申請受付、承認、却下）も完了後に`AuditLogService.record(...)`
   （フロー1）を呼び出し、`ADMIN_OPERATION`カテゴリのイベントとして記録する（承認/却下時）。

---

## フロー4: 共通例外のレスポンス変換

**関与コンポーネント**: 全ユニットのController（U1提供の`@ControllerAdvice`が横断適用）

1. 任意ユニットのServiceが業務ルール違反（権限不足・対象不存在・入力不正等）を検知すると、
   `common`の共通例外（`PermissionDeniedException`等）をスローする。
2. `@ControllerAdvice`（U1の`config`パッケージ、または`common`パッケージに配置）がこれを捕捉し、
   `business-rules.md` 3.1のマッピングに従いHTTPステータスと共通エラーDTOに変換して
   フロントエンドへ返す。
3. フロントエンド共通基盤のAPIクライアント（`frontend-components.md`参照）が、401
   （未認証・トークン期限切れ）の場合は自動ログアウト＋ログイン画面へのリダイレクトを行う
   （`business-rules.md` 4節、Question 6回答）。403/404/400はAPIクライアント経由で呼び出し元の
   画面コンポーネントにエラー情報を伝え、画面側でエラーメッセージ・トースト通知として表示する。

---

## テスト可能な性質（Testable Properties, PBT-01）

`property-based-testing`拡張（enabled）のRule PBT-01に基づき、本ユニットの業務ロジック
（フロー1〜4）が持つ性質をカテゴリ別に識別する。実際のPBTケース設計・生成器定義は
Code Generation計画時に確定する。

| # | 対象 | カテゴリ | 性質 | 備考 |
|---|---|---|---|---|
| P1 | `AuditLogService.record`（フロー1） | Invariant（業務ルール不変条件） | 内部DB書き込みが失敗しても例外は呼び出し元に伝播しない。任意の入力（正常値・境界値・書き込み失敗を模したモック）で常に正常終了する | `business-rules.md` 1.1。PBT-03該当 |
| P2 | `AuditLogService.record`（フロー1） | Round-trip | 書き込んだ`AuditLog`の全フィールドが、直後の読み出しで同一値として得られる（DB write/read、PBT-02が明示的に対象とする「データ変換層のDB書き込み/読み出し」に該当） | `domain-entities.md`のフィールド定義が対象 |
| P3 | `AuditLogService.search`（フロー2） | Invariant | 返却される`PageResult<AuditLog>`の全要素が指定フィルタ条件（日時範囲・ユーザ・eventCategory/eventType）を満たす。フィルタ未指定時は全件が対象範囲になる | `business-rules.md` 1.4 |
| P4 | `AuditLogService.search`（フロー2） | Invariant（順序保証） | 返却される要素は`occurredAt`降順で整列されている | `business-logic-model.md` フロー2 |
| P5 | `AuditLogService.search`（フロー2） | Range constraint | 1ページの要素数は指定/既定ページサイズ（`application.yml`設定値の範囲内）を超えない | `business-rules.md` 1.4 |
| P6 | `MailService.send`（フロー3） | Invariant | 送信（SMTP呼び出し）が失敗しても例外は呼び出し元（`UserRegistrationService`）に伝播せず、主業務処理は成功として扱われる | `business-rules.md` 2.3。P1と同型の不変条件 |
| P7 | `MailService.send`（フロー3） | Invariant（テンプレート変数埋め込み） | Thymeleafテンプレートに渡した変数（宛先名・リンクURL・有効期限等）は、生成されたメール本文に必ず出現し、未解決のプレースホルダ（`${...}`等）が本文に残らない | `business-rules.md` 2.1-2.2 |
| P8 | `@ControllerAdvice`共通例外変換（フロー4） | Oracle（参照マッピング比較） | 任意の共通例外インスタンスに対し、変換後のHTTPステータスコードは`business-rules.md` 3.1のマッピング表と常に一致する（決定的関数） | PBT-05該当。マッピング表自体がオラクル |
| P9 | `DialectStrategy`系（`common/dialect`） | （本ユニットではNo PBT properties identified） | 対象RDBMS方言吸収の具体的アルゴリズムは技術的関心事としてFunctional Designのスコープ外（本plan「ユニット適用可否の判定」）。方言別SQL生成の性質識別（例: 生成されたLIMIT/OFFSET句が各方言で構文的に妥当）はこの機能を実装するCode Generation計画時に改めて識別する | スコープ外のため本stageではN/A |

**PBT-09（フレームワーク選定）**: Java実装のため`jqwik`（JUnit 5統合）を候補として
NFR Requirementsステージで確定する。