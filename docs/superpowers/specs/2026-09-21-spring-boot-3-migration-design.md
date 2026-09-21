# Spring Blog モダナイゼーション設計

Spring Boot 1.4.3.RELEASE（2017年）→ 3.5.16 / Java 25 への移行。

## 背景

本リポジトリは Spring Boot 1.4.3 + Java 8 で書かれた小さな CRUD ブログである。
依存とAPIが9年分古く、`javax.*` 名前空間の廃止、Spring Data JPA のメソッド削除、
Hibernate Validator の制約クラス非推奨など、放置するとビルド自体が通らない。
外部から見た振る舞い（画面・URL・DBスキーマ）は維持したまま、基盤だけを現行化する。

## 目的

- Spring Boot 3.5.16 / Java 25 でビルド・起動・全CRUDが動くこと
- 画面・エンドポイント・DBスキーマの互換性を維持すること
- 次回以降の作業のために、再現可能な起動手段（Docker Compose）と最低限のテストを用意すること

## 対象リポジトリの現状

```
pom.xml                         Spring Boot 1.4.3.RELEASE / java.version 1.8
mvnw, mvnw.cmd, .mvn/           Maven Wrapper（distributionUrl は Maven 3.3.9、mvnw に実行権限なし）
src/main/java/com/example/
  SpringBlogApplication.java    エントリポイント
  Blog.java                     @Entity（javax.persistence）
  BlogForm.java                 @NotBlank/@NotNull（javax.validation + hibernate独自）
  BlogController.java           /  /edit  /edit/{id}  のCRUD（findOne / delete(ID) を使用）
  BlogRepository.java           JpaRepository<Blog, Integer>
  FileUploadRestController.java /file/upload への一括アップロード（javax依存なし）
  converter/
    LocalDateConverter.java     AttributeConverter<LocalDate, java.sql.Date>（autoApply）
    LocalDateTimeConverter.java AttributeConverter<LocalDateTime, java.sql.Timestamp>（未使用）
    LocalTimeConverter.java     AttributeConverter<LocalTime, java.sql.Time>（未使用）
src/main/resources/
  application.properties        PostgreSQL localhost:5432/mrs（mrs/mrs/mrs）、ddl-auto=update
  templates/index.html          一覧（DataTables 1.10.13）
  templates/edit.html           新規/編集/削除 + ファイルアップロードフォーム
```

テストは0件。`.gitignore` は無い。Docker 関連ファイルは無い。

フロントの実測構成（CDNを実際に確認した結果）:

| ライブラリ | 現在のバージョン | 備考 |
| --- | --- | --- |
| jQuery | 1.9.1 | `ajax.googleapis.com` |
| jQuery UI | 1.10.4 | `.../jqueryui/1/` は 1.10.4（2014年）に解決される |
| jQuery UI i18n (ja) | 1.x 同梱 | `jquery.ui.datepicker-ja.min.js` |
| Bootstrap | 3.3.7 | `maxcdn.bootstrapcdn.com`（現在も配信、確認済み） |
| DataTables | 1.10.13 | `cdn.datatables.net` |

使用しているAPIは `datepicker()` と `DataTable()` のみ。

## 非対象（今回やらないこと = YAGNI）

- Kotlin 化
- Bootstrap 3.3.7 → 5 へのアップグレード（見た目の改修が大きく、移行の目的から外れる）
- **jQuery / jQuery UI / DataTables のアップグレード**（理由は「6. テンプレート」に記載）
- FileUploadRestController のロジック改善（保存先がファイルシステム直下の `/uploadfile` にハードコードされている点、TODO コメント等）。macOS では `/` が読み取り専用のためアップロードは失敗するが、これは移行前からの既存の不具合であり今回のスコープ外とする。修正が必要かは別途判断する
- フィールド `@Autowired` からコンストラクタ注入への変更
- Spring Boot 4.x への移行（今回は 3.5 系）

## 設計

### 1. ビルド（pom.xml / Maven Wrapper）

| 項目 | 変更前 | 変更後 |
| --- | --- | --- |
| `spring-boot-starter-parent` | 1.4.3.RELEASE | 3.5.16 |
| `java.version` | 1.8 | 25 |
| `thymeleaf-extras-java8time` | 2.1.0.RELEASE | 削除 |
| `spring-boot-starter-validation` | なし | 追加 |
| `spring-boot-starter-test` (test) | コメントアウト | 追加 |
| `com.h2database:h2` (test) | コメントアウト | 追加 |

