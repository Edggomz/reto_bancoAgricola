package com.bancoagricola.ruta.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Inyecta el id del cliente autenticado (header Authorization: Bearer). Sin sesión válida responde 401. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentCustomer {}
