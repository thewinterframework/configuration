package com.thewinterframework.configurate.feedback;

import com.thewinterframework.component.ComponentJoiner;
import com.thewinterframework.component.ComponentUtils;
import com.thewinterframework.configurate.feedback.media.impl.ChatMedia;
import com.thewinterframework.configurate.feedback.resource.FeedbackResourceManager;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.Arrays;
import java.util.List;

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
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2.0.0")
    default void sendFeedback(Audience audience, TagResolver... resolver) {
        send(audience, resolver);
    }

    /**
     * Sends the feedback to the specified {@link Audience}.
     *
     * @param audience the audience to send the feedback to
     * @param resolver the tag resolvers to use
     */
    void send(Audience audience, TagResolver... resolver);

    /**
     * Converts the feedback to a component.
     *
     * @param resolver the tag resolvers to use
     * @return the component representation of the feedback
     * @throws IllegalStateException if the feedback cannot be converted to a component
     */
    default @NotNull Component asComponent(TagResolver... resolver) throws IllegalStateException {
        return ComponentJoiner.newLine(this.asComponents(resolver));
    }

    /**
     * Converts the feedback to a list of components.
     *
     * @param resolver the tag resolvers to use
     * @return the list of component representation of the feedback
     * @throws IllegalStateException if the feedback cannot be converted to components
     */
    @NotNull List<Component> asComponents(TagResolver... resolver) throws IllegalStateException;

    /**
     * Creates a feedback that sends a component message to the audience.
     *
     * @param message the message to send
     * @return the created feedback
     */
    static Feedback plain(Component message) {
        return new Feedback() {

            @Override
            public void send(Audience audience, TagResolver... resolver) {
                audience.sendMessage(message);
            }

            @Override
            public @NotNull List<Component> asComponents(TagResolver... resolver) throws IllegalStateException {
                return List.of(message);
            }
        };
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