- `thymeleaf-extras-java8time` の削除: Thymeleaf 3.1 で `#temporals` は本体に統合されたため不要。テンプレート側の記述は変更不要。
- `spring-boot-starter-validation` の追加: Spring Boot 2.3 以降 `spring-boot-starter-web` に含まれないため、無いと `@NotBlank`/`@NotNull` が**エラーにならず無視される**（最も気づきにくい破壊）。
- Java 25: Spring Boot 3.5.16 の公式システム要件は「Java 17 以上、25 まで対応」。ローカルに JDK 25 が導入済みのため追加導入は不要。コンパイルは `--release 25` 相当。Java 21 でも動作するが、その場合は JDK 21 の導入が必要になる。ポータビリティを優先したい場合のみ 21 を選ぶ。
- Maven: Spring Boot 3.5 は Maven 3.6.3 以上を要求。Wrapper を `mvn wrapper:wrapper` で再生成して Maven 3.9 系に更新する（現行ラッパーは 2016 年製でチェックサム検証が無い）。加えて `chmod +x mvnw` で実行権限を付与する。

### 2. `javax.*` → `jakarta.*`

- `Blog.java`: `import javax.persistence.*;` → `import jakarta.persistence.*;`
- `BlogForm.java`:
  - `postDate` の `@NotNull` を `jakarta.validation.constraints.NotNull` に変更（`LocalDate` なので `NotBlank` にはしない）
  - `title` / `contents` の `org.hibernate.validator.constraints.NotBlank` を `jakarta.validation.constraints.NotBlank` に変更
  - 未使用 import（`javax.persistence.*`、`org.hibernate.validator.constraints.NotEmpty`、`org.springframework.beans.factory.annotation.Value`、`java.time.LocalTime`）を削除

補足: `org.hibernate.validator.constraints.NotBlank` の削除は Hibernate Validator 9.0。Spring Boot 3.5 が使う Hibernate Validator 8.0.3 では非推奨だが**残存**するため、これは「コンパイルを通すための必須修正」ではなくクリーンアップである。方針として同時に直す。

### 3. Spring Data JPA API の変更

- `BlogController.edit()`: `blogReposiroty.findOne(id)` → `blogReposiroty.findById(id).orElseThrow()`
  - `findOne` は Spring Data の旧APIで現行に存在しない。
- `BlogController.delete()`: `blogReposiroty.delete(blogform.getBlogId())` → `blogReposiroty.deleteById(blogform.getBlogId())`
  - `CrudRepository.delete(ID)` は Spring Data 3.0 で削除済み。現行APIは `deleteById(ID)` のみ。`delete(T entity)` とは引数型（`Blog` vs `Integer`）が異なるためオーバーロード解決もされず、コンパイルエラーになる。直近で追加された削除機能のため特に注意する。

### 4. 周辺モダン化

- `converter/` の3クラスを削除する。
  - Hibernate 6 は `LocalDate`/`LocalDateTime`/`LocalTime` をネイティブにマッピングする。
  - `LocalDateTimeConverter` と `LocalTimeConverter` はどのエンティティからも使われていない。
  - `LocalDateConverter` は `Blog.postDate` に `autoApply` で効いているが、ネイティブマッピングも同じ `DATE` になるため振る舞いは変わらない。
  - これにより `javax.persistence` 依存のファイルが減り、jakarta 化の対象も減る。
- `BlogController`: `@RequestMapping(method = RequestMethod.GET/POST)` を `@GetMapping` / `@PostMapping` に置き換える。`params = "edit"` / `params = "delete"` は維持する（画面のボタン名に依存しているため）。
- エンドポイントは変更しない: `GET /`、`GET /edit`、`GET /edit/{id}`、`POST /edit?edit`、`POST /edit?delete`、`POST /file/upload`。

### 5. DB / Docker

- `compose.yml` を追加する。
  - イメージ: `postgres:16`
  - `POSTGRES_DB=mrs`、`POSTGRES_USER=mrs`、`POSTGRES_PASSWORD=mrs`
  - ポート `127.0.0.1:5432:5432`（ホストの loopback のみに公開）、named volume で永続化
