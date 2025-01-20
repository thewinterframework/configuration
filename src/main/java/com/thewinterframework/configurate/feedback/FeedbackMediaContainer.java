package com.thewinterframework.configurate.feedback;

import com.thewinterframework.configurate.feedback.media.FeedbackMedia;

import java.util.List;

/**
 * Represents a container for {@link FeedbackMedia}.
 */
public interface FeedbackMediaContainer {

    List<FeedbackMedia> medias();

}
