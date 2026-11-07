package com.thewinterframework.configurate.serializer.processor;

import com.google.auto.service.AutoService;
import com.thewinterframework.configurate.serializer.ConfigurateSerializer;
import com.thewinterframework.processor.context.ProcessorContext;
import com.thewinterframework.processor.extension.WinterProcessorExtension;
import com.thewinterframework.utils.reflect.ProcessorUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.lang.annotation.Annotation;

/**
 * An annotation processor that generates a module for all classes annotated with {@link ConfigurateSerializer}.
 */
@AutoService(WinterProcessorExtension.class)
public class ConfigurateSerializerAnnotationProcessor extends SerializerWireProcessor {

    @Override
    protected boolean filter(final TypeElement annotation, final Element element, final ProcessorContext ctx) {
        return ProcessorUtils.isChild(element.asType(), "org.spongepowered.configurate.serialize.TypeSerializer");
    }

    @Override
    protected Class<? extends Annotation> wiredAnnotation() {
        return ConfigurateSerializer.class;
    }
}
