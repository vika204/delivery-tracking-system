package com.delivery.webui.controller.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller of a dev-only API without authentication: {@link LoopbackInterceptor}
 * accepts only callers from this machine. {@code value} is the feature name in the 403 message.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LocalDevOnly {

    String value();
}
