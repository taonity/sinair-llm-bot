package org.taonity.sinairllmbot.security.config

import jakarta.servlet.DispatcherType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.taonity.sinairllmbot.common.config.AppProperties
import org.taonity.sinairllmbot.security.handler.OAuth2AuthenticationFailureHandler
import org.taonity.sinairllmbot.security.handler.SpaCsrfTokenRequestHandler
import org.taonity.sinairllmbot.security.filter.UserMdcFilter
import org.taonity.sinairllmbot.security.service.OAuth2UserPersistenceService
import org.taonity.sinairllmbot.security.service.OidcUserPersistenceService

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val oAuth2UserPersistenceService: OAuth2UserPersistenceService,
    private val oidcUserPersistenceService: OidcUserPersistenceService,
    private val oAuth2AuthenticationFailureHandler: OAuth2AuthenticationFailureHandler,
    private val spaCsrfTokenRequestHandler: SpaCsrfTokenRequestHandler,
    private val appProperties: AppProperties,
    private val userMdcFilter: UserMdcFilter,
) {

    @Bean
    @Throws(Exception::class)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .addFilterAfter(userMdcFilter, SecurityContextHolderFilter::class.java)
            .authorizeHttpRequests { requests ->
                requests.dispatcherTypeMatchers(DispatcherType.FORWARD).permitAll()
                    .requestMatchers(
                        "/actuator/**",
                        "/"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .logout { l ->
                l.logoutSuccessHandler { _, response, _ ->
                    response.status = 200
                }.permitAll()
            }
            .exceptionHandling { e ->
                e.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .csrf { c ->
                val csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
                csrfTokenRepository.setCookieName(appProperties.csrfCookieName)
                csrfTokenRepository.setCookieCustomizer { builder ->
                    builder.secure(appProperties.cookie.secure).sameSite(appProperties.cookie.sameSite)
                }
                c.csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(spaCsrfTokenRequestHandler)
            }
            .oauth2Login { o ->
                o.userInfoEndpoint { u ->
                    u.userService(oAuth2UserPersistenceService)
                    u.oidcUserService(oidcUserPersistenceService)
                }
                    .defaultSuccessUrl(appProperties.defaultSuccessUrl, true)
                    .failureHandler(oAuth2AuthenticationFailureHandler)
            }
            .oauth2Client(Customizer.withDefaults())

        return http.build()
    }
}
