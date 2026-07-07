# Unit of Work Plan — Units Generation (Part 1: Planning)

## 目的

`application-design/` の成果物（14バックエンドパッケージ + 対応するフロントエンド
`features/`）を、CONSTRUCTION フェーズで1ユニットずつ完結させる（Functional Design →
NFR Requirements → NFR Design → Code Generation）ための「ユニット・オブ・ワーク」に
分割する。

**前提**: 本プロジェクトは単一の Spring Boot モノリス（`backend/`）+ 単一の React SPA
（`frontend/`）としてデプロイされる（`docs/PROJECT_STRUCTURE.md`）。「独立デプロイ可能な
サービス」ではないため、ここでの「ユニット」は *開発順序と検証範囲を区切るための論理単位*
であり、`units-generation.md` の用語定義に従い "Module"/"Unit of Work" として扱う
（"Service" ではない）。

---

## 必須成果物チェックリスト

- [x] `aidlc-docs/inception/application-design/unit-of-work.md` — ユニット定義・責務
- [x] `aidlc-docs/inception/application-design/unit-of-work-dependency.md` — 依存関係マトリクス
- [x] `aidlc-docs/inception/application-design/unit-of-work-story-map.md` — ストーリー対応表
- [x] ユニット境界・依存関係の妥当性検証
- [x] 全33ストーリー（MVP-1〜MVP-11, ADM-1〜ADM-6, GEN-1〜GEN-16）がいずれかのユニットに割当済みであることの確認

（本プロジェクトは Brownfield のため、Greenfield限定の「コード編成戦略の文書化」項目は対象外）

---

## 提案ユニット構成（たたき台）

`components.md`・`services.md`・ストーリー対応表を基に、パッケージ依存関係
（`component-dependency.md` の依存マトリクス）とストーリー親和性で以下の7ユニットを提案する。
Question 1 以降でこの構成の妥当性を確認する。

| ユニット | 含むバックエンドパッケージ | 含むフロントエンド`features/` | 対応ストーリー |
|---|---|---|---|
| U1: Platform Foundation | `common`, `config`, `audit`, `mail` | `components/`, `api/`, `hooks/`, `store/`, `routes/`（共通基盤）, `auditLog/` | ADM-6（監査ログ閲覧）＋各ストーリーの監査記録AC全般 |
| U2: Auth & User Registration | `auth`, `userregistration` | `auth/`, `userRegistration/` | MVP-1〜MVP-6 |
| U3: RDBMS Connection & Schema Import | `rdbmsconnection`, `schema` | `rdbmsConnection/`, `schema/` | MVP-7, MVP-8, ADM-3 |
| U4: Permission Management | `permission` | `permission/` | MVP-9, ADM-1, ADM-2, ADM-4, ADM-5 |
| U5: Master Data Maintenance | `masterdata` | `masterData/` | MVP-10, MVP-11, GEN-1〜GEN-5 |
| U6: Query Builder | `querybuilder` | `queryBuilder/` | GEN-6〜GEN-9 |
| U7: Saved Query / Execution / History | `savedquery`, `queryexecution`, `queryhistory` | `savedQuery/`, `queryExecution/`, `queryHistory/` | GEN-10〜GEN-16 |

**依存関係（`component-dependency.md` マトリクスに基づく推定順序）**:
U1 → U2 → U3 → U4 → {U5, U6} → U7
（`EffectivePermissionResolver`（U4）は U5/U6 双方から参照される共有コンポーネントのため、
U4 完了後に U5/U6 に着手する。U7 の `queryexecution` は `permission` に依存しない設計
（`component-dependency.md` 注記）だが、`savedquery`/`queryhistory` 経由で間接的に
U5/U6 の成果物（クエリビルダー生成SQL等）を利用するため最後段に置く。）

---

## Question 1: Story Grouping（ストーリーのグルーピング戦略）

