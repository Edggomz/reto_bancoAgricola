package com.bancoagricola.ruta.web;

import com.bancoagricola.ruta.service.AuthService;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentCustomerResolver implements HandlerMethodArgumentResolver {
  private final AuthService auth;

  public CurrentCustomerResolver(AuthService auth) {
    this.auth = auth;
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentCustomer.class) && parameter.getParameterType() == String.class;
  }

  @Override
  public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                NativeWebRequest request, WebDataBinderFactory binderFactory) {
    return auth.requireCustomerId(request.getHeader(HttpHeaders.AUTHORIZATION));
  }
}
