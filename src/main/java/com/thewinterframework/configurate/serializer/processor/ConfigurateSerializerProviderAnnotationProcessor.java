package com.thewinterframework.configurate.serializer.processor;

import com.google.auto.service.AutoService;
import com.thewinterframework.configurate.serializer.ConfigurateSerializer;
import com.thewinterframework.configurate.serializer.provider.ConfigurateSerializerProvider;
import com.thewinterframework.processor.extension.WinterProcessorExtension;

import java.lang.annotation.Annotation;

/**
 * An annotation processor that registry all serializers
 * from methods annotated with {@link ConfigurateSerializer}.
 */
@AutoService(WinterProcessorExtension.class)
public class ConfigurateSerializerProviderAnnotationProcessor extends SerializerWireProcessor {
    @Override
    protected Class<? extends Annotation> wiredAnnotation() {
        return ConfigurateSerializerProvider.class;
    }
}