上記のユニット構成は「バックエンドパッケージ境界 + ペルソナ/機能親和性」を軸にしている
（`docs/PROJECT_STRUCTURE.md` のパッケージ構成をそのまま踏襲）。

A. この7ユニット構成のまま進める（推奨— パッケージ構成・依存関係・ストーリー対応が
   最も自然に一致する）
B. MVP（Part 1）/ ADM（Part 2）/ GEN（Part 3）というストーリーパート境界を優先し、
   パッケージ横断でユニットを再編する
C. その他（自由記述で理由とともに指定）

[Answer]:

Aとします。

---

## Question 2: Dependencies（依存関係・統合方式）

U4（Permission）は U5（Master Data）・U6（Query Builder）双方から `EffectivePermissionResolver`
を通じて参照される共有コンポーネント。U7（Saved Query/Execution/History）はどのユニットが
完了してから着手すべきか、また U5とU6は並行着手可能とみなしてよいか。

A. 提案順序のまま（U1→U2→U3→U4→{U5, U6並行可}→U7）で進める（推奨）
B. U5→U6→U7 のように完全に逐次で進める（並行を許さない）
C. その他（自由記述で順序を指定）

[Answer]:

Aとします。

---

## Question 3: Team Alignment（チーム体制・所有権境界）

`docs/PROJECT_STRUCTURE.md` は「単独開発かつMVP段階的リリース」を前提としている。

A. 単独開発者が本ユニット順序で逐次実装する前提でよい（チーム分割・オーナーシップ境界の
   設計は不要、N/A） （推奨）
B. 複数人での分担を前提に、ユニットをオーナーシップ境界としても設計してほしい
   （分担案を具体的に指定）

[Answer]:

Aとします。

---

## Question 4: Technical Considerations（スケーラビリティ・デプロイ差異）

全ユニットは単一のSpring Boot WAR + 単一React SPAに組み込まれ、独立デプロイはされない
（`docs/PROJECT_STRUCTURE.md`、`CLAUDE.md` Deployment target）。

A. 全ユニットで技術的な差異（スケーラビリティ要件・デプロイ方式の違い）はなく、共通の
   WARパッケージングで統一する（推奨、N/A扱い）
B. 特定ユニットに個別の技術的考慮が必要（具体的に指定）

[Answer]:

Aです。

---

## Question 5: Business Domain（ドメイン境界の妥当性）

提案の7ユニットの粒度・境界について、特にA〜Cの点を確認したい。

- U1に `audit`（監査ログ閲覧UI含む）を含めるのは、他の全ユニットから横断的に参照される
  基盤機能だからだが、ADM-6は Part 2（管理者機能拡張）のストーリーであり、U1を最初期に
  完全に作り込む必要はない（記録機能 `AuditLogService.record` のみ先行実装し、検索/閲覧UI
  `AuditLogService.search` + `auditLog/` 画面はU4完了後などに後回しにする）という進め方も
  可能。
- U7で `savedquery` / `queryexecution` / `queryhistory` の3パッケージを1ユニットに
  まとめているが、これらは相互依存が強い（クエリ実行が履歴記録・保存クエリ取得を都度呼ぶ）
  ため統合が妥当と判断した。分割したい場合は理由とともに指定してほしい。

A. 提案のドメイン境界のまま進める（推奨）
B. U1の監査ログ「記録」機能（`AuditLogService.record`のみ）を先行実装し、「閲覧UI」
   （`search` + `auditLog/`画面、ADM-6）はU4完了後の独立フェーズに切り出す
C. U7を `savedquery` / `queryexecution+queryhistory` の2ユニットに分割する
D. その他（自由記述で境界の変更点を指定）

[Answer]:

Aとします。

---

## Step 5-6: 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください（A/B/C/D いずれかの記号、または
自由記述）。全ての質問に回答後、その旨を伝えてください。回答内容に曖昧さが残る場合は
追加の確認質問を本ドキュメントに追記します（`units-generation.md` Step 7-8）。