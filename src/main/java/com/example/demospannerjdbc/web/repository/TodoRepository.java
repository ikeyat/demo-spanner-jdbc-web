package com.example.demospannerjdbc.web.repository;

import java.util.Collection;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.example.demospannerjdbc.web.model.Todo;

@Mapper
public interface TodoRepository {
	long countByFinished(Boolean finished);

	Collection<Todo> findAll();

	long insert(Todo todo);

	long update(Todo todo);

	long delete(Todo todo);

	Optional<Todo> findById(String id);
}
