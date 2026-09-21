package com.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlogControllerTest {

	private static final int MISSING_ID = 424242;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BlogRepository blogRepository;

	//作成した記事が採番され、そのまま読み込めること（Hibernate 6 の IDENTITY と LocalDate マッピングの確認）
	@Test
	void createsPostAndLoadsItBack() throws Exception {

		mockMvc.perform(post("/edit")
				.param("edit", "")
				.param("blogId", "0")
				.param("title", "タイトル")
				.param("contents", "本文")
				.param("postDate", "2026/9/21"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/"));

		List<Blog> blogs = blogRepository.findAll();

		assertThat(blogs).hasSize(1);

		Blog created = blogs.get(0);

		assertThat(created.getBlogId()).isNotNull().isNotZero();
		assertThat(created.getTitle()).isEqualTo("タイトル");
		assertThat(created.getContents()).isEqualTo("本文");
		assertThat(created.getPostDate()).isEqualTo(LocalDate.of(2026, 9, 21));

		MvcResult result = mockMvc.perform(get("/edit/" + created.getBlogId()))
				.andExpect(status().isOk())
				.andExpect(view().name("edit"))
				.andReturn();

		BlogForm form = (BlogForm) result.getModelAndView().getModel().get("blogForm");

		assertThat(form.getBlogId()).isEqualTo(created.getBlogId());
		assertThat(form.getTitle()).isEqualTo("タイトル");
		assertThat(form.getPostDate()).isEqualTo(LocalDate.of(2026, 9, 21));

	}

	//既存記事の更新が新規行を作らずに反映されること
	@Test
	void updatesExistingPost() throws Exception {

		Blog saved = blogRepository.save(newBlog("旧タイトル"));

		mockMvc.perform(post("/edit")
				.param("edit", "")
				.param("blogId", String.valueOf(saved.getBlogId()))
				.param("title", "新タイトル")
				.param("contents", "本文")
				.param("postDate", "2026/9/21"))
				.andExpect(redirectedUrl("/"));

		assertThat(blogRepository.findAll()).hasSize(1);
		assertThat(blogRepository.findById(saved.getBlogId()).orElseThrow().getTitle()).isEqualTo("新タイトル");

	}

	//存在しない ID の読み込みは 500 ではなく 404
	@Test
	void returnsNotFoundForUnknownId() throws Exception {

		mockMvc.perform(get("/edit/" + MISSING_ID))
				.andExpect(status().isNotFound());

	}

	//編集中に他者が削除した記事の保存は 500 ではなく 404
	@Test
	void returnsNotFoundWhenSavingDeletedPost() throws Exception {

		mockMvc.perform(post("/edit")
				.param("edit", "")
				.param("blogId", String.valueOf(MISSING_ID))
				.param("title", "タイトル")
				.param("contents", "本文")
				.param("postDate", "2026/9/21"))
				.andExpect(status().isNotFound());

		assertThat(blogRepository.findAll()).isEmpty();

	}

	//必須項目が未入力なら保存せず編集画面を再表示すること
	@Test
	void redisplaysFormOnValidationError() throws Exception {

		mockMvc.perform(post("/edit")
				.param("edit", "")
				.param("blogId", "0")
				.param("title", "")
				.param("contents", "本文")
				.param("postDate", "2026/9/21"))
				.andExpect(status().isOk())
				.andExpect(view().name("edit"))
				.andExpect(model().attributeHasFieldErrors("blogForm", "title"));

		assertThat(blogRepository.findAll()).isEmpty();

	}

	//削除が行を消すこと
	@Test
	void deletesPost() throws Exception {

		Blog saved = blogRepository.save(newBlog("消す記事"));

		mockMvc.perform(post("/edit")
				.param("delete", "")
				.param("blogId", String.valueOf(saved.getBlogId())))
				.andExpect(redirectedUrl("/"));

		assertThat(blogRepository.findAll()).isEmpty();

	}

	//blogId のない削除は 500 にならず、何も消さないこと
	@Test
	void ignoresDeleteWithoutBlogId() throws Exception {

		blogRepository.save(newBlog("残る記事"));

		mockMvc.perform(post("/edit")
				.param("delete", ""))
				.andExpect(redirectedUrl("/"));

		assertThat(blogRepository.findAll()).hasSize(1);

	}

	//存在しない行の削除はエラーにならない（Spring Data 3 での挙動。旧版は例外だった）
	@Test
	void ignoresDeleteOfMissingRow() throws Exception {

		mockMvc.perform(post("/edit")
				.param("delete", "")
				.param("blogId", String.valueOf(MISSING_ID)))
				.andExpect(redirectedUrl("/"));

	}

	//一覧が保存済みの記事を描画すること
	@Test
	void rendersIndex() throws Exception {

		blogRepository.save(newBlog("一覧の記事"));

		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(view().name("index"))
				.andExpect(model().attributeExists("blogForms"));

	}

	private Blog newBlog(String title) {

		Blog blog = new Blog();

		blog.setTitle(title);
		blog.setContents("本文");
		blog.setPostDate(LocalDate.of(2026, 9, 21));

		return blog;

	}

}
