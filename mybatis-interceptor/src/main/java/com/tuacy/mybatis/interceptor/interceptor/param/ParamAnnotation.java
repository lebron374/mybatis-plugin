package com.tuacy.mybatis.interceptor.interceptor.param;

import com.tuacy.mybatis.interceptor.interceptor.encryptresultfield.IEncryptResultFieldStrategy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 通过注解来表明，我们需要对那个字段进行加密
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface ParamAnnotation {
    String[] srcKey() default {};

    String[] destKey() default {};
}
