package com.example;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class BlogController {
	
	private static final Logger logger = LoggerFactory.getLogger(BlogController.class);
	
	@Autowired
	BlogRepository blogReposiroty;

	//新規
	@RequestMapping(value="/edit", method=RequestMethod.GET)
	public String edit_new(Model model) {
		
		BlogForm blogForm = new BlogForm();
		
		blogForm.setBlogId(0);
		blogForm.setPostDate(LocalDate.now());

		model.addAttribute("blogForm", blogForm);

		return "edit";
		
	}
	
	//読み込み
	@RequestMapping(value="/edit/{id}", method=RequestMethod.GET)
	public String edit(Model model, @PathVariable int id) {
		
		model.addAttribute("title", "test");
		model.addAttribute("maxlength", "10");
		model.addAttribute("postdate", LocalDateTime.now());
		return "edit";
		
	}
	
	//保存
	@RequestMapping(value="/edit", method=RequestMethod.POST)
	public String edit_post(@Validated BlogForm form, BindingResult bindingResult,  Model model) {
		
		if (bindingResult.hasErrors()) {
			//エラーの場合は、予約画面のまま

			logger.info("エラーです");

			return "edit";
		}
		
		logger.info("エラーではない");
		
		//blogReposiroty.save(blog);
		
		//model.addAttribute("blogForm", blogForm);
		
		return "edit";
		
	}

}
