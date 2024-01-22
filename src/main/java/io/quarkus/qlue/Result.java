package io.quarkus.qlue;

import static io.quarkus.qlue._private.Messages.log;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import io.smallrye.common.constraint.Assert;

/**
 * The result of the execution.
 */
public abstract class Result {
    private final Instant start;
    private final Instant end;
    private final Map<StepId, StepSummary> summaries;
    private Duration duration;

    Result(final Instant start, final Instant end, final Map<StepId, StepSummary> summaries) {
        this.start = start;
        this.end = end;
        this.summaries = Map.copyOf(summaries);
    }

    /**
     * {@return the start time of the execution}
     */
    public Instant start() {
        return start;
    }

    /**
     * {@return the end time of the execution}
     */
    public Instant end() {
        return end;
    }

    /**
     * {@return the amount of elapsed time from the time the operation was initiated to the time it was completed}
     */
    public Duration duration() {
        Duration duration = this.duration;
        if (duration == null) {
            duration = this.duration = Duration.between(start, end);
        }
        return duration;
    }

    public boolean isSuccess() {
        return false;
    }

    public Success asSuccess() {
        throw log.didNotSucceed();
    }

    public boolean isFailure() {
        return false;
    }

    public Failure asFailure() {
        throw log.didNotFail();
    }

    /**
     * {@return the set of steps which were executed in this execution}
     */
    public Set<StepId> executedSteps() {
        return summaries.keySet();
    }

    /**
     * {@return the step summary for the given step ID}
     *
     * @param stepId the step ID (must not be {@code null})
     */
    public StepSummary stepSummary(StepId stepId) {
        StepSummary summary = summaries.get(Assert.checkNotNullParam("stepId", stepId));
        if (summary == null) {
            throw log.noSuchStep(stepId);
        }
        return summary;
    }
}
