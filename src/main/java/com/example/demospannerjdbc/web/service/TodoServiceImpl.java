package com.example.demospannerjdbc.web.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demospannerjdbc.web.model.Todo;
import com.example.demospannerjdbc.web.repository.TodoRepository;

// https://terasolunaorg.github.io/guideline/5.7.0.RELEASE/ja/Tutorial/TutorialTodo.html#service
// を真似して作る
@Service
@Transactional
public class TodoServiceImpl implements TodoService {
	private static final long MAX_UNFINISHED_COUNT = 5;

	private TodoRepository todoRepository;

	public TodoServiceImpl(TodoRepository todoRepository) {
		this.todoRepository = todoRepository;
	}

	@Transactional(readOnly = true)
	public Iterable<Todo> findAll() {
		return todoRepository.findAll();
	}

	@Override
	public Todo create(Todo todo) {
		long unfinishedCount = todoRepository.countByFinished(false);
		if (unfinishedCount >= MAX_UNFINISHED_COUNT) {
			throw new RuntimeException(
					"[E001] The count of un-finished Todo must not be over " + MAX_UNFINISHED_COUNT + ".");
		}

		String todoId = UUID.randomUUID().toString();
		LocalDateTime createdAt = LocalDateTime.now();

		todo.setId(todoId);
		todo.setCreatedAt(createdAt);
		todo.setFinished(false);

		todoRepository.insert(todo);
		return todo;
	}

	@Override
	public Todo finish(String todoId) {
		Todo todo = findOne(todoId);
		if (todo.getFinished()) {
			throw new RuntimeException("[E002] The requested Todo is already finished. (id=" + todoId + ")");
		}
		todo.setFinished(true);
		todoRepository.update(todo);
		return todo;
	}

	@Override
	public void delete(String todoId) {
		Todo todo = findOne(todoId);
		todoRepository.delete(todo);
	}

	private Todo findOne(String todoId) {
		return todoRepository.findById(todoId).orElseThrow(() -> {
			throw new RuntimeException("[E404] The requested Todo is not found. (id=" + todoId + ")");
		});
	}

}
