# U1 Platform Foundation - フロントエンドコンポーネントサマリ

Step 11（フロントエンドコンポーネント生成）・Step 12（フロントエンドコンポーネント単体テスト）で
生成した、共通基盤コンポーネントと`auditLog/`機能コンポーネントの一覧。

## 共通基盤（`src/api`, `src/store`, `src/hooks`, `src/routes`, `src/components`, `src/types`）

| ファイル | 役割 |
|---|---|
| `src/types/api.ts` | バックエンドDTOに対応する共通型（`PageRequest`, `PageResult<T>`, `ErrorResponse`）。 |
| `src/api/apiClient.ts` | `fetch`ラッパー`apiFetch<T>`。ベースURLなし（実行可能WARとしてSPAとバックエンドが同一オリジンで配信される前提の相対パス）、`authStore`のトークンから`Authorization`ヘッダを自動付与、`ErrorResponse`をパースして`ApiError`（`status`/`code`/`message`）としてthrow、`401`受信時は`authStore.logout()`呼び出し＋`window.location.href = '/login'`によるリダイレクト。 |
| `src/store/authStore.ts` | zustandストア`useAuthStore`。`currentUser: {id, email, role: Role} \| null`（`Role = 'ADMIN' \| 'USER'`。既存コードに定義がなく本ステップで新規定義）、`token: string \| null`、`login`/`logout`アクション。 |
| `src/hooks/useAuth.ts` | `authStore`から`currentUser`/`token`/`isAuthenticated`（`currentUser !== null && token !== null`）/`login`/`logout`を返すラッパーフック。 |
| `src/hooks/usePagination.ts` | ページング状態管理フック。`page`/`pageSize`/`totalCount`/`pageRequest`/`setTotalCount`/`goToPage`/`changePageSize`（ページサイズ変更時は`page`を`0`にリセット）。 |
| `src/routes/ProtectedRoute.tsx` | `requiredRole`prop対応の認可ガード。未認証時は`/login`へ、`requiredRole`指定かつロール不一致時は`/`へリダイレクト。 |
| `src/routes/AppRouter.tsx` | `BrowserRouter` + `AppLayout` + `Routes`。現時点では`/audit-logs`（`ProtectedRoute requiredRole="ADMIN"` → `AuditLogPage`）のみ登録。 |
| `src/components/AppLayout.tsx` | 全画面共通のナビゲーションレイアウト。ADMIN以外には監査ログリンクを表示しない。 |
| `src/components/DataTable.tsx` | 汎用テーブル`DataTable<T>`。列定義（`key`/`header`/`sortable?`/`render?`）に基づきヘッダ・行を描画、ソート可能列はボタン化。 |
| `src/components/Pagination.tsx` | ページ送りUI。前/次ボタン（境界で`disabled`）とページサイズ選択。 |
| `src/components/ToastNotification.tsx` | `severity`別（`info`/`success`/`warning`/`error`）のトースト表示、`role="alert"`。 |
| `src/components/ConfirmDialog.tsx` | 確認ダイアログ。確認/キャンセルボタンとコールバック。 |

## `auditLog/`機能（`src/features/auditLog`）

| ファイル | 役割 |
|---|---|
| `types.ts` | `EventCategory`/`EventType`（バックエンド`EventCategory`/`EventType`enumをミラーしたUnion型）、`AuditResult`、`EVENT_CATEGORY_OPTIONS`、カテゴリ→種別の絞り込みに使う`EVENT_TYPES_BY_CATEGORY`、`AuditLog`/`AuditLogFilter`インターフェース。 |
| `api.ts` | `searchAuditLogs(filter, pageRequest)`。`URLSearchParams`を組み立て`GET /api/audit-logs`を呼び出し`PageResult<AuditLog>`を返す。 |
| `AuditLogFilterPanel.tsx` | 絞り込み条件入力（開始/終了日時、ユーザID、カテゴリ、種別）。カテゴリ変更時は種別選択をリセット、`dateFrom > dateTo`の場合はエラー表示のみで`onFilterChange`を呼ばない。ユーザーID絞り込みは、U1時点でユーザー一覧取得APIが未実装のため`<select>`ではなく`<input type="number">`で実装（将来のユーザー管理機能実装時に真のドロップダウンへ置き換えを想定）。 |
| `AuditLogTable.tsx` | `DataTable`を利用した一覧表示。`eventCategory`はバッジ表示（`auditLogTable.css`で`design-tokens.css`のCSS変数を参照した色分け）。 |
| `AuditLogPage.tsx` | ページ全体の統合。`usePagination`・`AuditLogFilterPanel`・`AuditLogTable`・`Pagination`を結合し、フィルタ/ページ変更のたびに`searchAuditLogs`を再実行。ページサイズ選択肢（`[20, 50, 100]`）とデフォルト値（`20`）は、対応する設定公開APIが未実装のため`application.yml`の`mm.app.audit.*`値とハードコードで一致させている。 |
| `auditLogTable.css` | カテゴリ別バッジ配色（`audit-log-badge-authentication` → `--color-info`、`audit-log-badge-admin-operation` → `--color-warning`、`audit-log-badge-data-access` → `--color-success`）。 |

