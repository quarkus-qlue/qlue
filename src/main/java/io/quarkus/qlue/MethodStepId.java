package io.quarkus.qlue;

import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Objects;

import io.smallrye.common.constraint.Assert;

/**
 * A step identifier which refers to a specific method on a class.
 */
public final class MethodStepId extends StepId {
    private final Class<?> owner;
    private final String methodName;
    private final MethodType methodType;

    /**
     * Construct a new instance.
     *
     * @param parent the parent identifier, or {@code null} for no parent
     * @param owner the owning class (must not be {@code null})
     * @param methodName the method name (must not be {@code null})
     * @param methodType the method type (must not be {@code null})
     */
    public MethodStepId(final StepId parent, final Class<?> owner, final String methodName, final MethodType methodType) {
        super(parent, Objects.hash(owner, methodName, methodType));
        this.owner = Assert.checkNotNullParam("owner", owner);
        this.methodName = Assert.checkNotNullParam("methodName", methodName);
        this.methodType = Assert.checkNotNullParam("methodType", methodType);
    }

    /**
     * Construct a new instance.
     *
     * @param owner the owning class (must not be {@code null})
     * @param methodName the method name (must not be {@code null})
     * @param methodType the method type (must not be {@code null})
     */
    public MethodStepId(final Class<?> owner, final String methodName, final MethodType methodType) {
        this(null, owner, methodName, methodType);
    }

    /**
     * Construct a new instance.
     *
     * @param parent the parent identifier, or {@code null} for no parent
     * @param method the reflection method corresponding to the identifier (must not be {@code null})
     */
    public MethodStepId(final StepId parent, final Method method) {
        this(parent, method.getDeclaringClass(), method.getName(),
                MethodType.methodType(method.getReturnType(), method.getParameterTypes()));
    }

    /**
     * Construct a new instance.
     *
     * @param method the reflection method corresponding to the identifier (must not be {@code null})
     */
    public MethodStepId(final Method method) {
        this(null, method);
    }

    /**
     * Construct a new instance.
     *
     * @param parent the parent identifier, or {@code null} for no parent
     * @param method the reflection method corresponding to the identifier (must not be {@code null})
     */
    public MethodStepId(final StepId parent, final Constructor<?> method) {
        this(parent, method.getDeclaringClass(), "<init>",
                MethodType.methodType(void.class, method.getParameterTypes()));
    }

    /**
     * Construct a new instance.
     *
     * @param method the reflection method corresponding to the identifier (must not be {@code null})
     */
    public MethodStepId(final Constructor<?> method) {
        this(null, method);
    }

    /**
     * Construct a new instance from a stack frame.
     *
     * @param parent the parent identifier, or {@code null} for no parent
     * @param frame the stack frame (must not be {@code null})
     */
    public MethodStepId(final StepId parent, StackWalker.StackFrame frame) {
        this(parent, frame.getDeclaringClass(), frame.getMethodName(), frame.getMethodType());
    }

    /**
     * Construct a new instance from a stack frame.
     *
     * @param frame the stack frame (must not be {@code null})
     */
    public MethodStepId(StackWalker.StackFrame frame) {
        this(null, frame);
    }

    /**
     * {@return the owning class}
     */
    public Class<?> owner() {
        return owner;
    }

    /**
     * {@return the method name}
     */
    public String methodName() {
        return methodName;
    }

    /**
     * {@return the method type}
     */
    public MethodType methodType() {
        return methodType;
    }

    public boolean equals(final StepId other) {
        return other instanceof MethodStepId si && equals(si);
    }

    public boolean equals(final MethodStepId other) {
        return this == other || super.equals(other) && owner == other.owner && methodName.equals(other.methodName)
                && methodType.equals(other.methodType);
    }

    public StringBuilder toString(final StringBuilder sb) {
        return prependParent(sb).append(owner.getName()).append('#').append(methodName).append(methodType);
    }
}
