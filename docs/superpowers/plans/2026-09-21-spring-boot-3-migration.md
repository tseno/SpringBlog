# Spring Boot 3.5.16 / Java 25 移行 実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring Boot 1.4.3 / Java 8 のブログアプリを、外部挙動（画面・URL・DBスキーマ）を変えずに Spring Boot 3.5.16 / Java 25 で動作させる。

**Architecture:** 単一の Spring Boot アプリ。Controller が Thymeleaf テンプレートを返し、Spring Data JPA 経由で PostgreSQL の `blog` テーブルを CRUD する。移行は「ビルド基盤 → ソースの名前空間/API 移行 → 実行環境 → テスト → 画面 → ドキュメント」の順に、各タスクが単体で検証可能な単位で進める。

**Tech Stack:** Java 25 / Spring Boot 3.5.16 / Maven Wrapper 3.9.16 / Thymeleaf 3.1 / Spring Data JPA (Hibernate 6.6) / PostgreSQL 16 / H2 (test) / Docker Compose

**Spec:** `docs/superpowers/specs/2026-09-21-spring-boot-3-migration-design.md`

## Global Constraints

- Spring Boot のバージョンは `3.5.16` 固定（`3.5.x` 系の最新）。
- `java.version` は `25`。JDK は 25 がインストール済みであることを前提とする。
- Maven は Wrapper（`./mvnw`）のみを使う。Maven 3.9.x 以上が必要（Boot 3.5 の要求は 3.6.3 以上）。
- エンドポイントを変更しない: `GET /`、`GET /edit`、`GET /edit/{id}`、`POST /edit?edit`、`POST /edit?delete`、`POST /file/upload`。
- DB 接続情報は既存を踏襲: DB/ユーザー/パスワードすべて `mrs`、`localhost:5432`。
- `BlogRepository`、`SpringBlogApplication`、`FileUploadRestController` は変更しない。
- フロントのライブラリ（jQuery 1.9.1 / jQuery UI 1.10.4 / Bootstrap 3.3.7 / DataTables 1.10.13）はバージョンアップしない。`http://` → `https://` の修正のみ行う。
- フィールド `@Autowired` をコンストラクタ注入に変更しない。
- コミットは機能単位で行い、各コミットメッセージの末尾に以下を付ける:

```
Co-authored-by: CommandCodeBot <noreply@commandcode.ai>
```

---

### Task 1: ビルド基盤の更新（pom.xml / Maven Wrapper / .gitignore）

このタスクの時点では **ソースのコンパイルは意図的に失敗する**（`javax.*` が残っているため）。Task 2 で解消する。検証は「Maven と依存解決が正しく動くこと」に対して行う。

**Files:**
- Modify: `pom.xml`
- Replace: `mvnw`, `mvnw.cmd`
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Delete: `.mvn/wrapper/maven-wrapper.jar`
- Create: `.gitignore`

**Interfaces:**
- Consumes: なし
- Produces: `./mvnw` で起動できる Maven 3.9.16、Spring Boot 3.5.16 の依存管理、`spring-boot-starter-validation` / `spring-boot-starter-test` / `com.h2database:h2`

- [ ] **Step 1: pom.xml を書き換える**

`pom.xml` の全体を以下に置き換える（コメントアウトされていた依存は削除する）。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>

	<groupId>com.example</groupId>
	<artifactId>demo</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<packaging>jar</packaging>

	<name>SpringBlog</name>
	<description>SpringBlog project for Spring Boot</description>

	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>3.5.16</version>
		<relativePath/> <!-- lookup parent from repository -->
	</parent>

	<properties>
		<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
		<project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
		<java.version>25</java.version>
	</properties>

	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>

		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-thymeleaf</artifactId>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
		</dependency>

		<dependency>
			<groupId>com.h2database</groupId>
			<artifactId>h2</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
		</plugins>
	</build>