## `data-testid`一覧

| `data-testid` | コンポーネント | 対象要素 |
|---|---|---|
| `app-layout-nav` | `AppLayout` | ナビゲーション全体 |
| `app-layout-nav-home` | `AppLayout` | ホームリンク |
| `app-layout-nav-audit-logs` | `AppLayout` | 監査ログリンク（ADMIN限定） |
| `app-layout-nav-logout` | `AppLayout` | ログアウトボタン（認証済み限定） |
| `data-table-{列名}-header` | `DataTable` | 各列のヘッダセル（列キーごとに動的生成） |
| `data-table-sort-button` | `DataTable` | ソート可能列のヘッダ内ボタン |
| `pagination-prev-button` | `Pagination` | 前ページボタン（先頭ページで`disabled`） |
| `pagination-next-button` | `Pagination` | 次ページボタン（最終ページで`disabled`） |
| `pagination-page-size-select` | `Pagination` | ページサイズ選択 |
| `toast-notification-{severity}` | `ToastNotification` | トースト本体（`info`/`success`/`warning`/`error`ごとに動的生成） |
| `confirm-dialog-confirm-button` | `ConfirmDialog` | 確認ボタン |
| `confirm-dialog-cancel-button` | `ConfirmDialog` | キャンセルボタン |
| `audit-log-page` | `AuditLogPage` | ページルート要素 |
| `audit-log-filter-date-from-input` | `AuditLogFilterPanel` | 開始日時入力 |
| `audit-log-filter-date-to-input` | `AuditLogFilterPanel` | 終了日時入力 |
| `audit-log-filter-user-select` | `AuditLogFilterPanel` | ユーザID絞り込み入力（`input[type=number]`。将来的にドロップダウン化を想定） |
| `audit-log-filter-category-select` | `AuditLogFilterPanel` | 操作カテゴリ選択 |
| `audit-log-filter-type-select` | `AuditLogFilterPanel` | 操作種別選択（カテゴリ未選択時は`disabled`） |
| `audit-log-filter-error` | `AuditLogFilterPanel` | バリデーションエラーメッセージ |
| `audit-log-filter-search-button` | `AuditLogFilterPanel` | 検索ボタン |

## テストカバレッジ（Step 12）

| テストファイル | 検証内容 |
|---|---|
| `src/api/apiClient.test.ts` | 成功時のJSONパース、トークン保持時の`Authorization`ヘッダ付与、`401`時の自動ログアウト＋`/login`リダイレクト、エラーDTOパースによる`ApiError`（`status`/`code`/`message`）の送出（4件）。 |
| `src/store/authStore.test.ts` | 初期状態、`login`/`logout`によるstate遷移（3件）。 |
| `src/hooks/useAuth.test.ts` | 未認証初期状態、`login`後の`isAuthenticated`/`currentUser`/`token`反映、`logout`後の未認証復帰（3件、`renderHook`/`act`使用）。 |
| `src/hooks/usePagination.test.ts` | 初期値、`goToPage`、`changePageSize`（`page`を`0`にリセットする点を含む）、`setTotalCount`（4件）。 |
| `src/routes/ProtectedRoute.test.tsx` | 未認証リダイレクト、`requiredRole`不一致時のリダイレクト、ロール一致時・未指定時の`children`描画（4件、`MemoryRouter`使用）。 |
| `src/components/DataTable.test.tsx` | ヘッダ/セル描画、ソートボタンクリックによる`onSort`呼び出し、カスタム`render`関数の適用（3件）。 |
| `src/components/Pagination.test.tsx` | 前後ボタンの境界`disabled`、クリック時の`onPageChange`呼び出し、ページサイズ変更時の`onPageSizeChange`呼び出し（3件）。 |
| `src/components/ToastNotification.test.tsx` | 4種の`severity`ごとの`data-testid`とメッセージ表示（`it.each`による1パラメータ化テスト）。 |
| `src/components/ConfirmDialog.test.tsx` | メッセージ描画、確認/キャンセルボタンのコールバック呼び出し（3件）。 |
| `src/features/auditLog/AuditLogFilterPanel.test.tsx` | 全項目入力後の検索送信ペイロード（`userId`の数値変換含む）、カテゴリ変更時の種別リセット、`dateFrom > dateTo`時のバリデーションエラー表示と送信抑止（3件）。 |
| `src/features/auditLog/AuditLogTable.test.tsx` | ローディング表示、行描画とカテゴリバッジのクラス付与、null許容フィールドの`-`プレースホルダ表示（3件）。 |
| `src/features/auditLog/AuditLogPage.test.tsx` | 初期表示時の検索呼び出しと結果反映、フィルタ送信による再検索（2件、`./api`をモック化）。 |

全12ファイル・39テスト（`npm run test`でグリーン確認済み）。`npm run build`（`tsc -b && vite build`）・`npm run lint`（oxlint）も併せてエラーなし。