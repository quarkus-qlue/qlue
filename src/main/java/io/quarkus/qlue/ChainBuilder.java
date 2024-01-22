package io.quarkus.qlue;

import static io.quarkus.qlue._private.Messages.log;
import static java.lang.invoke.MethodHandles.Lookup;
import static java.lang.invoke.MethodHandles.lookup;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import io.quarkus.qlue.item.ClassItem;
import io.quarkus.qlue.item.InstanceItem;
import io.quarkus.qlue.item.Item;
import io.smallrye.common.constraint.Assert;

/**
 * A chain builder.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ChainBuilder {

    // StepBuilders are compared by identity deliberately
    final Set<StepBuilder> steps = Collections.newSetFromMap(new IdentityHashMap<>());
    final Set<ItemId> initialIds = new HashSet<>();
    final Set<ItemId> finalIds = new HashSet<>();
    InjectionMapper injectionMapper = InjectionMapper.BASIC;
    ClassLoader classLoader = ChainBuilder.class.getClassLoader();

    ChainBuilder() {
    }

    /**
     * Add a step to the chain. The configuration in the step builder at the time that the chain is built is
     * the configuration that will apply to the step in the final chain. Any subsequent changes will be ignored.
     * <p>
     * A given step is included in the chain when one or more of the following criteria are met:
     * <ul>
     * <li>It includes a produce step for an item which is consumed by a step that is included in the chain or is a final
     * item</li>
     * <li>It includes a consume step for an item which is produced by a step that is included in the chain or is an
     * initial item</li>
     * </ul>
     * In addition, the declaration of producers and consumers can cause corresponding consumers and producers to be
     * included if they exist.
     *
     * @param step the step instance
     * @return the builder for the step
     */
    public StepBuilder addRawStep(Consumer<StepContext> step) {
        return new StepBuilder(this, step);
    }

    /**
     * Declare an initial item that will be provided to steps in the chain. Note that if this method is called
     * for a simple item, no steps will be allowed to produce that item.
     *
     * @param type the item type (must not be {@code null})
     * @return this builder
     * @throws IllegalArgumentException if the item type is {@code null}
     */
    public ChainBuilder addInitial(Class<? extends Item> type) {
        Assert.checkNotNullParam("type", type);
        if (ClassItem.class.isAssignableFrom(type)) {
            throw log.namedNeedsArgument(type);
        }
        initialIds.add(new ItemId(type));
        return this;
    }

    /**
     * Declare an initial item that will be provided to steps in the chain. Note that if this method is called
     * for a simple item, no steps will be allowed to produce that item.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @return this builder
     * @throws IllegalArgumentException if the item type is {@code null}
     */
    public <U> ChainBuilder addInitial(Class<? extends ClassItem<U>> type, Class<? extends U> argument) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("argument", argument);
        initialIds.add(new ItemId(type, argument));
        return this;
    }

    /**
     * Declare a final item that will be consumable after the step chain completes. This may be any item
     * that is produced in the chain.
     *
     * @param type the item type (must not be {@code null})
     * @return this builder
     * @throws IllegalArgumentException if the item type is {@code null}
     */
    public ChainBuilder addFinal(Class<? extends Item> type) {
        Assert.checkNotNullParam("type", type);
        if (ClassItem.class.isAssignableFrom(type)) {
            throw log.namedNeedsArgument(type);
        }
        finalIds.add(new ItemId(type));
        return this;
    }

    /**
     * Declare a final item that will be consumable after the step chain completes. This may be any item
     * that is produced in the chain.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @return this builder
     * @throws IllegalArgumentException if the item type is {@code null}
     */
    public <U> ChainBuilder addFinal(Class<? extends ClassItem<U>> type, Class<? extends U> argument) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("argument", argument);
        finalIds.add(new ItemId(type, argument));
        return this;
    }

    /**
     * Set the injection mapper to use for subsequent step class and object handling.
     *
     * @param injectionMapper the injection mapper to use (must not be {@code null})
     * @return this builder
     */
    public ChainBuilder setInjectionMapper(final InjectionMapper injectionMapper) {
        this.injectionMapper = Assert.checkNotNullParam("injectionMapper", injectionMapper);
        return this;
    }

    /**
     * Add all of the steps defined in the given object's class. The given object instance is used as-is.
     * Each recognized step method is added as a step which invokes the method, producing any results that are produced
     * by the method.
     * Step methods must be {@code public} or accessible from this module.
     *
     * @param obj the step object to add (must not be {@code null})
     * @return this builder
     */
    public ChainBuilder addStepObject(Object obj) {
        return addStepObject(obj, lookup());
    }

    /**
     * Add all of the steps defined in the given object's class. The given object instance is used as-is.
     * Each recognized step method is added as a step which invokes the method, producing any results that are produced
     * by the method.
     * Step methods must be accessible according to the given {@link Lookup}.
     *
     * @param obj the step object to add (must not be {@code null})
     * @param lookup a {@link Lookup} whose access privileges should be used (must not be {@code null})
     * @return this builder
     */
    public ChainBuilder addStepObject(Object obj, Lookup lookup) {
        Assert.checkNotNullParam("obj", obj);
        Class<?> clazz = obj.getClass();
        // now create steps for each step method
        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                // skip static methods
                continue;
            }
            if (injectionMapper.isStepMethod(method, lookup)) {
                final MethodHandle methodHandle;
                try {
                    methodHandle = lookup.unreflect(method);
                } catch (IllegalAccessException e) {
                    throw new IllegalArgumentException("Cannot access step method", e);
                }
                SwitchableConsumer<StepContext> cons = new SwitchableConsumer<>(method.toString());
                StepBuilder stepBuilder = addRawStep(cons);
                stepBuilder.id(new MethodStepId(new InstanceStepId(obj), method));
                Consumer<StepContext> methodHandler = injectionMapper.handleStepMethod(stepBuilder, method, lookup);
                int cnt = method.getParameterCount();
                List<Function<StepContext, Object>> methodParamVals = new ArrayList<>(cnt);
                for (int i = 0; i < cnt; i++) {
                    methodParamVals.add(injectionMapper.handleParameter(stepBuilder, method, i, lookup));
                }
                BiConsumer<StepContext, Object> retHandler = injectionMapper.handleReturnValue(stepBuilder, method, lookup);
                cons.setDelegate(new Consumer<StepContext>() {
                    public void accept(final StepContext stepContext) {
                        methodHandler.accept(stepContext);
                        final int cnt = methodParamVals.size();
                        final Object[] args = new Object[cnt + 1];
                        args[0] = obj;
                        for (int i = 0; i < cnt; i++) {
                            args[i + 1] = methodParamVals.get(i).apply(stepContext);
                        }
                        Object retVal;
                        try {
                            retVal = methodHandle.invokeWithArguments(Arrays.asList(args));
                        } catch (Throwable t) {
                            log.failedToInvokeMethod(method, t);
                            stepContext.addProblem(t);
                            return;
                        }
                        retHandler.accept(stepContext, retVal);
                    }
                });
                stepBuilder.build();
            }
        }
        // done
        return this;
    }

    /**
     * Add all of the steps defined in the given class. The construction of the class itself is
     * added as a step which consumes the injected dependencies of the class and produces the
     * corresponding {@link InstanceItem}. Each recognized step method is added as a step
     * which consumes the {@code StepClassItem} and the injected dependencies of the method, and subsequently invokes
     * the method, producing any results that are produced by the method.
     *
     * @param clazz the step class to add (must not be {@code null})
     * @param <T> the step class type
     * @return this builder
     */
    public <T> ChainBuilder addStepClass(Class<T> clazz) {
        return addStepClass(clazz, lookup());
    }

    /**
     * Add all of the steps defined in the given class. The construction of the class itself is
     * added as a step which consumes the injected dependencies of the class and produces the
     * corresponding {@link InstanceItem}. Each recognized step method is added as a step
     * which consumes the {@code StepClassItem} and the injected dependencies of the method, and subsequently invokes
     * the method, producing any results that are produced by the method.
     *
     * @param clazz the step class to add (must not be {@code null})
     * @param lookup the {@link Lookup} to use (must not be {@code null})
     * @param <T> the step class type
     * @return this builder
     */
    public <T> ChainBuilder addStepClass(Class<T> clazz, Lookup lookup) {
        Assert.checkNotNullParam("clazz", clazz);
        SwitchableConsumer<StepContext> cons = new SwitchableConsumer<>(clazz.toString());
        // this is the step builder for the class producer
        StepBuilder classStepBuilder = addRawStep(cons);
        classStepBuilder.produces(InstanceItem.class, clazz);
        // get the overall class handler
        Consumer<StepContext> classHandler = injectionMapper.handleClass(classStepBuilder, clazz, lookup);
        MethodHandle mh = null;
        Constructor<?> ctor = null;
        // find a constructor
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            MethodHandle tmp;
            try {
                ctor = constructor;
                tmp = lookup.unreflectConstructor(constructor);
            } catch (IllegalAccessException e) {
                continue;
            }
            if (mh != null) {
                throw log.multipleConstructors(clazz);
            }
            classStepBuilder.id(new MethodStepId(constructor));
            mh = tmp;
        }
        // no eligible constructor found
        if (mh == null) {
            throw log.noConstructor(clazz);
        }
        final MethodHandle finalMh = mh;
        // process each parameter
        int cnt = mh.type().parameterCount();
        List<Function<StepContext, Object>> ctorParamVals = new ArrayList<>(cnt);
        for (int i = 0; i < cnt; i++) {
            ctorParamVals.add(injectionMapper.handleParameter(classStepBuilder, ctor, i, lookup));
        }
        // check out each field
        Field[] declaredFields = clazz.getDeclaredFields();
        List<BiConsumer<Object, StepContext>> fieldInjections = Stream.of(declaredFields)
                .filter(field -> {
                    int mods = field.getModifiers();
                    return !Modifier.isFinal(mods) && !Modifier.isStatic(mods);
                })
                .map(field -> {
                    MethodHandle setter;
                    try {
                        setter = lookup.unreflectSetter(field);
                    } catch (IllegalAccessException e) {
                        throw log.notAccessible(field, lookup.lookupClass(), e);
                    }
                    Function<StepContext, Object> fn = injectionMapper.handleField(classStepBuilder, field, lookup);
                    return (BiConsumer<Object, StepContext>) (instance, ctxt) -> {
                        try {
                            setter.invoke(instance, fn.apply(ctxt));
                        } catch (Throwable e) {
                            log.failedToSetField(field, e);
                            ctxt.addProblem(e);
                            return;
                        }
                    };
                })
                .toList();
        Consumer<StepContext> classFinish = injectionMapper.handleClassFinish(classStepBuilder, clazz, lookup);
        // now create the real class build step
        final Constructor<?> finalCtor = ctor;
        cons.setDelegate(new Consumer<StepContext>() {
            public void accept(final StepContext stepContext) {
                classHandler.accept(stepContext);
                // construct
                final int cnt = ctorParamVals.size();
                final Object[] args = new Object[cnt];
                for (int i = 0; i < cnt; i++) {
                    args[i] = ctorParamVals.get(i).apply(stepContext);
                }
                final Object instance;
                try {
                    instance = finalMh.invokeWithArguments(args);
                } catch (Throwable t) {
                    log.failedToInvokeConstructor(finalCtor, t);
                    stepContext.addProblem(t);
                    return;
                }
                // inject fields
                for (BiConsumer<Object, StepContext> injection : fieldInjections) {
                    injection.accept(instance, stepContext);
                }
                classFinish.accept(stepContext);
                // and we're set
                stepContext.produce(clazz, new InstanceItem(instance));
            }
        });
        classStepBuilder.build();
        // now create steps for each step method
        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                // skip static methods
                continue;
            }
            if (injectionMapper.isStepMethod(method, lookup)) {
                method.setAccessible(true);
                cons = new SwitchableConsumer<>(method.toString());
                StepBuilder stepBuilder = addRawStep(cons);
                stepBuilder.id(new MethodStepId(method));
                stepBuilder.consumes(InstanceItem.class, clazz);
                Consumer<StepContext> methodHandler = injectionMapper.handleStepMethod(stepBuilder, method, lookup);
                cnt = method.getParameterCount();
                List<Function<StepContext, Object>> methodParamVals = new ArrayList<>(cnt);
                for (int i = 0; i < cnt; i++) {
                    methodParamVals.add(injectionMapper.handleParameter(stepBuilder, method, i, lookup));
                }
                BiConsumer<StepContext, Object> retHandler = injectionMapper.handleReturnValue(stepBuilder, method, lookup);
                cons.setDelegate(new Consumer<StepContext>() {
                    public void accept(final StepContext stepContext) {
                        InstanceItem item = stepContext.consume(InstanceItem.class, clazz);
                        Object instance = item.instance();
                        methodHandler.accept(stepContext);
                        final int cnt = methodParamVals.size();
                        final Object[] args = new Object[cnt];
                        for (int i = 0; i < cnt; i++) {
                            args[i] = methodParamVals.get(i).apply(stepContext);
                        }
                        Object retVal;
                        try {
                            retVal = method.invoke(instance, args);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            log.failedToInvokeMethod(method, e);
                            stepContext.addProblem(e);
                            return;
                        }
                        retHandler.accept(stepContext, retVal);
                    }
                });
                stepBuilder.build();
            }
        }
        // done
        return this;
    }

    /**
     * Sets the ClassLoader for the execution. Every step will be run with this as the TCCL.
     *
     * @param classLoader The ClassLoader
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Build the step chain from the current builder configuration.
     *
     * @return the constructed chain
     * @throws ChainBuildException if the chain could not be built
     */
    public Chain build() throws ChainBuildException {
        return new Chain(this);
    }

    void addStep(final StepBuilder stepBuilder) {
        steps.add(stepBuilder);
    }
}