</project>
```

- [ ] **Step 2: Maven Wrapper を現行版に置き換える**

公式配布物の `only-script` 版から `mvnw` / `mvnw.cmd` を取得する。Maven 本体のインストールは不要。`only-script` 版には jar が含まれず、`mvnw` は Maven 本体を直接ダウンロードする（`bin` 版の `mvnw` は `distributionType=only-script` を無視して jar を再取得し、未追跡ファイルが残るため使わない）。

```bash
curl -sL -o /tmp/maven-wrapper.zip \
  "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper-distribution/3.3.4/maven-wrapper-distribution-3.3.4-only-script.zip"
unzip -o /tmp/maven-wrapper.zip mvnw mvnw.cmd -d .
rm -f .mvn/wrapper/maven-wrapper.jar
chmod +x mvnw
```

- [ ] **Step 3: `.mvn/wrapper/maven-wrapper.properties` を書き換える**

既存ファイルの内容を以下に置き換える（`distributionType=only-script` により jar は不要）。

```properties
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip
distributionSha256Sum=5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce
```

`distributionSha256Sum` は必須である。spec は「現行ラッパーはチェックサム検証が無い」ことを再生成の理由に挙げているが、`mvnw` はこのキーが存在する場合にのみ検証するため、書かなければ理由が達成されない。値は公式配布物 `apache-maven-3.9.16-bin.zip` の SHA-256（Maven Central 公開の SHA-512 と一致することを確認済み）:

- [ ] **Step 4: `.gitignore` を作成する**

```gitignore
target/
uploadfile/
*.log
.idea/
*.iml
.DS_Store
```

- [ ] **Step 5: Wrapper が動作することを確認する**

Run: `./mvnw -v`
Expected: `Apache Maven 3.9.16` と `Java version: 25` が表示される（初回は Maven 3.9.16 のダウンロードが走る）。`Permission denied` になる場合は `chmod +x mvnw` を実行する。

- [ ] **Step 6: Spring Boot のバージョンが解決されることを確認する**

Run: `./mvnw -q help:evaluate -Dexpression=project.parent.version -DforceStdout`
Expected: `3.5.16`

- [ ] **Step 7: 依存が解決されることを確認する**

Run: `./mvnw dependency:resolve -DincludeScope=test`
Expected: `BUILD SUCCESS`（`spring-boot-starter-validation`、`h2`、`postgresql` が解決される）。
注意: この時点で `./mvnw compile` は `javax.persistence` 等が解決できず **失敗する**。これは想定どおりで、Task 2 で解消する。

- [ ] **Step 8: コミット**

```bash
git add pom.xml mvnw mvnw.cmd .mvn/wrapper/maven-wrapper.properties .gitignore
git rm --cached .mvn/wrapper/maven-wrapper.jar 2>/dev/null || true
git commit -F - <<'EOF'
build: Spring Boot 3.5.16 / Java 25 へ更新し Maven Wrapper を再生成

- parent を 1.4.3.RELEASE から 3.5.16、java.version を 8 から 25 へ
- thymeleaf-extras-java8time を削除（Thymeleaf 3.1 で本体統合済み）
- spring-boot-starter-validation を追加（Boot 2.3 以降は必須）
- spring-boot-starter-test と H2 をテスト用に追加
- Maven Wrapper を 3.9.16 に更新し実行権限を付与
- .gitignore を追加

