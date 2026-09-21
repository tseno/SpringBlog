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
