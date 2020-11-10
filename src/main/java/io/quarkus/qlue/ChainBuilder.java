package io.quarkus.qlue;

import static io.quarkus.qlue._private.Messages.log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import io.quarkus.qlue.item.ClassItem;
import io.quarkus.qlue.item.Item;
import io.quarkus.qlue.item.StepClassItem;
import io.smallrye.common.constraint.Assert;

/**
 * A chain builder.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ChainBuilder {

    final Set<StepBuilder> steps = new HashSet<>();
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
     *
     * @param obj the step object to add (must not be {@code null})
     * @return this builder
     */
    public ChainBuilder addStepObject(Object obj) {
        Assert.checkNotNullParam("obj", obj);
        Class<?> clazz = obj.getClass();
        // now create steps for each step method
        for (Method method : clazz.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                // skip static methods
                continue;
            }
            if (injectionMapper.isStepMethod(method)) {
                SwitchableConsumer<StepContext> cons = new SwitchableConsumer<>(method.toString());
                StepBuilder stepBuilder = addRawStep(cons);
                Consumer<StepContext> methodHandler = injectionMapper.handleStepMethod(stepBuilder, method);
                int cnt = method.getParameterCount();
                List<Function<StepContext, Object>> methodParamVals = new ArrayList<>(cnt);
                for (int i = 0; i < cnt; i++) {
                    methodParamVals.add(injectionMapper.handleParameter(stepBuilder, method, i));
                }
                BiConsumer<StepContext, Object> retHandler = injectionMapper.handleReturnValue(stepBuilder, method);
                cons.setDelegate(new Consumer<StepContext>() {
                    public void accept(final StepContext stepContext) {
                        methodHandler.accept(stepContext);
                        final int cnt = methodParamVals.size();
                        final Object[] args = new Object[cnt];
                        for (int i = 0; i < cnt; i++) {
                            args[i] = methodParamVals.get(i).apply(stepContext);
                        }
                        Object retVal;
                        try {
                            retVal = method.invoke(obj, args);
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
     * Add all of the steps defined in the given class. The construction of the class itself is
     * added as a step which consumes the injected dependencies of the class and produces the
     * corresponding {@link StepClassItem}. Each recognized step method is added as a step
     * which consumes the {@code StepClassItem} and the injected dependencies of the method, and subsequently invokes
     * the method, producing any results that are produced by the method.
     *
     * @param clazz the step class to add (must not be {@code null})
     * @param <T> the step class type
     * @return this builder
     */
    public <T> ChainBuilder addStepClass(Class<T> clazz) {
        Assert.checkNotNullParam("clazz", clazz);
        SwitchableConsumer<StepContext> cons = new SwitchableConsumer<>(clazz.toString());
        // this is the step builder for the class producer
        StepBuilder classStepBuilder = addRawStep(cons);
        classStepBuilder.produces(StepClassItem.class, clazz);
        // get the overall class handler
        Consumer<StepContext> classHandler = injectionMapper.handleClass(classStepBuilder, clazz);
        // find a public constructor
        Constructor<?> ctor = null;
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (Modifier.isPublic(constructor.getModifiers())) {
                if (ctor != null) {
                    throw log.multipleConstructors(clazz);
                }
                ctor = constructor;
            }
        }
        // OK, find a non-private constructor and warn
        if (ctor == null) {
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                if (!Modifier.isPrivate(constructor.getModifiers())) {
                    if (ctor != null) {
                        throw log.multipleConstructors(clazz);
                    }
                    ctor = constructor;
                    log.nonPublicConstructor(clazz);
                    ctor.setAccessible(true);
                }
            }
        }
        // no eligible constructor found
        if (ctor == null) {
            throw log.noConstructor(clazz);
        }
        final Constructor<?> finalCtor = ctor;
        // process each parameter
        int cnt = ctor.getParameterCount();
        List<Function<StepContext, Object>> ctorParamVals = new ArrayList<>(cnt);
        for (int i = 0; i < cnt; i++) {
            ctorParamVals.add(injectionMapper.handleParameter(classStepBuilder, ctor, i));
        }
        // check out each field
        Map<Field, Function<StepContext, Object>> fieldVals = new HashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isFinal(mods) || Modifier.isStatic(mods)) {
                // skip it
                continue;
            }
            fieldVals.put(field, injectionMapper.handleField(classStepBuilder, field));
        }
        // now create the real class build step
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
                    instance = finalCtor.newInstance(args);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    log.failedToInvokeConstructor(finalCtor, e);
                    stepContext.addProblem(e);
                    return;
                }
                // inject fields
                for (Map.Entry<Field, Function<StepContext, Object>> entry : fieldVals.entrySet()) {
                    Field field = entry.getKey();
                    Function<StepContext, Object> fn = entry.getValue();
                    try {
                        field.set(instance, fn.apply(stepContext));
                    } catch (IllegalAccessException e) {
                        log.failedToSetField(field, e);
                        stepContext.addProblem(e);
                        return;
                    }
                }
                // and we're set
                stepContext.produce(clazz, new StepClassItem(instance));
            }
        });
        classStepBuilder.build();
        // now create steps for each step method
        for (Method method : clazz.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                // skip static methods
                continue;
            }
            if (injectionMapper.isStepMethod(method)) {
                cons = new SwitchableConsumer<>(method.toString());
                StepBuilder stepBuilder = addRawStep(cons);
                stepBuilder.consumes(StepClassItem.class, clazz);
                Consumer<StepContext> methodHandler = injectionMapper.handleStepMethod(stepBuilder, method);
                cnt = method.getParameterCount();
                List<Function<StepContext, Object>> methodParamVals = new ArrayList<>(cnt);
                for (int i = 0; i < cnt; i++) {
                    methodParamVals.add(injectionMapper.handleParameter(stepBuilder, method, i));
                }
                BiConsumer<StepContext, Object> retHandler = injectionMapper.handleReturnValue(stepBuilder, method);
                cons.setDelegate(new Consumer<StepContext>() {
                    public void accept(final StepContext stepContext) {
                        StepClassItem item = stepContext.consume(StepClassItem.class, clazz);
                        Object instance = item.getInstance();
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
                            stepContext.markAsFailed();
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
