# SpringBlog

Spring Boot + Thymeleaf + Spring Data JPA で作られた、シンプルなブログの CRUD アプリケーションです。

## 概要

- ブログ記事の一覧表示・新規作成・編集・削除
- 投稿日の入力にカレンダー（jQuery UI Datepicker、日本語ロケール）を使用
- 動作確認用のファイルアップロード用 REST エンドポイント

## 技術スタック

| 分類 | 技術 | バージョン |
| --- | --- | --- |
| 言語 | Java | 25 |
| フレームワーク | Spring Boot | 3.5.16 |
| ビルド | Maven Wrapper | 3.9.16 |
| テンプレート | Thymeleaf | 3.1.x |
| 永続化 | Spring Data JPA / Hibernate | 6.6.x |
| DB | PostgreSQL | 16 |
| テスト | JUnit 5 / Spring Boot Test / H2 | - |
| フロントエンド | jQuery | 1.9.1 |
| フロントエンド | jQuery UI | 1.10.4 |
| フロントエンド | Bootstrap | 3.3.7 |
| フロントエンド | DataTables | 1.10.13 |

フロントエンドのライブラリは CDN から読み込んでおり、バージョンアップは行っていません。

## 必要要件

- JDK 25
- Docker / Docker Compose
- Maven は不要（Maven Wrapper を同梱）

## ディレクトリ構成

```
.
├── compose.yml                                  PostgreSQL 16 の起動定義
├── mvnw / mvnw.cmd                              Maven Wrapper
├── pom.xml                                      Maven ビルド定義
└── src
    ├── main
    │   ├── java/com/example
    │   │   ├── SpringBlogApplication.java       エントリポイント
    │   │   ├── Blog.java                        blog テーブルのエンティティ
    │   │   ├── BlogForm.java                    画面入出力用のフォーム（バリデーション定義）
    │   │   ├── BlogRepository.java              Spring Data JPA リポジトリ
    │   │   ├── BlogController.java              画面の CRUD 処理
    │   │   └── FileUploadRestController.java    ファイルアップロード API
    │   └── resources
    │       ├── application.properties           アプリ設定
    │       └── templates
    │           ├── index.html                   一覧画面
    │           └── edit.html                    新規作成・編集画面
    └── test
        ├── java/com/example/SpringBlogApplicationTests.java
        └── resources/application.properties     H2 インメモリのテスト用設定
```

## セットアップと起動

### 1. PostgreSQL を起動する

```bash
docker compose up -d
```

起動を確認する:

```bash
docker compose exec db pg_isready -U mrs -d mrs
```

停止する場合は `docker compose stop`、コンテナとデータを削除する場合は `docker compose down -v` を使います。

### 2. アプリケーションを起動する

```bash
./mvnw spring-boot:run
```

Windows の場合は `mvnw.cmd spring-boot:run` を使います。

ブラウザで http://localhost:8080/ を開きます。

### 3. 停止する

アプリは `Ctrl+C`、DB は `docker compose stop` で停止します。

## 設定

`src/main/resources/application.properties`:

| キー | 値 | 説明 |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/mrs` | 接続先 |
| `spring.datasource.username` | `mrs` | DB ユーザー |
| `spring.datasource.password` | `mrs` | DB パスワード |
| `spring.jpa.hibernate.ddl-auto` | `update` | 起動時にテーブルを自動作成・更新 |
| `spring.jpa.properties.hibernate.format_sql` | `true` | SQL ログを整形して出力 |
| `logging.level.org.springframework.web` | `DEBUG` | Web 層のログレベル |
| `spring.thymeleaf.cache` | `false` | テンプレートのキャッシュを無効化（開発用） |

環境変数で上書きする例:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/mrs \
SPRING_DATASOURCE_USERNAME=mrs \
SPRING_DATASOURCE_PASSWORD=secret \
./mvnw spring-boot:run
```

## 画面・エンドポイント仕様

