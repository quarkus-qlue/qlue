package io.quarkus.qlue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A failure result.
 */
public class Failure extends Result {
    private final List<Throwable> problems;

    Failure(final Instant start, final Instant end, final List<Throwable> problems, final Map<StepId, StepSummary> summaries) {
        super(start, end, summaries);
        this.problems = List.copyOf(problems);
    }

    public List<Throwable> getProblems() {
        return problems;
    }

    public boolean isFailure() {
        return true;
    }

    public Failure asFailure() {
        return this;
    }
}
