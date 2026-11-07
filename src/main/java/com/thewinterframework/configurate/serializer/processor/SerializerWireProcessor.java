package com.thewinterframework.configurate.serializer.processor;

import com.thewinterframework.processor.WinterProcessor;
import com.thewinterframework.processor.context.ProcessorContext;
import com.thewinterframework.processor.extension.WinterProcessorExtension;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.TreeSet;

/** Collects deterministic serializer wires using Winter 3's processor SPI. */
public abstract class SerializerWireProcessor implements WinterProcessorExtension {
    private final Set<String> classes = new TreeSet<>();

    protected abstract Class<? extends Annotation> wiredAnnotation();

    @Override
    public final Set<String> supportedAnnotationNames() {
        return Set.of(wiredAnnotation().getCanonicalName());
    }

    protected boolean filter(final TypeElement annotation, final Element element, final ProcessorContext context) {
        return true;
    }

    @Override
    public final void process(final TypeElement annotation, final Set<? extends Element> elements,
                              final ProcessorContext context) {
        elements.stream().filter(element -> element.getKind().isClass())
                .filter(element -> filter(annotation, element, context))
                .map(element -> element.asType() + ".class").forEach(classes::add);
    }

    @Override
    public final void onProcessingFinished(final ProcessorContext context) {
        WinterProcessor.writeClassWire(context, wiredAnnotation().getSimpleName() + "ListWire",
                context.getWinterModulePackageString(), classes);
    }
}
