package com.example.demospannerjdbc.web;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SelectedDbWriterFilter extends OncePerRequestFilter {
	private String selectedDb;

	public SelectedDbWriterFilter(@Value("${demo.spanner.selecteddb:unknown}") String selectedDb) {
		this.selectedDb = selectedDb;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		response.addHeader("X-SELECTED-DB", selectedDb);

		filterChain.doFilter(request, response);
	}
}
