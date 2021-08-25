package com.example.demospannerjdbc.web.model;

import java.time.LocalDateTime;

public class Todo {

	private String id;

	private String title;

	private Boolean finished;

	private LocalDateTime createdAt;

	public Todo() {
		super();
	}

	public Todo(String title) {
		super();
		this.title = title;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Boolean getFinished() {
		return finished;
	}

	public void setFinished(Boolean finished) {
		this.finished = finished;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

}
