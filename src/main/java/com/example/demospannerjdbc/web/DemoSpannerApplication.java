package com.example.demospannerjdbc.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.example.demospannerjdbc.web.mybatis.SpannerLocalDateTimeTypeHandler;
import com.github.dozermapper.spring.DozerBeanMapperFactoryBean;

@SpringBootApplication
public class DemoSpannerApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoSpannerApplication.class, args);
	}

	@Bean
	public SpannerLocalDateTimeTypeHandler spannerLocalDateTimeTypeHandler() {
		return new SpannerLocalDateTimeTypeHandler();
	}

	@Bean
	public DozerBeanMapperFactoryBean beanMapper() {
		return new DozerBeanMapperFactoryBean();
	}
}
