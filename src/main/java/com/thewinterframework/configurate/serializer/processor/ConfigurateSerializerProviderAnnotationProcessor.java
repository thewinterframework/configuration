package com.thewinterframework.configurate.serializer.processor;

import com.thewinterframework.configurate.serializer.ConfigurateSerializer;
import com.thewinterframework.processor.provider.ClassListProviderAnnotationProcessor;

import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * An annotation processor that registry all serializers
 * from methods annotated with {@link ConfigurateSerializer}.
 */
public class ConfigurateSerializerProviderAnnotationProcessor extends ClassListProviderAnnotationProcessor {

  @Override
  protected Set<Class<? extends Annotation>> getSupportedAnnotations() {
    return Set.of();
  }
}
