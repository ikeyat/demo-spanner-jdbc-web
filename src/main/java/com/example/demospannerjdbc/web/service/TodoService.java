package com.example.demospannerjdbc.web.service;

import com.example.demospannerjdbc.web.model.Todo;

public interface TodoService {
	Iterable<Todo> findAll();

	Todo create(Todo todo);

	Todo finish(String todoId);

	void delete(String todoId);
}
