package io.quarkus.qlue._private;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

import io.quarkus.qlue.ChainBuildException;
import io.quarkus.qlue.ItemId;
import io.quarkus.qlue.StepId;

@MessageLogger(projectCode = "QLUE", length = 4)
public interface Messages extends BasicLogger {
    Messages log = Logger.getMessageLogger(Messages.class, "io.quarkus.qlue");

    @Message(id = 1, value = "The step is not currently running", format = Message.Format.NO_FORMAT)
    IllegalStateException stepNotRunning();

    @Message(id = 2, value = "Undeclared item %s")
    IllegalArgumentException undeclaredItem(Object itemId);

    @Message(id = 3, value = "Cannot provide or consume multiple values for %s")
    IllegalArgumentException cannotMulti(Object itemId);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 4, value = "Closing resource \"%s\" failed")
    void closeFailed(@Cause Exception e, Object obj);

    @Message(id = 5, value = "Item %s cannot be produced here (it is an initial resource): %s")
    ChainBuildException cannotProduceInitialResource(Object itemId, List<StepId> step);

    @Message(id = 6, value = "Multiple producers of item %s: %s")
    ChainBuildException multipleProducers(Object id, List<StepId> step);

    @Message(id = 7, value = "No producers for required item %s")
    ChainBuildException noProducers(Object id);

    @Message(id = 8, value = "Cannot construct empty items")
    UnsupportedOperationException emptyItem();

    @Message(id = 9, value = "Cannot produce an empty item")
    IllegalArgumentException emptyItemProduced();

    @Message(id = 10, value = "Cannot consume an empty item")
    IllegalArgumentException emptyItemConsumed();

    @Message(id = 11, value = "Item classes must be leaf (final) types: %s")
    IllegalArgumentException itemsMustBeLeafs(Class<?> clazz);

    @Message(id = 12, value = "A generic type is not allowed here; try creating a subclass with concrete type arguments instead: %s")
    IllegalArgumentException genericNotAllowed(Class<?> clazz);

    @Message(id = 13, value = "Build step %s must have exactly one public constructor")
    IllegalArgumentException mustHaveOneCtor(Class<?> clazz);

    @Message(id = 14, value = "Named items need a matching name argument: %s")
    IllegalArgumentException namedNeedsArgument(Class<?> clazz);

    @Message(id = 15, value = "Cannot inject into primitive type %s in %s")
    IllegalArgumentException cannotInjectPrimitive(String simpleName, Object member);

    @Message(id = 16, value = "Unnamed items must not have a name argument: %s")
    IllegalArgumentException unnamedMustNotHaveArgument(Class<?> clazz);

    @Message(id = 17, value = "No rule to produce objects of %s")
    IllegalArgumentException cannotProduce(Class<?> clazz);

    @Message(id = 18, value = "No rule to consume objects of %s")
    IllegalArgumentException cannotConsume(Class<?> clazz);

    @Message(id = 19, value = "Multiple eligible, accessible constructors are present on step %s")
    IllegalArgumentException multipleConstructors(Class<?> clazz);

    @Message(id = 20, value = "No eligible, accessible constructor present on step %s")
    IllegalArgumentException noConstructor(Class<?> clazz);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 21, value = "Failed to invoke constructor %s")
    void failedToInvokeConstructor(Constructor<?> ctor, @Cause Throwable t);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 22, value = "Failed to write to field %s")
    void failedToSetField(Field field, @Cause Throwable e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 23, value = "Failed to invoke method %s")
    void failedToInvokeMethod(Method method, @Cause Throwable e);

    @Message(id = 24, value = "Execution was not successful")
    UnsupportedOperationException didNotSucceed();

    @Message(id = 25, value = "Execution did not fail")
    UnsupportedOperationException didNotFail();

    @Message(id = 26, value = "@AlwaysProduce annotation cannot be added to non-producer on %s")
    IllegalArgumentException alwaysProduceNotProducer(AnnotatedElement element);

    @Message(id = 27, value = "Too many attachments present on target object")
    IllegalStateException tooManyAttachments();

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 28, value = "No public constructor available on %s")
    void nonPublicConstructor(Class<?> clazz);

    @Message(id = 29, value = "Cannot access field `%s` for writing from %s")
    IllegalArgumentException notAccessible(Field field, Class<?> fromClass, @Cause IllegalAccessException e);

    @Message(id = 30, value = "No item argument exists for %s")
    NoSuchElementException noItemArgument(ItemId itemId);

    @Message(id = 31, value = "Incorrect item type for %s (expected %s, but was actually %s)")
    ClassCastException wrongItemArgumentType(ItemId itemId, Class<?> expected, Class<?> actual);

    @Message(id = 32, value = "Step %s not yet started")
    IllegalStateException stepNotStarted(StepId id);

    @Message(id = 33, value = "Step %s not yet completed")
    IllegalStateException stepNotEnded(StepId id);

    @Message(id = 34, value = "No step found with identifier %s")
    NoSuchElementException noSuchStep(StepId stepId);

    // debug logs

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(value = "Execution of step \"%s\" threw an exception")
    void stepFailed(@Cause Throwable cause, Object step);

    // trace logs

    @LogMessage(level = Logger.Level.TRACE)
    @Message(value = "Starting step %s")
    void startingStep(Object step);

    @LogMessage(level = Logger.Level.TRACE)
    @Message(value = "Finished step %s in %s")
    void finishingStep(Object step, Duration duration);

    @LogMessage(level = Logger.Level.TRACE)
    @Message(value = "Skipped step %s due to dependency failure")
    void skippedStep(Object step);

    @LogMessage(level = Logger.Level.TRACE)
    @Message(value = "Step completed; %d remaining")
    void stepCompleted(int remaining);
}
