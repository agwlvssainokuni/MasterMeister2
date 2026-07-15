# frontend-components.md — U1: Platform Foundation

`business-rules.md` 4節（Question 6 = A + 追記）に基づく、`features/`外の共通基盤と
`auditLog/`機能のコンポーネント設計。

---

## デザインシステム（Question 6 追記事項）

配色・サイズのカスタマイズを容易にするため、CSS変数を専用ファイルに定義する。

- **配置**: `src/styles/design-tokens.css`（またはCode Generation段階で確定する同等の
  専用ファイル）に`:root`スコープでCSSカスタムプロパティとして定義する。
- **対象トークン例**: プライマリ/セカンダリ色、警告/エラー/成功色（トースト通知・
  バリデーションエラー表示との整合）、フォントサイズスケール、スペーシングスケール、
  ボーダー半径。
- 個々のコンポーネントは直接の色・サイズのハードコードを避け、このファイルで定義された
  CSS変数を参照する（実装規約。具体的なトークン一覧・命名規則はCode Generation段階で確定）。

---

## 共通基盤コンポーネント（`features/`外）

### api/ — APIクライアント
| コンポーネント | 責務 |
|---|---|
| `apiClient`（fetchラッパー） | ベースURL・共通ヘッダ（JWT付与）・エラーレスポンス（共通エラーDTO、`business-rules.md` 3.1）のパースを一元化。401受信時は`authStore`のログアウト処理を呼び出し、ログイン画面へリダイレクトする（`business-logic-model.md` フロー4） |

### store/ — 状態管理
| コンポーネント | 責務 | 主な状態 |
|---|---|---|
| `authStore` | 認証状態（ログイン中ユーザ情報・ロール・JWT）の保持 | `currentUser: { id, email, role } \| null`, `token: string \| null` |
| `connectionStore`（2026-07-15変更要求） | グローバル接続コンテキスト（選択中の対象RDBMS接続、アクセス可能な接続一覧）の保持。`authStore`と同じ`sessionStorage`永続化パターン。ログアウト時にクリアされる（`business-logic-model.md` フロー5手順5） | `connectionId: number \| null`, `connections: ConnectionSummary[]` |

### hooks/ — 共通フック
| コンポーネント | 責務 |
|---|---|
| `useAuth` | `authStore`の値と、ログイン/ログアウト操作を提供 |
| `usePagination` | `common.PageRequest`/`PageResult<T>`パターンに対応したページング状態管理（ページ番号・ページサイズ・総件数） |
| `useConnection`（2026-07-15変更要求） | `connectionStore`の値（`connectionId`, `connections`）と、`setConnectionId`操作を提供。`masterData`/`queryBuilder`/`savedQuery`/`queryExecution`/`queryHistory`の各画面が、自身のページ内接続セレクタの代わりにこのフックから`connectionId`を取得する |

### routes/ — ルーティング基盤
| コンポーネント | 責務 |
|---|---|
| `ProtectedRoute` | 未認証時はログイン画面へリダイレクト。`requiredRole`propで管理者専用ルートを制御（`auditLog/`等が使用） |
| `AppRouter` | 全ユニットの`features/*/routes`を集約するルートルーター |

**ルーティング規約（全ユニット共通、U2レビュー時に確定）**: `ProtectedRoute requiredRole="ADMIN"`
配下の管理者専用画面のパスには、必ず`/admin`プレフィクスを付ける（例:
`/admin/audit-logs`、U2の`/admin/pending-users`）。一般ユーザも利用する認証必須画面や
未認証パブリック画面にはこのプレフィクスを付けない。以後のユニット（U3以降）の管理者専用
画面もこの規約に従う。

### components/ — 共通UIコンポーネント
| コンポーネント | Props | 責務 |
|---|---|---|
| `AppLayout` | `children` | ヘッダー・ナビゲーション・メインコンテンツ領域の共通レイアウト。`authStore`のロールに応じ管理者専用メニュー項目の出し分けを行う。**（2026-07-15変更要求）** 常設のグローバル接続セレクタを持ち、`connectionStore`が空の場合はマウント時に`listAccessibleConnections()`（U3所有API）を呼び出す。接続切替時、現在のパスが`/master-data/:connectionId/:schema/:table`に一致する場合のみ`/master-data`へナビゲーションする（`business-logic-model.md` フロー5、`stories.md` CHG-1〜CHG-3） |
| `DataTable<T>` | `columns`, `rows`, `onSort?` | 汎用テーブル表示（他ユニットの一覧画面から再利用） |
| `Pagination` | `page`, `pageSize`, `pageSizeOptions`, `totalCount`, `onPageChange`, `onPageSizeChange` | `PageResult<T>`に対応したページング操作UI。ページサイズ選択肢は`business-rules.md` 1.4のとおりAPI経由で取得した設定値を反映 |
| `ToastNotification` | `message`, `severity` (`info`/`success`/`warning`/`error`) | 操作結果・エラー通知の共通トースト表示 |
| `ConfirmDialog` | `message`, `onConfirm`, `onCancel` | 削除等の破壊的操作前の確認ダイアログ（他ユニットから再利用） |

---

## auditLog/ 機能（ADM-6）

### コンポーネント階層

```
AuditLogPage（ルート、ProtectedRoute requiredRole="ADMIN"配下）
├── AuditLogFilterPanel
└── AuditLogTable（DataTableを利用）
    └── Pagination
```

### AuditLogPage
- **状態**: `filter: { dateFrom?, dateTo?, userId?, eventCategory?, eventType? }`,
  `pageRequest: { page, pageSize }`, `result: PageResult<AuditLog> | null`, `loading: boolean`
- **責務**: `AuditLogFilterPanel`からの絞り込み条件変更、`Pagination`からのページ変更を受けて
  `GET /api/audit-logs`（絞り込み条件・ページ情報をクエリパラメータ化）を呼び出し、結果を
  `AuditLogTable`に渡す（`business-logic-model.md` フロー2）。

### AuditLogFilterPanel
- **Props**: `filter`, `onFilterChange`
- **責務**: 日時範囲（開始・終了）、ユーザ選択、操作種別（`eventCategory`→`eventType`の
  連動セレクト）の入力UIを提供。「検索」ボタン押下で`onFilterChange`を呼び出す。
- **バリデーション**: 開始日時が終了日時より後の場合はクライアント側でエラー表示し検索を
  送信しない。

### AuditLogTable
- **Props**: `rows: AuditLog[]`, `loading`
- **責務**: `eventCategory`ごとに視覚的に区別した一覧表示（例: バッジ色分け。配色は
  design-tokens.cssのCSS変数を参照）。`occurredAt`降順が既定（サーバ側ソート結果をそのまま表示）。

### API連携ポイント
| 画面操作 | APIエンドポイント（例） |
|---|---|
| 初期表示・絞り込み検索・ページ送り | `GET /api/audit-logs?dateFrom=&dateTo=&userId=&eventCategory=&eventType=&page=&pageSize=` |

（正確なエンドポイントパス・パラメータ名はCode Generation段階で確定。`AuditLogService.search`
のシグネチャに準拠する）

### AppRouter.tsxへの追加

| パス | コンポーネント | 認可 |
|---|---|---|
| `/admin/audit-logs` | `AuditLogPage` | `ProtectedRoute requiredRole="ADMIN"`（上記ルーティング規約に基づく） |