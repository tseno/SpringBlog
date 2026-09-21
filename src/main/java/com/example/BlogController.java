package com.example;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

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

		Blog blog = blogReposiroty.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

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

		Integer blogId = blogform.getBlogId();

		//新規作成中や blogId 欠損の場合は削除対象がないため何もしない
		if (blogId != null && blogId != 0) {

			blogReposiroty.deleteById(blogId);

		}

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

			//編集中に他者が削除した場合など、対象が存在しなければ 404 とする
			if (!blogReposiroty.existsById(blogId)) {

				throw new ResponseStatusException(HttpStatus.NOT_FOUND);

			}

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