| メソッド | パス | 説明 |
| --- | --- | --- |
| GET | `/` | 記事一覧を表示（`index.html`） |
| GET | `/edit` | 新規作成画面を表示（`edit.html`）。`postDate` の初期値は当日 |
| GET | `/edit/{id}` | 指定 ID の記事を編集画面に表示 |
| POST | `/edit`（`edit` パラメータ） | 記事を保存し `/` へリダイレクト。バリデーションエラー時は `edit.html` を再表示 |
| POST | `/edit`（`delete` パラメータ） | 記事を削除し `/` へリダイレクト |
| POST | `/file/upload` | ファイルをアップロード（後述） |

### 一覧画面（`/`）

- DataTables で表を描画します。
- 行をクリックすると `/edit/{id}` へ遷移します。
- 「新規作成」ボタンで `/edit` へ遷移します。

### 編集画面（`/edit`, `/edit/{id}`）

- タイトル・本文は必須です。未入力の場合は `必須です` を表示して保存しません。
- 投稿日は `yyyy/M/d` 形式で、カレンダーから選択できます。
- 新規作成時は「削除」ボタンが無効になります。

### ファイルアップロード（`POST /file/upload`）

- リクエスト: `multipart/form-data`
  - `upload_file`: アップロードするファイル
  - `filetype`: 保存時のファイル名
- レスポンス: 成功時は `You successfully uploaded.`、失敗時は `error!`
- 保存先: `/uploadfile/<yyyyMMddHHmmssSSS>/<filetype>`（コード上はファイルシステム直下の絶対パス）
- 注意: 保存先は `/uploadfile` 固定で、設定では変更できません。macOS などルートが読み取り専用の環境では書き込みに失敗し `error!` を返します。

## DB 仕様

テーブルは Hibernate が `spring.jpa.hibernate.ddl-auto=update` により自動作成します。

`blog` テーブル:

| カラム | 型 | 制約 |
| --- | --- | --- |
| `blog_id` | integer | 主キー、自動採番（IDENTITY） |
| `title` | varchar(255) | |
| `contents` | varchar(255) | |
| `post_date` | date | |

初期接続情報は DB 名 `mrs`、ユーザー `mrs`、パスワード `mrs`、ポート `5432` です。これらは `compose.yml` と `application.properties` の両方で定義しています。

## テスト

```bash
./mvnw test
```

テストは H2 インメモリ DB を使うため、PostgreSQL や Docker は不要です。

## ビルドと配布

```bash
./mvnw clean package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

## 既知の制約

- フロントエンドのライブラリ（jQuery 1.9.1 / jQuery UI 1.10.4 / Bootstrap 3.3.7 / DataTables 1.10.13）は古く、更新は別途対応が必要です。jQuery 3 系へ上げる場合は jQuery UI 1.13 以上への同時更新と、日本語ロケールの配布方法の見直しが前提になります。
- ファイルアップロードの保存先が `/uploadfile` というファイルシステム直下の絶対パスでハードコードされています。macOS ではルートが読み取り専用のためアップロードは失敗し `error!` を返します（移行前からの既存の挙動）。この挙動は今回の移行では変更していません。
- 画面に認証・認可はありません。
- `spring.jpa.hibernate.ddl-auto=update` のため、スキーマ変更は手動マイグレーションではなくエンティティ定義に依存します。

## トラブルシューティング

- **`docker compose up -d` がポート競合で失敗する**: ローカルで既に PostgreSQL が 5432 を使っている可能性があります。既存の PostgreSQL を停止するか、`compose.yml` のポートを変更してください。
- **`./mvnw: Permission denied`**: `chmod +x mvnw` を実行してください。
- **`UnsupportedClassVersionError` が出る**: JDK のバージョンが 25 未満です。`java -version` を確認し、JDK 25 をインストールしてください。
- **バリデーションのエラーが表示されない**: `spring-boot-starter-validation` が依存に含まれているか `pom.xml` を確認してください。
