package com.thewinterframework.configurate.feedback.resource;

import com.thewinterframework.configurate.feedback.Feedback;
import com.thewinterframework.configurate.feedback.resource.provider.FeedbackNodeProvider;
import com.thewinterframework.configurate.feedback.resource.provider.FeedbackProvider;
import com.thewinterframework.plugin.WinterPlugin;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.HashMap;
import java.util.Map;

public class FeedbackResourceManager {

    private static final FeedbackResourceManager INSTANCE = new FeedbackResourceManager();

    private final Map<String, FeedbackProvider> providers = new HashMap<>();

    public static FeedbackResourceManager instance() {
        return INSTANCE;
    }

    public FeedbackResourceManager registerProvider(final WinterPlugin plugin, final ConfigurationNode node) {
        final var namespace = plugin.namespace();
        final var provider = new FeedbackNodeProvider(node);

        providers.put(namespace, provider);
        return this;
    }

    /**
     * Registers a feedback provider.
     *
     * @param plugin the plugin that provides the feedback
     */
    public FeedbackResourceManager unregisterProvider(final WinterPlugin plugin) {
        final var namespace = plugin.namespace();
        providers.remove(namespace);
        return this;
    }

    /**
     * Gets the feedback with the specified key.
     *
     * @param key the key of the feedback
     * @return the feedback with the specified key, or the key as feedback chat message if not found
     */
    @NotNull
    public Feedback getFeedback(final String key) {
        try {
            for (final var provider : providers.values()) {
                final var feedback = provider.getFeedback(key);
                if (feedback != null) {
                    return feedback;
                }
            }
        } catch (final Exception e) {
            throw new RuntimeException("Failed to get feedback with key " + key, e);
        }

        return Feedback.plain(key);
    }

}
