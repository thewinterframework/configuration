package com.thewinterframework.configurate.serializer.processor;

import com.google.auto.service.AutoService;
import com.thewinterframework.configurate.serializer.ConfigurateSerializer;
import com.thewinterframework.configurate.serializer.provider.ConfigurateSerializerProvider;
import com.thewinterframework.processor.clazz.ClassWireProcessor;
import com.thewinterframework.processor.handler.WinterAnnotationProcessor;

import java.lang.annotation.Annotation;

/**
 * An annotation processor that registry all serializers
 * from methods annotated with {@link ConfigurateSerializer}.
 */
@AutoService(WinterAnnotationProcessor.class)
public class ConfigurateSerializerProviderAnnotationProcessor extends ClassWireProcessor {
    @Override
    protected Class<? extends Annotation> wiredAnnotation() {
        return ConfigurateSerializerProvider.class;
    }
}
