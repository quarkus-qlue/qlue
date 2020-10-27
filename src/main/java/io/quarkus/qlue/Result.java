package io.quarkus.qlue;

import static io.quarkus.qlue._private.Messages.log;

/**
 * The result of the execution.
 */
public abstract class Result {
    Result() {
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
}
