package com.thewinterframework.configurate.feedback;

import com.thewinterframework.configurate.feedback.resource.FeedbackResourceManager;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * Represents a feedback type that can be sent to an {@link Audience}.
 */
@ConfigSerializable
public interface Feedback {

    /**
     * Sends the feedback to the specified {@link Audience}.
     *
     * @param audience the audience to send the feedback to
     * @param resolver the tag resolvers to use
     */
    void sendFeedback(Audience audience, TagResolver... resolver);

    /**
     * Creates a feedback that sends a component message to the audience.
     *
     * @param message the message to send
     * @return the created feedback
     */
    static Feedback plain(Component message) {
        return (audience, resolver) -> audience.sendMessage(message);
    }

    /**
     * Creates a feedback that sends a component message to the audience.
     *
     * @param message the message to send
     * @return the created feedback
     */
    static Feedback plain(String message) {
        return plain(Component.text(message));
    }

    /**
     * Gets a feedback from the specified path.
     *
     * @param path the path to get the feedback from
     * @return the feedback
     */
    static Feedback path(String path) {
        return FeedbackResourceManager.instance().getFeedback(path);
    }

}
