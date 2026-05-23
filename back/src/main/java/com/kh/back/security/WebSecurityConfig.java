package com.kh.back.security;

import com.kh.back.config.OAuth2SuccessHandler;
import com.kh.back.jwt.TokenProvider;
import com.kh.back.security.JwtAuthenticationEntryPoint;
import com.kh.back.security.JwtAccessDeniedHandler;
import com.kh.back.jwt.JwtFilter;
import com.kh.back.service.auth.OAuth2UserService;
import com.kh.back.service.auth.RedisTokenBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
public class WebSecurityConfig implements WebMvcConfigurer {

	private final OAuth2SuccessHandler oAuth2SuccessHandler;
	private final TokenProvider tokenProvider;
	private final RedisTokenBlacklistService blacklistService;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
	private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
	private final OAuth2UserService oauth2UserService;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.cors().configurationSource(request -> {
					CorsConfiguration corsConfig = new CorsConfiguration();
					corsConfig.setAllowedOrigins(List.of("http://localhost:3000")); // 프론트엔드 도메인
					corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
					corsConfig.setAllowCredentials(true);
					corsConfig.addAllowedHeader("*");
					corsConfig.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
					return corsConfig;
				})
				.and()
				.httpBasic()
				.and()
				.csrf().disable()
				.sessionManagement()
				.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
				.and()
				.exceptionHandling()
				.authenticationEntryPoint(jwtAuthenticationEntryPoint)
				.accessDeniedHandler(jwtAccessDeniedHandler)
				.and()
				.authorizeRequests()
				// 여기에서 Forum 및 관련 엔드포인트를 permitAll 처리합니다.
				.antMatchers("/forum/**", "/api/forums/**").permitAll()
				.antMatchers(HttpMethod.GET, "/recipe/reaction-summary").permitAll()
				.antMatchers(
						"/", "/static/**", "/auth/**", "/ws/**", "/oauth2/**",
						"/api/v1/auth/**", "/api/v1/payments/**", "/chat/**", "/flask/**", "/review/**", "/comments/**",
						"/**/public/**", "/pay/**", "/test/**", "/search", "/api/foodrecipes/**", "/api/recipes/**",
						"/favicon.ico", "/manifest.json", "/logo192.png", "/logo512.png",
						"/admin/**", "/api/search/**", "/api/cocktails/**"
				).permitAll()
				.antMatchers("/v2/api-docs", "/swagger-resources/**", "/swagger-ui.html", "/webjars/**", "/swagger/**", "/sign-api/exception").permitAll()
				.antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.antMatchers("/api/profile/**", "/api/profile/getId").permitAll()
				.anyRequest().authenticated()
				.and()
				.oauth2Login(oauth2 -> oauth2
						.authorizationEndpoint(endpoint -> endpoint.baseUri("/api/v1/auth/oauth2"))
						.redirectionEndpoint(endpoint -> endpoint.baseUri("/oauth2/callback/*"))
						.userInfoEndpoint(endpoint -> endpoint.userService(oauth2UserService))
						.successHandler(oAuth2SuccessHandler)
				)
				.apply(new JwtSecurityConfig(tokenProvider, blacklistService));

		return http.build();
	}
}
