package com.thewinterframework.configurate.serializer.provider;

import com.google.inject.ScopeAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation that specifies that a class is a Configurate serializer provider.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ScopeAnnotation
public @interface ConfigurateSerializerProvider {
}