- `application.properties`: `spring.jpa.database=POSTGRESQL` を削除する（このプロパティは Boot 3.5.16 にも存在し非推奨ではない。値の大文字・小文字も relaxed binding によりどちらも有効。削除するのは DB 種別が接続から自動判定され、指定が冗長になるため）。接続先・認証情報は既存の `mrs` を踏襲し、`ddl-auto=update`、`spring.thymeleaf.cache=false` は維持する。
- 既存 DB のスキーマは Hibernate 5 が作成したもの。Hibernate 6 + `ddl-auto=update` で変更が入り得るため、検証前にダンプを取得する。

### 6. テンプレート

- `index.html` / `edit.html` の jQuery UI 系リンク（`ajax.googleapis.com`）を `http://` → `https://` に修正する。混在コンテンツとしてブロックされるため。
- Bootstrap 3.3.7（`maxcdn.bootstrapcdn.com`）、DataTables、jQuery 1.9.1 は変更しない。

**jQuery を上げない理由（調査結果）**

- 現状は jQuery 1.9.1 + jQuery UI 1.10.4 + DataTables 1.10.13。`.../jqueryui/1/` は 1.10.4 に解決される。
- jQuery 3.x は jQuery UI 1.10.4 非対応のため、jQuery UI 1.13+/1.14 への同時更新が必須。
- 日本語ロケールが問題になる。Google CDN の 1.12/1.13/1.14 には i18n が無く（404）、npm / jsdelivr / cdnjs の `jquery-ui@1.14` にも `i18n` ディレクトリが存在しない。つまり `datepicker-ja` は自前ホストか別配布物が必要になる。
- 得られる機能上の利益は無く、datepicker と DataTables の再検証コストだけが増える。移行検証と混ざると原因切り分けができなくなる。
- したがって今回は据え置き、jQuery 更新は独立ブランチで扱う。同時に実施する場合は「jQuery 3.7.x + jQuery UI 1.14.x + ロケール自前ホスト + DataTables 互換確認」をスコープとして明記する。

- Thymeleaf 3.1 のパースで問題になる記述は無い（`#temporals` は本体統合済み）。

### 7. リポジトリ整備

- `.gitignore` を追加: `target/`、`uploadfile/`、`.idea/`、`*.iml`、`.DS_Store`、`*.log`
- `README.md` を追加する。単なる起動メモではなく、このアプリの仕様と運用手順を一通り把握できる内容にする。記載項目は以下:
  - 概要（何をするアプリか、主な機能）
  - 技術スタックとバージョン（Spring Boot 3.5.16 / Java 25 / PostgreSQL 16 / Thymeleaf 3.1 / Spring Data JPA、フロントは jQuery 1.9.1・jQuery UI 1.10.4・Bootstrap 3.3.7・DataTables 1.10.13）
  - 必要要件（JDK 25、Docker / Docker Compose、Maven は Wrapper 同梱のため不要）
  - ディレクトリ構成（`com/example` 配下の各クラスの役割）
  - セットアップと起動（`docker compose up -d` → `./mvnw spring-boot:run`、Windows は `mvnw.cmd`、`http://localhost:8080/`）
  - 設定（`application.properties` の各項目の意味、`SPRING_DATASOURCE_*` など環境変数での上書き例）
  - 画面・エンドポイント仕様（URL一覧と各画面の説明、`POST /edit` の `edit`/`delete` パラメータ、`POST /file/upload` の入出力と保存先）
  - DB 仕様（`blog` テーブルのカラム定義、`spring.jpa.hibernate.ddl-auto=update` により自動生成されること、接続情報 `mrs/mrs/mrs`）
  - テスト（`./mvnw test`、H2 インメモリで DB 不要）
  - ビルドと配布（`./mvnw clean package` → `java -jar target/demo-0.0.1-SNAPSHOT.jar`）
  - 既知の制約（フロントのライブラリが古い、ファイルアップロード先が相対パスでハードコード、画面に認証が無い）
  - トラブルシューティング（5432 のポート衝突、`./mvnw` の実行権限、Java バージョン不一致）

### 8. テスト

