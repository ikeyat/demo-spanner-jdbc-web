package com.example.demospannerjdbc.web;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SelectedDbWriterFilter extends OncePerRequestFilter {
	private Environment environment;

	public SelectedDbWriterFilter(Environment environment) {
		this.environment = environment;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String db = "unknown";

		List<String> profiles = Arrays.asList(environment.getActiveProfiles());
		if (profiles.contains("h2")) {
			db = "h2";
		} else if (profiles.contains("spanner")) {
			db = "spanner";
		}
		response.addHeader("X-SELECTED-DB", db);

		filterChain.doFilter(request, response);
	}
}
