package com.thewinterframework.configurate.feedback.resource.provider;

import com.thewinterframework.configurate.feedback.Feedback;

import javax.annotation.Nullable;

/**
 * Represents a provider for {@link Feedback}.
 */
public interface FeedbackProvider {

    /**
     * Gets the feedback with the specified key.
     *
     * @param key the key of the feedback
     * @return the feedback with the specified key, or {@code null} if not found
     */
    @Nullable
    Feedback getFeedback(String key) throws Exception;

}