Co-authored-by: CommandCodeBot <noreply@commandcode.ai>
EOF
```

---

### Task 2: ソースを Boot 3 の名前空間・API に移行する

このタスクを完了するとコンパイルが通る。JSR-310 コンバータは Hibernate 6 のネイティブ対応により削除する。

**Files:**
- Modify: `src/main/java/com/example/Blog.java`
- Modify: `src/main/java/com/example/BlogForm.java`
- Modify: `src/main/java/com/example/BlogController.java`
- Delete: `src/main/java/com/example/converter/LocalDateConverter.java`
- Delete: `src/main/java/com/example/converter/LocalDateTimeConverter.java`
- Delete: `src/main/java/com/example/converter/LocalTimeConverter.java`

**Interfaces:**
- Consumes: Task 1 の pom（Boot 3.5.16 / jakarta API）
- Produces: `BlogRepository.findById(Integer)` の利用、`BlogRepository.deleteById(Integer)` の利用、`BlogController` のマッピング（`GET /`, `GET /edit`, `GET /edit/{id}`, `POST /edit?edit`, `POST /edit?delete`）

- [ ] **Step 1: `Blog.java` を書き換える**

`javax.persistence.*` を `jakarta.persistence.*` に変更し、未使用 import（`com.fasterxml.jackson.annotation.JsonFormat`、`java.time.LocalTime`）を削除する。

```java
package com.example;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.*;

@Entity
public class Blog implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer blogId;

	private String title;

	private String contents;

	private LocalDate postDate;

	public Integer getBlogId() {
		return blogId;
	}

	public void setBlogId(Integer blogId) {
		this.blogId = blogId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getContents() {
		return contents;
	}

	public void setContents(String contents) {
		this.contents = contents;
	}

	public LocalDate getPostDate() {
		return postDate;
	}

	public void setPostDate(LocalDate postDate) {
		this.postDate = postDate;
	}

}
```

- [ ] **Step 2: `BlogForm.java` を書き換える**

バリデーションを jakarta 標準に統一し、未使用 import を削除する。`postDate` は `LocalDate` なので `NotBlank` ではなく `NotNull` のままにする。

```java
package com.example;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;

public class BlogForm implements Serializable {

	private Integer blogId;

	@NotBlank(message = "必須です")
	private String title;

	@NotBlank(message = "必須です")
	private String contents;

	@NotNull(message = "必須です")
	@DateTimeFormat(pattern = "yyyy/M/d")
	private LocalDate postDate;

	public Integer getBlogId() {
		return blogId;
	}

