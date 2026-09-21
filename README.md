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
| ビルド | Maven（Maven Wrapper 経由） | 3.9.16 |
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
├── .mvn/                                        Maven Wrapper の設定
├── README.md                                    このドキュメント
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
        ├── java/com/example
        │   ├── SpringBlogApplicationTests.java  コンテキスト起動の確認
        │   └── BlogControllerTest.java          画面の CRUD・バリデーション・異常系のテスト
        └── resources
            └── application-test.properties      test プロファイル用の上書き設定（H2 インメモリ）
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
| GET | `/edit/{id}` | 指定 ID の記事を編集画面に表示。存在しない ID の場合は HTTP 404 |
| POST | `/edit`（`edit` パラメータ） | 記事を保存し `/` へリダイレクト。バリデーションエラー時は `edit.html` を再表示。`blogId` が指定されているが該当記事が存在しない場合（編集中に他者が削除した場合など）は HTTP 404 |
| POST | `/edit`（`delete` パラメータ） | 記事を削除し `/` へリダイレクト。`blogId` がない場合と、該当記事が既に存在しない場合は何もせずリダイレクトする |
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
- `filetype` にディレクトリを含む値（`../` など）が指定された場合は、最後のファイル名部分のみを使用します。ファイル名として使えない値（`..`、空文字など）は `error!` を返します
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

初期接続情報は DB 名 `mrs`、ユーザー `mrs`、パスワード `mrs`、ポート `5432` です。これらは `compose.yml` と `application.properties` の両方で定義しています。`compose.yml` のポート公開は `127.0.0.1:5432:5432` としており、DB はホストの loopback からのみ接続できます。

## テスト

```bash
./mvnw test
```

テストは `test` プロファイルで実行され、`src/main/resources/application.properties` を読み込んだうえで `src/test/resources/application-test.properties` が接続先と `ddl-auto` だけを H2 インメモリに上書きします。本番の設定ファイルを読み込み対象に残すことで、そちらに追加した設定がテスト対象から漏れないようにしています。PostgreSQL や Docker は不要です。

## ビルドと配布

```bash
./mvnw clean package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

## 既知の制約

- フロントエンドのライブラリ（jQuery 1.9.1 / jQuery UI 1.10.4 / Bootstrap 3.3.7 / DataTables 1.10.13）は古く、更新は別途対応が必要です。jQuery 3 系へ上げる場合は jQuery UI 1.13 以上への同時更新と、日本語ロケールの配布方法の見直しが前提になります。
- ファイルアップロードの保存先が `/uploadfile` というファイルシステム直下の絶対パスでハードコードされています。macOS ではルートが読み取り専用のためアップロードは失敗し `error!` を返します（移行前からの既存の挙動）。この挙動は今回の移行では変更していません。
- アップロード画面の JavaScript は HTTP ステータスだけを見てレスポンス本文を判定しません。そのため保存に失敗して `error!` が返った場合でも「アップロードが完了しました。」と表示されます（移行前からの既存の挙動）。
- 削除は、対象が既に存在しない場合でもエラーにならず成功扱いでリダイレクトします。Spring Data JPA 3 の `deleteById` が存在しない ID を無視する仕様によるもので、移行前（`delete(ID)`）は例外になっていました。
- 画面に認証・認可はありません。
- `spring.jpa.hibernate.ddl-auto=update` のため、スキーマ変更は手動マイグレーションではなくエンティティ定義に依存します。
- スキーマは `spring.jpa.hibernate.ddl-auto=update` で作成されます。現在の `blog` テーブルは Hibernate 6（Spring Boot 3.5.16）が `blog_id integer ... generated by default as identity` として生成したものです。移行前の Hibernate 5 で作成したデータベースが存在しないため、旧スキーマとの差分確認は行っていません。

## トラブルシューティング

- **`docker compose up -d` がポート競合で失敗する**: ローカルで既に PostgreSQL が 5432 を使っている可能性があります。既存の PostgreSQL を停止するか、`compose.yml` のポートを変更してください。
- **`./mvnw: Permission denied`**: `chmod +x mvnw` を実行してください。
- **`UnsupportedClassVersionError` が出る**: JDK のバージョンが 25 未満です。`java -version` を確認し、JDK 25 をインストールしてください。
- **テストが PostgreSQL に接続しようとする / 設定変更が反映されない**: 以前のビルドで生成された `target/test-classes/application.properties` が残り、設定を上書きしている可能性があります。`./mvnw clean test` を実行してください。
- **バリデーションのエラーが表示されない**: `spring-boot-starter-validation` が依存に含まれているか `pom.xml` を確認してください。
