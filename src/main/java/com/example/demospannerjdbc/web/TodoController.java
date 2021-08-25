package com.example.demospannerjdbc.web;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demospannerjdbc.web.model.Todo;
import com.example.demospannerjdbc.web.resource.TodoResource;
import com.example.demospannerjdbc.web.service.TodoService;
import com.github.dozermapper.core.Mapper;

@RestController
@RequestMapping("todos")
public class TodoController {
	private static final Logger LOG = LoggerFactory.getLogger(TodoController.class);

	private TodoService todoService;

	private Mapper beanMapper;

	public TodoController(TodoService todoService, Mapper beanMapper) {
		this.todoService = todoService;
		this.beanMapper = beanMapper;
	}

	@GetMapping // (1)
	@ResponseStatus(HttpStatus.OK) // (2)
	public List<TodoResource> getTodos() {
		Iterable<Todo> todos = todoService.findAll();
		List<TodoResource> todoResources = new ArrayList<>();
		for (Todo todo : todos) {
			todoResources.add(beanMapper.map(todo, TodoResource.class)); // (3)
		}
		printTodos("findAll() is requested.");
		return todoResources; // (4)
	}

	@PostMapping // (1)
	@ResponseStatus(HttpStatus.CREATED) // (2)
	public TodoResource postTodos(@RequestBody @Validated TodoResource todoResource) { // (3)
		Todo todo = beanMapper.map(todoResource, Todo.class);
		Todo createdTodo = todoService.create(todo); // (4)
		TodoResource createdTodoResponse = beanMapper.map(createdTodo, TodoResource.class); // (5)
		printTodos("create() is requested.");
		return createdTodoResponse; // (6)
	}

	@PutMapping("{todoId}") // (1)
	@ResponseStatus(HttpStatus.OK)
	public TodoResource putTodo(@PathVariable("todoId") String todoId) { // (2)
		Todo finishedTodo = todoService.finish(todoId); // (3)
		TodoResource finishedTodoResource = beanMapper.map(finishedTodo, TodoResource.class);
		printTodos("finish() is requested.");
		return finishedTodoResource;
	}

	@DeleteMapping("{todoId}") // (1)
	@ResponseStatus(HttpStatus.NO_CONTENT) // (2)
	public void deleteTodo(@PathVariable("todoId") String todoId) { // (3)
		todoService.delete(todoId); // (4)
		printTodos("delete() is requested.");
	}

	private void printTodos(String memo) {
		Iterable<Todo> todos = todoService.findAll();
		LOG.info("--printTodos: " + memo + " --");
		todos.forEach(item -> LOG.info(ToStringBuilder.reflectionToString(item)));
		LOG.info("-------------------------");
	}
}