- `src/test/java/com/example/SpringBlogApplicationTests.java` に `@SpringBootTest` の context-load テストを1本追加する。
- `src/test/resources/application.properties` でデータソースを H2 インメモリに上書きし、Docker やPostgres 無しで `./mvnw test` が通るようにする。
  - `spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1`
  - `spring.jpa.hibernate.ddl-auto=create-drop`
- 目的は「移行の取りこぼし（DI・JPA マッピング・Bean 定義の破綻）を起動時に検出する」こと。CRUD の網羅テストは今回のスコープ外。

## 変更ファイル一覧

| 種別 | ファイル |
| --- | --- |
| 変更 | `pom.xml` |
| 変更 | `src/main/java/com/example/Blog.java` |
| 変更 | `src/main/java/com/example/BlogForm.java` |
| 変更 | `src/main/java/com/example/BlogController.java` |
| 変更 | `src/main/resources/application.properties` |
| 変更 | `src/main/resources/templates/index.html` |
| 変更 | `src/main/resources/templates/edit.html` |
| 変更 | `mvnw` / `mvnw.cmd` / `.mvn/wrapper/*`（Wrapper 再生成） |
| 追加 | `compose.yml` |
| 追加 | `.gitignore` |
| 追加 | `README.md` |
| 追加 | `src/test/java/com/example/SpringBlogApplicationTests.java` |
| 追加 | `src/test/resources/application.properties` |
| 削除 | `src/main/java/com/example/converter/LocalDateConverter.java` |
| 削除 | `src/main/java/com/example/converter/LocalDateTimeConverter.java` |
| 削除 | `src/main/java/com/example/converter/LocalTimeConverter.java` |

`FileUploadRestController.java`、`BlogRepository.java`、`SpringBlogApplication.java` は変更不要。

## リスクと対策

| リスク | 対策 |
| --- | --- |
| Hibernate 6 のスキーマ変更が既存データに影響 | 検証前に `pg_dump` を取得。`ddl-auto=update` の差分を起動ログで確認 |
| バリデーションが無効のまま動いてしまう | `spring-boot-starter-validation` を追加し、必須エラーが実際に表示されることを手動確認 |
| 削除機能のAPI変更漏れ | `deleteById` へ修正し、削除ボタンからの動作を手動確認 |
| JSR-310 コンバータ削除による日付マッピング差異 | `postDate` の登録・表示・再読み込みを手動確認 |
| ファイルアップロードが macOS では `/` 読み取り専用のため失敗する（既存の不具合） | スコープ外とし、エンドポイントのルーティングと応答のみを検証対象にする（README の既知の制約に記載） |
| Wrapper 再生成で `./mvnw` が壊れる | 再生成後に `./mvnw -v` と `./mvnw test` で確認 |
| フロントの別改修と移行が混ざり原因切り分け不能になる | jQuery 系は今回触らず、独立ブランチで行う |

## 検証手順

1. `docker compose up -d` で PostgreSQL 起動
2. `./mvnw test` で context-load テストが通ること
3. `./mvnw spring-boot:run` で起動しエラーが無いこと
4. ブラウザで確認
   - `GET /` 一覧の表示・DataTables の動作・行クリックで `/edit/{id}` へ遷移
   - `GET /edit` で新規作成、`postDate` の datepicker が動くこと
   - タイトル未入力で `必須です` が出ること（バリデーション有効の確認）
   - 保存 → 一覧に反映、編集 → 反映、削除 → 反映
   - ファイルアップロードのエンドポイントが応答すること（`POST /file/upload` が 404/500 にならない）。保存先はファイルシステム直下の `/uploadfile` 固定で、macOS では `/` が読み取り専用のため書き込みに失敗し `error!` を返す。これは移行前からの既存の挙動であり、今回の移行では変更しない
5. jQuery UI の CSS/JS が https で読み込まれ、コンソールに混在コンテンツ警告が出ないこと

## デリバリ

- ブランチ `feature/spring-boot-3` で作業
- 上記検証を通したうえで master へマージ
- コミットは機能単位（build / jakarta / persistence API / infra / docs）

## 決定事項

- 実行環境は Java 25（JDK 25 が導入済み。Spring Boot 3.5.16 のサポート範囲内）
- jQuery / jQuery UI / DataTables のアップグレードは今回は実施しない（独立ブランチで扱う）
- JSR-310 コンバータ3クラスは削除する
- context-load テストの DB は H2 インメモリ

## 未決事項

- なし
