# 要件確認質問（Requirements Analysis）

`docs/REQUIREMENTS.md` はすでに詳細な要件を整理済みですが、実装を始める前に確定させておきたい未確定事項がいくつかあります。以下の質問にお答えください。各質問の `[Answer]:` タグの後ろに、選択肢のアルファベットを記入してください（該当がなければ最後の「その他」を選び、内容を記述してください）。

## Question 1
ユーザ認証の実装方式はどれにしますか？（`docs/PROJECT_STRUCTURE.md` の `auth/` パッケージ説明で「ログイン/セッション or JWT」と未確定になっています）

A) セッションベース認証（Spring Sessionなど、サーバ側にセッション状態を保持）

B) JWT（ステートレストークン、Authorizationヘッダーで送信）

C) どちらでも良い（実装時に技術的に適切な方を選定してよい）

X) その他（[Answer]: タグの後に内容を記述してください）

[Answer]:
「B) JWT（ステートレストークン、Authorizationヘッダーで送信）」とします。


## Question 2
ユーザのロール（役割）モデルはどの粒度にしますか？

A) 「管理者」「一般ユーザ」の2種類のみ

B) 上記2種類に加えて、将来的な拡張（例: 閲覧専用ユーザなど）を見据えた拡張可能なロール設計にする

C) その他（[Answer]: タグの後に内容を記述してください）

[Answer]: 
「A) 「管理者」「一般ユーザ」の2種類のみ」とします。
なお、ロールは2種類ですが、「一般ユーザ」をグルーピングしてまとめてテーブル/カラムのアクセス権限を設定できるようにしてください。


## Question 3
対象RDBMSへの接続は、同時にいくつまで管理できる必要がありますか？（この回答により、権限モデルを「接続ごとに独立」させるか「単一接続前提」にするかが決まります）

A) 常に1つの対象RDBMS接続のみを管理する（接続を切り替える場合は既存設定を上書き）

B) 複数の対象RDBMS接続を登録し、ユーザ/権限は接続ごとに独立して管理する

C) その他（[Answer]: タグの後に内容を記述してください）

[Answer]: 
「B) 複数の対象RDBMS接続を登録し、ユーザ/権限は接続ごとに独立して管理する」とします。


## Question 4
パスワードに関する要件はありますか？（`docs/REQUIREMENTS.md` に記載がないため確認します）

A) 標準的なハッシュ化（bcrypt等）のみで良く、強度ポリシー（最低文字数・複雑さ等）は特に設けない

B) 最低文字数や文字種混在などの強度ポリシーを設ける（詳細は別途Application Designで検討）

C) その他（[Answer]: タグの後に内容を記述してください）

[Answer]: 
「A) 標準的なハッシュ化（bcrypt等）のみで良く、強度ポリシー（最低文字数・複雑さ等）は特に設けない」とします。


## Question 5
ログインセッション（またはトークン）の有効期限はどうしますか？

A) ユーザ登録トークンと同様に環境変数/設定ファイルで有効期限を設定可能にする（デフォルト値は実装時に提案）

B) 固定値でよい（具体的な時間を [Answer]: の後に記載してください）

C) その他（[Answer]: タグの後に内容を記述してください）

[Answer]: 
「A) ユーザ登録トークンと同様に環境変数/設定ファイルで有効期限を設定可能にする（デフォルト値は実装時に提案）」とします。


## Question 6
UI・メッセージの言語対応はどうしますか？

A) 日本語のみ対応する

B) 日本語をデフォルトとしつつ、将来的な多言語化（i18n）を見据えた設計にする

C) その他（[Answer]: タグの後に内容を記述してください）

[Answer]: 
「B) 日本語をデフォルトとしつつ、将来的な多言語化（i18n）を見据えた設計にする」とします。


## Question: Security Extensions
Should security extension rules be enforced for this project?

A) Yes — enforce all SECURITY rules as blocking constraints (recommended for production-grade applications)

B) No — skip all SECURITY rules (suitable for PoCs, prototypes, and experimental projects)

X) Other (please describe after [Answer]: tag below)

[Answer]: 
「B) No — skip all SECURITY rules (suitable for PoCs, prototypes, and experimental projects)」とします。
一通りの機能を実装できたら A を取り込みます。


## Question: Resiliency Extensions
Should the resiliency baseline be applied to this project?

**What this extension is.** Enabling it applies a set of **directional, design-time best practices** for building resilient systems, derived from the **AWS Well-Architected Framework (Reliability Pillar)** and resilience-review guidance. It steers requirements, design, and code toward fault tolerance, high availability, observability, and recoverability — covering 15 practice areas across business goals, change management, observability, high availability, disaster recovery, and continuous improvement.

**What this extension is NOT.** Enabling it does **not** make your workload production-ready, nor does it certify or guarantee any availability, RTO, or RPO target. It is a **starting point** that scaffolds good resiliency decisions early — it is not a substitute for a formal **AWS Well-Architected Review** of the built system.

Treat the output as a well-grounded **first draft of your resiliency posture** to build on and validate — not a finished, production-certified result.

A) Yes — apply the resiliency baseline as directional best practices and design-time guidance (recommended for business-critical workloads, as an informed starting point that you can validate and harden before go-live)

B) No — skip the resiliency baseline (suitable for PoCs, prototypes, and experimental projects where rapid iteration matters more than reliability)

X) Other (please describe after [Answer]: tag below)

[Answer]:
「B) No — skip the resiliency baseline (suitable for PoCs, prototypes, and experimental projects where rapid iteration matters more than reliability)」とします。
一通りの機能を実装できたら A を取り込みます。


## Question: Property-Based Testing Extension
Should property-based testing (PBT) rules be enforced for this project?

A) Yes — enforce all PBT rules as blocking constraints (recommended for projects with business logic, data transformations, serialization, or stateful components)

B) Partial — enforce PBT rules only for pure functions and serialization round-trips (suitable for projects with limited algorithmic complexity)

C) No — skip all PBT rules (suitable for simple CRUD applications, UI-only projects, or thin integration layers with no significant business logic)

X) Other (please describe after [Answer]: tag below)

[Answer]: 
「A) Yes — enforce all PBT rules as blocking constraints (recommended for projects with business logic, data transformations, serialization, or stateful components)」とします。
