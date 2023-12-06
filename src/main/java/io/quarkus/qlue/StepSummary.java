package io.quarkus.qlue;

import java.time.Duration;
import java.time.Instant;

import io.smallrye.common.constraint.Assert;

/**
 * The summary of the execution of a single build step.
 */
public final class StepSummary {
    private final StepId stepId;
    private final StepContext.State state;
    private final Instant start;
    private final Instant end;
    private Duration duration;

    /**
     * Construct a new instance.
     *
     * @param stepId the step identifier (must not be {@code null})
     * @param state  the final state (must not be {@code null})
     * @param start  the start time of the step execution (must not be {@code null})
     * @param end    the start time of the step execution (must not be {@code null})
     */
    public StepSummary(final StepId stepId, final StepContext.State state, final Instant start, final Instant end) {
        Assert.checkNotNullParam("stepId", stepId);
        Assert.checkNotNullParam("state", state);
        Assert.checkNotNullParam("start", start);
        Assert.checkNotNullParam("end", end);
        this.stepId = stepId;
        this.state = state;
        this.start = start;
        this.end = end;
    }

    public StepId stepId() {
        return stepId;
    }

    public StepContext.State state() {
        return state;
    }

    public Instant start() {
        return start;
    }

    public Instant end() {
        return end;
    }

    public Duration duration() {
        Duration duration = this.duration;
        if (duration == null) {
            duration = this.duration = max(Duration.between(start, end), Duration.ZERO);
        }
        return duration;
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) > 0 ? a : b;
    }

    @Override
    public String toString() {
        return "StepSummary[" +
            "stepId=" + stepId + ", " +
            "state=" + state + ", " +
            "start=" + start + ", " +
            "end=" + end + ']';
    }
}
