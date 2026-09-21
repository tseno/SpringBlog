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
