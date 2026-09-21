package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileUploadRestControllerTest {

	//通常のファイル名はそのまま使えること
	@Test
	void keepsPlainFileName() {

		assertThat(FileUploadRestController.safeFileName("up.txt")).isEqualTo("up.txt");
		assertThat(FileUploadRestController.safeFileName("日本語のファイル.csv")).isEqualTo("日本語のファイル.csv");

	}

	//ディレクトリ要素を含む指定からはファイル名だけを取り出すこと
	@Test
	void stripsDirectoryComponents() {

		assertThat(FileUploadRestController.safeFileName("../../etc/cron.d/x")).isEqualTo("x");
		assertThat(FileUploadRestController.safeFileName("/etc/passwd")).isEqualTo("passwd");
		assertThat(FileUploadRestController.safeFileName("sub/dir/up.txt")).isEqualTo("up.txt");
		assertThat(FileUploadRestController.safeFileName("a/../b.txt")).isEqualTo("b.txt");

	}

	//ファイル名として使えない指定は拒否すること
	@Test
	void rejectsUnusableNames() {

		assertThat(FileUploadRestController.safeFileName("..")).isNull();
		assertThat(FileUploadRestController.safeFileName(".")).isNull();
		assertThat(FileUploadRestController.safeFileName("")).isNull();
		assertThat(FileUploadRestController.safeFileName("/")).isNull();
		assertThat(FileUploadRestController.safeFileName("../..")).isNull();
		assertThat(FileUploadRestController.safeFileName("up\0.txt")).isNull();
		assertThat(FileUploadRestController.safeFileName(null)).isNull();

	}

}
