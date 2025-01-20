package com.thewinterframework.configurate.serializer.processor;

import com.thewinterframework.configurate.serializer.ConfigurateSerializer;
import com.thewinterframework.processor.provider.ClassListProviderAnnotationProcessor;

import javax.lang.model.element.Element;
import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * An annotation processor that generates a module for all classes annotated with {@link ConfigurateSerializer}.
 */
public class ConfigurateSerializerAnnotationProcessor extends ClassListProviderAnnotationProcessor {

	@Override
	protected boolean filterClass(Element element) {
		return isChild(element.asType(), "org.spongepowered.configurate.serialize.TypeSerializer");
	}

	@Override
	protected Set<Class<? extends Annotation>> getSupportedAnnotations() {
		return Set.of(ConfigurateSerializer.class);
	}
}