	public void setBlogId(Integer blogId) {
		this.blogId = blogId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getContents() {
		return contents;
	}

	public void setContents(String contents) {
		this.contents = contents;
	}

	public LocalDate getPostDate() {
		return postDate;
	}

	public void setPostDate(LocalDate postDate) {
		this.postDate = postDate;
	}

}
```

- [ ] **Step 3: `BlogController.java` を書き換える**

`findOne` → `findById(...).orElseThrow()`、`delete(id)` → `deleteById(id)`、`@RequestMapping(method=...)` → `@GetMapping`/`@PostMapping` に変更する。`params = "edit"` / `params = "delete"` は維持する。

```java
package com.example;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class BlogController {

	private static final Logger logger = LoggerFactory.getLogger(BlogController.class);

	@Autowired
	BlogRepository blogReposiroty;

	//新規
	@GetMapping("/edit")
	public String edit_new(Model model) {

		BlogForm blogForm = new BlogForm();

		blogForm.setBlogId(0);
		blogForm.setPostDate(LocalDate.now());

		model.addAttribute("blogForm", blogForm);

		return "edit";

	}

	//読み込み
	@GetMapping("/edit/{id}")
	public String edit(Model model, @PathVariable int id) {

		Blog blog = blogReposiroty.findById(id).orElseThrow();

		BlogForm blogForm = new BlogForm();

		blogForm.setBlogId(blog.getBlogId());
		blogForm.setTitle(blog.getTitle());
		blogForm.setContents(blog.getContents());
		blogForm.setPostDate(blog.getPostDate());

		model.addAttribute("blogForm", blogForm);

		return "edit";

	}

	//削除
	@PostMapping(value = "/edit", params = "delete")
	public String delete(Model model, BlogForm blogform) {

		blogReposiroty.deleteById(blogform.getBlogId());

		return "redirect:/";

	}

	//保存
	@PostMapping(value = "/edit", params = "edit")
	public String edit_post(@Validated BlogForm blogform, BindingResult bindingResult, Model model) {

		if (bindingResult.hasErrors()) {
			//エラーの場合は、予約画面のまま

			logger.info("エラーです");

			return "edit";
		}

		logger.info("エラーではない");

		Blog blog = new Blog();

		Integer blogId = blogform.getBlogId();

		if (blogId != null && blogId != 0) {

			blog.setBlogId(blogId);

		}

		blog.setTitle(blogform.getTitle());
		blog.setContents(blogform.getContents());
		blog.setPostDate(blogform.getPostDate());

		blogReposiroty.save(blog);

		return "redirect:/";

	}

	//読み込み
	@GetMapping("/")
	public String index(Model model) {

		List<Blog> blogs = blogReposiroty.findAll();

		List<BlogForm> blogForms = new ArrayList<>();

		for (Blog blog : blogs) {

			BlogForm blogForm = new BlogForm();

			blogForm.setBlogId(blog.getBlogId());
			blogForm.setTitle(blog.getTitle());
			blogForm.setContents(blog.getContents());
			blogForm.setPostDate(blog.getPostDate());

			blogForms.add(blogForm);
		}

		model.addAttribute("blogForms", blogForms);

		return "index";

	}

}
```

- [ ] **Step 4: JSR-310 コンバータ3クラスを削除する**

Hibernate 6 は `LocalDate`/`LocalDateTime`/`LocalTime` をネイティブにマッピングするため不要。`LocalDateTimeConverter` と `LocalTimeConverter` はどのエンティティからも参照されていない。

```bash
git rm src/main/java/com/example/converter/LocalDateConverter.java \
       src/main/java/com/example/converter/LocalDateTimeConverter.java \
       src/main/java/com/example/converter/LocalTimeConverter.java
```

- [ ] **Step 5: コンパイルが通ることを確認する**

Run: `./mvnw clean compile`
Expected: `BUILD SUCCESS`。エラーが出る場合は `javax.` の残存（`grep -rn "javax\." src/main/java` が空であること）と `delete(ID)` の残存を確認する。

- [ ] **Step 6: コミット**

```bash
git add src/main/java
git commit -F - <<'EOF'
refactor: javax から jakarta へ移行し Spring Data JPA の現行APIに追従

- Blog / BlogForm の javax.persistence, javax.validation を jakarta へ
- BlogForm の hibernate 独自 @NotBlank を jakarta 標準へ統一
- findOne を findById().orElseThrow() へ変更（旧APIは削除済み）
- delete(id) を deleteById(id) へ変更（Spring Data 3.0 で削除済み）
- @RequestMapping(method=...) を @GetMapping / @PostMapping へ
- Hibernate 6 のネイティブ JSR-310 対応により converter 3クラスを削除

Co-authored-by: CommandCodeBot <noreply@commandcode.ai>
EOF
```

---

### Task 3: 実行環境を整える（compose.yml / application.properties）

**Files:**
- Create: `compose.yml`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Consumes: Task 2 のビルド可能なアプリ
- Produces: `localhost:5432` に `mrs/mrs/mrs` で接続できる PostgreSQL 16、`docker compose up -d` / `docker compose down` の運用

- [ ] **Step 1: `compose.yml` を作成する**

```yaml
services:
  db:
    image: postgres:16
    container_name: springblog-db
    environment:
      POSTGRES_DB: mrs
      POSTGRES_USER: mrs
      POSTGRES_PASSWORD: mrs
    ports:
      - "5432:5432"
    volumes:
      - springblog-db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U mrs -d mrs"]
      interval: 5s
      timeout: 5s
      retries: 10

volumes:
  springblog-db-data:
```

- [ ] **Step 2: `application.properties` から廃止された設定を削除する**

`spring.jpa.database=POSTGRESQL` の行を削除する。他の行は変更しない。最終的な内容:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/mrs
spring.datasource.username=mrs
spring.datasource.password=mrs
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.springframework.web=DEBUG
spring.thymeleaf.cache=false
```

- [ ] **Step 3: PostgreSQL を起動する**

Run: `docker compose up -d`
Expected: `springblog-db` コンテナが起動する。

- [ ] **Step 4: DB が接続可能になるまで待つ**

Run: `docker compose exec db pg_isready -U mrs -d mrs`
Expected: `... accepting connections`。`no response` の場合は数秒待って再実行する。

- [ ] **Step 5: アプリを起動して疎通確認する**

Run: `./mvnw spring-boot:run`（別ターミナル、またはバックグラウンド実行）
Expected: 起動ログに `Tomcat started on port 8080` と `Started SpringBlogApplication` が出る。

Run: `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/`
Expected: `200`

- [ ] **Step 6: 起動したアプリを停止する**

Run: アプリのプロセスを停止する（`Ctrl-C`、または起動に使ったタスクIDを `kill`）。
Expected: 8080 ポートが解放される。以降のタスクのために `docker compose` は起動したままでよい。

- [ ] **Step 7: コミット**

```bash
git add compose.yml src/main/resources/application.properties
git commit -F - <<'EOF'
chore: PostgreSQL 16 を Docker Compose で起動できるようにする

- compose.yml を追加（mrs/mrs/mrs、5432、named volume、healthcheck）
- 廃止された spring.jpa.database=POSTGRESQL を削除

Co-authored-by: CommandCodeBot <noreply@commandcode.ai>
EOF
```

---

### Task 4: context-load テストを追加する（H2 で DB 不要）

移行の取りこぼし（DI・JPA マッピング・Bean 定義の破綻）を起動時に検出するための最小テスト。

**Files:**
- Create: `src/test/java/com/example/SpringBlogApplicationTests.java`
- Create: `src/test/resources/application.properties`

**Interfaces:**
- Consumes: Task 1 の `spring-boot-starter-test` と `h2`
- Produces: `./mvnw test` が DB なしで成功する状態

- [ ] **Step 1: 失敗する状態を確認するため PostgreSQL を止める**

Run: `docker compose stop`
Expected: `mrs` DB に接続できない状態になる（テストが DB 非依存であることを確認するため）。

- [ ] **Step 2: テストを書く**

`src/test/java/com/example/SpringBlogApplicationTests.java`:

```java
package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SpringBlogApplicationTests {

	@Test
	void contextLoads() {
	}

}
```

- [ ] **Step 3: テストを実行して失敗を確認する**

Run: `./mvnw test`
Expected: FAIL。PostgreSQL に接続できない旨（`Connection to localhost:5432 refused` 等）のエラーになる。これが「テスト用 DB 設定が無い」ことの証明。

- [ ] **Step 4: テスト用設定を追加する**

`src/test/resources/application.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
```

- [ ] **Step 5: テストを実行して成功を確認する**

Run: `./mvnw test`
Expected: `BUILD SUCCESS`、`Tests run: 1, Failures: 0, Errors: 0`。

- [ ] **Step 6: PostgreSQL を戻す**

Run: `docker compose start`
Expected: `springblog-db` が再起動する。

- [ ] **Step 7: コミット**

```bash
git add src/test
git commit -F - <<'EOF'
test: context-load テストを追加し H2 で DB 非依存にする

- @SpringBootTest による context-load テストを1本追加
- src/test/resources/application.properties で H2 インメモリに上書き
- Docker / PostgreSQL なしで ./mvnw test が通ることを確認

Co-authored-by: CommandCodeBot <noreply@commandcode.ai>
EOF
```

---

### Task 5: テンプレートの http 混在を解消する

**Files:**
- Modify: `src/main/resources/templates/index.html`
- Modify: `src/main/resources/templates/edit.html`

**Interfaces:**
- Consumes: Task 3 の起動可能なアプリ
- Produces: `https` のみで外部リソースを読み込むテンプレート

- [ ] **Step 1: http のリンクを https に置き換える**

```bash
sed -i '' 's|http://ajax.googleapis.com|https://ajax.googleapis.com|g' \
  src/main/resources/templates/index.html \
  src/main/resources/templates/edit.html
```

- [ ] **Step 2: http の取得リンクが残っていないことを確認する**

Run: `grep -rn 'src="http://\|href="http://' src/main/resources/templates`
Expected: 出力なし（`xmlns:th="http://www.thymeleaf.org"` は名前空間であり取得リンクではないため残ってよい）。

Run: `grep -c 'https://ajax.googleapis.com' src/main/resources/templates/index.html src/main/resources/templates/edit.html`
Expected: 各ファイル `4`（jQuery 本体の行が元から https のため、変換後の 3 件と合わせて 4 件になる）。jQuery UI に限定するなら `grep -c 'ajax.googleapis.com/ajax/libs/jqueryui'` が各ファイル `3`。

- [ ] **Step 3: ブラウザで混在コンテンツが出ないことを確認する**

Run: `./mvnw spring-boot:run` → ブラウザで `http://localhost:8080/` と `http://localhost:8080/edit` を開き、開発者コンソールを確認する。
Expected: Mixed Content の警告・ブロックが無く、Datepicker と DataTables が動作する。
確認後、アプリを停止する。

- [ ] **Step 4: コミット**

```bash
git add src/main/resources/templates
git commit -F - <<'EOF'
fix: テンプレートの jQuery UI 参照を https に変更

ブラウザが混在コンテンツとしてブロックするため http を https へ。
Bootstrap / DataTables / jQuery のバージョンは据え置き。

Co-authored-by: CommandCodeBot <noreply@commandcode.ai>
EOF
```

---

### Task 6: README.md を作成する

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: Task 1〜5 の内容（バージョン、起動手順、エンドポイント、DB、テスト）
- Produces: 仕様と運用手順を網羅した README

- [ ] **Step 1: `README.md` を作成する**

````markdown
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
````

- [ ] **Step 2: Markdown の体裁を確認する**

Run: `grep -c '^## ' README.md`
Expected: `12`（概要／技術スタック／必要要件／ディレクトリ構成／セットアップと起動／設定／画面・エンドポイント仕様／DB 仕様／テスト／ビルドと配布／既知の制約／トラブルシューティング）。

- [ ] **Step 3: コミット**

```bash
git add README.md
git commit -F - <<'EOF'
docs: 仕様と運用手順を記載した README を追加

概要、技術スタック、必要要件、ディレクトリ構成、起動方法、
設定、エンドポイント仕様、DB 仕様、テスト、既知の制約、
トラブルシューティングを記載。

Co-authored-by: CommandCodeBot <noreply@commandcode.ai>
EOF
```

---

### Task 7: エンドツーエンド検証とマージ

**Files:** なし（検証のみ。不具合があれば該当タスクに戻って修正する）

**Interfaces:**
- Consumes: Task 1〜6 のすべて
- Produces: master へマージ可能な状態

補足（実装時に判明・修正済み）: 新規作成 `POST /edit?edit` は、フォームが `blogId=0` を送るため `save()` が `merge` を選び、Hibernate 6 では対象行が存在しないため `StaleObjectStateException`（HTTP 500）となっていた。Hibernate 5 では新規挿入されていた挙動であり、移行による回帰。Task 2 の `BlogController.edit_post` は、id が `null` または `0` のとき id を設定しない形に修正済み。なお `GET /edit/{存在しないid}` が 500 になるのは、移行前の `findOne` が null を返して NPE になっていたのと挙動同等で、回帰ではない。

- [ ] **Step 1: 既存 DB のバックアップを取得する**

既存環境に Postgres のデータがある場合、Hibernate 6 の `ddl-auto=update` でスキーマが変わり得るため、検証前にダンプを取得する。

```bash
docker compose exec -T db pg_dump -U mrs -d mrs > backup-before-migration.sql
```

- [ ] **Step 2: クリーンビルドとテスト**

Run: `./mvnw clean test`
Expected: `BUILD SUCCESS`、`Tests run: 1, Failures: 0, Errors: 0`。

- [ ] **Step 3: アプリを起動する**

Run: `./mvnw spring-boot:run`
Expected: `Started SpringBlogApplication` がログに出る。Hibernate 6 によるスキーマ差分の警告が出た場合は内容を記録する。

- [ ] **Step 4: 一覧と新規作成を確認する**

Run: `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/`
Expected: `200`

Run:
```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/edit \
  -d 'blogId=0' -d 'title=移行テスト' -d 'contents=本文' -d 'postDate=2026/9/21' -d 'edit=投稿'
```
Expected: `302`（`/` へリダイレクト）

Run: `curl -s http://localhost:8080/ | grep -c '移行テスト'`
Expected: `1` 以上

- [ ] **Step 5: バリデーションが有効なことを確認する**

Run:
```bash
curl -s -X POST http://localhost:8080/edit \
  -d 'blogId=0' -d 'title=' -d 'contents=' -d 'postDate=2026/9/21' -d 'edit=投稿' | grep -c '必須です'
```
Expected: `1` 以上（`spring-boot-starter-validation` が効いていることの確認）

- [ ] **Step 6: 編集と削除を確認する**

一覧に表示された ID を使って実行する。

Run:
```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/edit \
  -d 'blogId=1' -d 'title=移行テスト更新' -d 'contents=本文2' -d 'postDate=2026/9/22' -d 'edit=投稿'
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/edit \
  -d 'blogId=1' -d 'delete=削除'
```
Expected: いずれも `302`。`curl -s http://localhost:8080/ | grep -c '移行テスト更新'` が `0` になる。

- [ ] **Step 7: ファイルアップロードのエンドポイントが応答することを確認する**

`FileUploadRestController` の保存先はファイルシステム直下の `/uploadfile` 固定であり、macOS では `/` が読み取り専用のため書き込みに失敗する（移行前からの既存の挙動。今回は変更しない）。したがってここでは「ルーティングとリクエスト処理が Boot 3 で動くこと」を確認する。全文検索で `/` が読み取り専用であることは確認済み（`test -w /` → NOT writable、`mkdir /uploadfile-probe` → Read-only file system）。

Run:
```bash
echo hello > /tmp/upload-test.txt
curl -s -X POST http://localhost:8080/file/upload \
  -F 'upload_file=@/tmp/upload-test.txt' -F 'filetype=upload-test.txt'
echo
```
Expected: `error!` が返る（HTTP 200）。接続拒否や `404`、`500` にならなければ、エンドポイント自体は移行できている。書き込みが成功する環境（Linux 等で `/` に書き込める場合）では `You successfully uploaded.` が返り `/uploadfile/<timestamp>/upload-test.txt` が作成される。

補足: この制約は README の「既知の制約」に記載する（Task 6 で反映済み）。

- [ ] **Step 8: ブラウザで画面を確認する**

ブラウザで `http://localhost:8080/` と `http://localhost:8080/edit` を開く。
Expected: DataTables の表が描画される／行クリックで `/edit/{id}` へ遷移する／Datepicker が日本語で表示される／Mixed Content の警告が無い。

- [ ] **Step 9: アプリを停止する**

Run: 起動したプロセスを停止する。
Expected: 8080 ポートが解放される（`lsof -nP -iTCP:8080 -sTCP:LISTEN` が空）。

- [ ] **Step 10: 作業ブランチをマージする**

ユーザーの確認を得てから実行する。

```bash
git checkout master
git merge --no-ff feature/spring-boot-3
```

- [ ] **Step 11: 後片付け**

`backup-before-migration.sql` はリポジトリにコミットせず、必要ならユーザーの任意の場所へ移動する。
