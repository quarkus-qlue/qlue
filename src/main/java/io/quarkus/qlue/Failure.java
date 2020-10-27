package io.quarkus.qlue;

import java.util.List;

/**
 *
 */
public class Failure extends Result {
    private final List<Throwable> problems;

    Failure(final List<Throwable> problems) {
        this.problems = problems;
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
