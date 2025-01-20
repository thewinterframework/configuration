package com.thewinterframework.configurate.feedback.resource.provider;

import com.google.common.base.Splitter;
import com.thewinterframework.configurate.feedback.Feedback;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public class FeedbackNodeProvider implements FeedbackProvider {

    private final ConfigurationNode parentNode;

    public FeedbackNodeProvider(ConfigurationNode parentNode) {
        this.parentNode = parentNode;
    }

    @Override
    public @Nullable Feedback getFeedback(String key) throws SerializationException {
        final var node = parentNode.node(
                Splitter.on('.')
                        .splitToList(key)
                        .toArray()
        );

        if (node.empty() || node.virtual()) {
            return null;
        }

        return node.get(Feedback.class);
    }

}
