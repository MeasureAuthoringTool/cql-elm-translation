package gov.cms.mat.cql_elm_translation.config.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.MappedInterceptor;

/** Class creates the interceptors used in header processing */
@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

  /**
   * Create the Request Interceptor
   *
   * @return the Interceptor used for all incoming servlet request
   */
  @Bean
  public MappedInterceptor requestInterceptor() {
    // https://stackoverflow.com/questions/46953039/spring-interceptor-not-working-in-spring-data-rest-urls
    return new MappedInterceptor(new String[] {"/**"}, new RequestHeaderInterceptor());
  }

  @Bean
  public FilterRegistrationBean<BufferedStreamFilter> loggingFilter() {
    FilterRegistrationBean<BufferedStreamFilter> registrationBean = new FilterRegistrationBean<>();

    registrationBean.setFilter(new BufferedStreamFilter());
    registrationBean.addUrlPatterns("/*");

    return registrationBean;
  }
}
