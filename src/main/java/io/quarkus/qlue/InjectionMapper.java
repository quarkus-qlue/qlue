package io.quarkus.qlue;

import static io.quarkus.qlue.ReflectUtil.rawTypeOf;
import static io.quarkus.qlue.ReflectUtil.rawTypeOfParameter;
import static io.quarkus.qlue.ReflectUtil.typeOfParameter;
import static io.quarkus.qlue._private.Messages.log;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import io.quarkus.qlue.annotation.AfterProduce;
import io.quarkus.qlue.annotation.AlwaysProduce;
import io.quarkus.qlue.annotation.BeforeConsume;
import io.quarkus.qlue.annotation.BeforeConsumeWeak;
import io.quarkus.qlue.annotation.ForClass;
import io.quarkus.qlue.annotation.None;
import io.quarkus.qlue.annotation.Overridable;
import io.quarkus.qlue.annotation.Step;
import io.quarkus.qlue.annotation.Weak;
import io.quarkus.qlue.item.ClassItem;
import io.quarkus.qlue.item.EmptyClassItem;
import io.quarkus.qlue.item.EmptyItem;
import io.quarkus.qlue.item.Item;
import io.quarkus.qlue.item.MultiClassItem;
import io.quarkus.qlue.item.MultiItem;
import io.quarkus.qlue.item.SimpleClassItem;
import io.quarkus.qlue.item.SimpleItem;

/**
 * A mapper which provides injection behavior for a step class.
 */
public interface InjectionMapper {
    /**
     * Perform class-wide setup for a step class, returning a consumer that accepts the step context.
     *
     * @param stepBuilder the step builder (not {@code null})
     * @param clazz the class (not {@code null})
     * @return the consumer (must not be {@code null})
     * @throws IllegalArgumentException if the class is not acceptable
     */
    Consumer<StepContext> handleClass(StepBuilder stepBuilder, Class<?> clazz) throws IllegalArgumentException;

    /**
     * Perform method-wide setup for a step method, returning a consumer that handles the step context.
     *
     * @param stepBuilder the step builder (not {@code null})
     * @param method the method (not {@code null})
     * @return the consumer (must not be {@code null})
     * @throws IllegalArgumentException if the method is not acceptable
     */
    Consumer<StepContext> handleStepMethod(StepBuilder stepBuilder, Method method) throws IllegalArgumentException;

    /**
     * Process a single parameter, returning a function that maps the step context to the actual parameter value.
     *
     * @param stepBuilder the step builder (not {@code null})
     * @param executable the method or constructor (not {@code null})
     * @param paramIndex the parameter index
     * @return the mapping function (must not be {@code null})
     * @throws IllegalArgumentException if the parameter is not acceptable
     */
    Function<StepContext, Object> handleParameter(StepBuilder stepBuilder, Executable executable, int paramIndex)
            throws IllegalArgumentException;

    /**
     * Process the return value of a method if it is not {@code void}, returning a consumer that accepts the returned
     * value and the step context.
     *
     * @param stepBuilder the step builder (not {@code null})
     * @param method the method (not {@code null})
     * @return the consumer (must not be {@code null})
     * @throws IllegalArgumentException if the return type is not acceptable
     */
    BiConsumer<StepContext, Object> handleReturnValue(StepBuilder stepBuilder, Method method) throws IllegalArgumentException;

    /**
     * Process a single non-{@code field} field, returning a function that maps the step context to the actual injected
     * field value.
     *
     * @param stepBuilder the step builder (not {@code null})
     * @param field the field (not {@code null})
     * @return the mapping function (must not be {@code null})
     * @throws IllegalArgumentException if the field is not acceptable
     */
    Function<StepContext, Object> handleField(StepBuilder stepBuilder, Field field) throws IllegalArgumentException;

    /**
     * Determine whether a method is a step method. Methods which are not step methods are skipped over.
     *
     * @param method the method
     * @return {@code true} if the method is a step method, or {@code false} otherwise
     */
    boolean isStepMethod(Method method);

    /**
     * An injection mapper that uses the standard annotation set in {@link io.quarkus.qlue.annotation} to identify
     * and wire injections. Method parameters may be item types, {@code Optional} or {@code List} of item types,
     * {@code Consumer} of item types, or {@link StepContext}. Fields may be item types or {@code Optional} or {@code List}
     * of item types.
     */
    InjectionMapper BASIC = new InjectionMapper() {
        public Consumer<StepContext> handleClass(final StepBuilder stepBuilder, final Class<?> clazz)
                throws IllegalArgumentException {
            return sc -> {
            };
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public Consumer<StepContext> handleStepMethod(final StepBuilder stepBuilder, final Method method)
                throws IllegalArgumentException {
            BeforeConsume[] beforeConsumeAnnotations = method.getAnnotationsByType(BeforeConsume.class);
            for (BeforeConsume ann : beforeConsumeAnnotations) {
                Class<? extends Item> itemType = ann.value();
                Class<?> classArg = ann.forClass();
                if (EmptyClassItem.class.isAssignableFrom(itemType)) {
                    if (classArg == None.class) {
                        throw log.namedNeedsArgument(itemType);
                    }
                    stepBuilder.beforeConsume((Class) itemType, (Class) classArg);
                } else if (EmptyItem.class.isAssignableFrom(itemType)) {
                    if (classArg != None.class) {
                        throw log.unnamedMustNotHaveArgument(itemType);
                    }
                    stepBuilder.beforeConsume(itemType);
                } else {
                    throw log.cannotProduce(classArg);
                }
            }
            BeforeConsumeWeak[] beforeConsumeWeakAnnotations = method.getAnnotationsByType(BeforeConsumeWeak.class);
            for (BeforeConsumeWeak ann : beforeConsumeWeakAnnotations) {
                Class<? extends Item> itemType = ann.value();
                Class<?> classArg = ann.forClass();
                if (EmptyClassItem.class.isAssignableFrom(itemType)) {
                    if (classArg == None.class) {
                        throw log.namedNeedsArgument(itemType);
                    }
                    stepBuilder.beforeConsume((Class) itemType, (Class) classArg, ProduceFlag.WEAK);
                } else if (EmptyItem.class.isAssignableFrom(itemType)) {
                    if (classArg != None.class) {
                        throw log.unnamedMustNotHaveArgument(itemType);
                    }
                    stepBuilder.beforeConsume(itemType, ProduceFlag.WEAK);
                } else {
                    throw log.cannotProduce(classArg);
                }
            }
            AfterProduce[] afterProduceAnnotations = method.getAnnotationsByType(AfterProduce.class);
            for (AfterProduce ann : afterProduceAnnotations) {
                Class<? extends Item> itemType = ann.value();
                Class<?> classArg = ann.forClass();
                if (ClassItem.class.isAssignableFrom(itemType)) {
                    if (classArg == None.class) {
                        throw log.namedNeedsArgument(itemType);
                    }
                    stepBuilder.afterProduce((Class) itemType, (Class) classArg);
                } else if (EmptyItem.class.isAssignableFrom(itemType)) {
                    if (classArg != None.class) {
                        throw log.unnamedMustNotHaveArgument(itemType);
                    }
                    stepBuilder.afterProduce(itemType);
                } else {
                    throw log.cannotConsume(classArg);
                }
            }
            return sc -> {
            };
        }

        public Function<StepContext, Object> handleParameter(final StepBuilder stepBuilder, final Executable executable,
                final int paramIndex) throws IllegalArgumentException {
            Parameter parameter = executable.getParameters()[paramIndex];
            Type type = parameter.getParameterizedType();
            return executable instanceof Method ? handleInput(stepBuilder, type, parameter)
                    : handleNonConsumerInput(stepBuilder, type, parameter);
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public BiConsumer<StepContext, Object> handleReturnValue(final StepBuilder stepBuilder, final Method method)
                throws IllegalArgumentException {
            Type type = method.getGenericReturnType();
            Class<?> clazz = rawTypeOf(type);
            if (clazz == void.class) {
                if (method.getAnnotation(AlwaysProduce.class) != null) {
                    throw log.alwaysProduceNotProducer(method);
                }
                return (sc, o) -> {
                };
            }
            ForClass forClassAnnotation = method.getAnnotation(ForClass.class);
            Class<?> classArg = forClassAnnotation == null ? null : forClassAnnotation.value();
            if (Item.class.isAssignableFrom(clazz)) {
                boolean always = method.getAnnotation(AlwaysProduce.class) != null;
                ProduceFlags flags = ProduceFlags.NONE;
                if (method.getAnnotation(Weak.class) != null) {
                    flags = flags.with(ProduceFlag.WEAK);
                }
                if (method.getAnnotation(Overridable.class) != null) {
                    flags = flags.with(ProduceFlag.OVERRIDABLE);
                }
                if (SimpleItem.class.isAssignableFrom(clazz) || MultiItem.class.isAssignableFrom(clazz)) {
                    if (classArg != null) {
                        throw log.unnamedMustNotHaveArgument(clazz);
                    }
                    Class<? extends Item> itemType = clazz.asSubclass(Item.class);
                    stepBuilder.produces(itemType, flags);
                    if (always) {
                        stepBuilder.getChainBuilder().addFinal(itemType);
                    }
                    return (sc, o) -> sc.produce((Class) itemType, itemType.cast(o));
                } else if (SimpleClassItem.class.isAssignableFrom(clazz) || MultiClassItem.class.isAssignableFrom(clazz)) {
                    if (classArg == null) {
                        throw log.namedNeedsArgument(clazz);
                    }
                    Class<? extends ClassItem<?>> itemType = (Class<? extends ClassItem<?>>) clazz.asSubclass(ClassItem.class);
                    stepBuilder.produces((Class) itemType, (Class) clazz, flags);
                    if (always) {
                        stepBuilder.getChainBuilder().addFinal((Class) itemType, (Class) clazz);
                    }
                    return (sc, o) -> sc.produce((Class) itemType, (Class) clazz, itemType.cast(o));
                }
            }
            throw log.cannotProduce(clazz);
        }

        public Function<StepContext, Object> handleField(final StepBuilder stepBuilder, final Field field)
                throws IllegalArgumentException {
            return handleNonConsumerInput(stepBuilder, field.getGenericType(), field);
        }

        public boolean isStepMethod(final Method method) {
            Step step = method.getAnnotation(Step.class);
            if (step == null) {
                return false;
            }
            Class<? extends BooleanSupplier>[] when = step.when();
            for (Class<? extends BooleanSupplier> clazz : when) {
                try {
                    if (!clazz.getConstructor().newInstance().getAsBoolean()) {
                        return false;
                    }
                } catch (InstantiationException e) {
                    throw ReflectUtil.toError(e);
                } catch (IllegalAccessException e) {
                    throw ReflectUtil.toError(e);
                } catch (InvocationTargetException e) {
                    try {
                        throw e.getCause();
                    } catch (RuntimeException | Error e2) {
                        throw e2;
                    } catch (Throwable t) {
                        throw new UndeclaredThrowableException(t);
                    }
                } catch (NoSuchMethodException e) {
                    throw ReflectUtil.toError(e);
                }
            }
            Class<? extends BooleanSupplier>[] unless = step.unless();
            for (Class<? extends BooleanSupplier> clazz : unless) {
                try {
                    if (clazz.getConstructor().newInstance().getAsBoolean()) {
                        return false;
                    }
                } catch (InstantiationException e) {
                    throw ReflectUtil.toError(e);
                } catch (IllegalAccessException e) {
                    throw ReflectUtil.toError(e);
                } catch (InvocationTargetException e) {
                    try {
                        throw e.getCause();
                    } catch (RuntimeException | Error e2) {
                        throw e2;
                    } catch (Throwable t) {
                        throw new UndeclaredThrowableException(t);
                    }
                } catch (NoSuchMethodException e) {
                    throw ReflectUtil.toError(e);
                }
            }
            return true;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private Function<StepContext, Object> handleInput(final StepBuilder stepBuilder, final Type type,
                final AnnotatedElement element) {
            Class<?> clazz = rawTypeOf(type);
            if (clazz == StepContext.class) {
                return sc -> sc;
            }
            if (clazz == Consumer.class) {
                ForClass forClassAnnotation = element.getAnnotation(ForClass.class);
                Class<?> classArg = forClassAnnotation == null ? null : forClassAnnotation.value();
                // we inject a consumer that produces the accepted item
                Class<?> argType = rawTypeOfParameter(type, 0);
                if (Item.class.isAssignableFrom(argType)) {
                    ProduceFlags flags = ProduceFlags.NONE;
                    boolean always = element.getAnnotation(AlwaysProduce.class) != null;
                    if (element.getAnnotation(Weak.class) != null) {
                        flags = flags.with(ProduceFlag.WEAK);
                    }
                    if (element.getAnnotation(Overridable.class) != null) {
                        flags = flags.with(ProduceFlag.OVERRIDABLE);
                    }
                    if (SimpleItem.class.isAssignableFrom(argType) || MultiItem.class.isAssignableFrom(argType)) {
                        if (classArg != null) {
                            throw log.unnamedMustNotHaveArgument(argType);
                        }
                        Class<? extends Item> itemType = argType.asSubclass(Item.class);
                        stepBuilder.produces(itemType, flags);
                        if (always) {
                            stepBuilder.getChainBuilder().addFinal(itemType);
                        }
                        return sc -> new Consumer<Object>() {
                            @SuppressWarnings({ "unchecked", "rawtypes" })
                            public void accept(final Object o) {
                                sc.produce((Class) itemType, itemType.cast(o));
                            }
                        };
                    } else if (SimpleClassItem.class.isAssignableFrom(argType)
                            || MultiClassItem.class.isAssignableFrom(argType)) {
                        if (classArg == null) {
                            throw log.namedNeedsArgument(argType);
                        }
                        Class<? extends ClassItem<?>> itemType = (Class<? extends ClassItem<?>>) argType
                                .asSubclass(ClassItem.class);
                        stepBuilder.produces((Class) itemType, (Class) argType, flags);
                        if (always) {
                            stepBuilder.getChainBuilder().addFinal((Class) itemType, (Class) argType);
                        }
                        return sc -> new Consumer<Object>() {
                            @SuppressWarnings({ "unchecked", "rawtypes" })
                            public void accept(final Object o) {
                                sc.produce((Class) itemType, (Class) argType, itemType.cast(o));
                            }
                        };
                    }
                }
                throw log.cannotProduce(argType);
            }
            return handleNonConsumerInput(stepBuilder, type, element);
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private Function<StepContext, Object> handleNonConsumerInput(final StepBuilder stepBuilder, final Type type,
                final AnnotatedElement element) {
            Class<?> clazz = rawTypeOf(type);
            if (clazz.isPrimitive()) {
                throw log.cannotInjectPrimitive(clazz.getSimpleName(), element);
            }
            if (element.getAnnotation(AlwaysProduce.class) != null) {
                throw log.alwaysProduceNotProducer(element);
            }
            ForClass forClassAnnotation = element.getAnnotation(ForClass.class);
            Class<?> classArg = forClassAnnotation == null ? null : forClassAnnotation.value();
            Class<? extends Item> realType;
            ConsumeFlags flags = ConsumeFlags.NONE;
            if (clazz == Optional.class) {
                realType = rawTypeOfParameter(type, 0).asSubclass(Item.class);
                flags = flags.with(ConsumeFlag.OPTIONAL);
            } else if (SimpleItem.class.isAssignableFrom(clazz) || SimpleClassItem.class.isAssignableFrom(clazz)) {
                realType = clazz.asSubclass(Item.class);
            } else if (clazz == List.class) {
                realType = rawTypeOfParameter(typeOfParameter(type, 0), 0).asSubclass(Item.class);
            } else {
                throw log.cannotConsume(clazz);
            }
            if (SimpleItem.class.isAssignableFrom(realType) || MultiItem.class.isAssignableFrom(realType)) {
                if (classArg != null) {
                    throw log.unnamedMustNotHaveArgument(realType);
                }
                stepBuilder.consumes(realType, flags);
            } else if (SimpleClassItem.class.isAssignableFrom(realType) || MultiClassItem.class.isAssignableFrom(realType)) {
                if (classArg == null) {
                    throw log.namedNeedsArgument(realType);
                }
                stepBuilder.consumes((Class) realType, (Class) classArg, flags);
            }
            if (clazz == Optional.class) {
                if (SimpleItem.class.isAssignableFrom(realType)) {
                    return sc -> Optional.ofNullable(sc.consume((Class) realType));
                } else if (SimpleClassItem.class.isAssignableFrom(realType)) {
                    return sc -> Optional.ofNullable(sc.consume((Class) realType, (Class) classArg));
                } else {
                    throw log.cannotConsume(clazz);
                }
            } else if (clazz == List.class) {
                if (MultiItem.class.isAssignableFrom(realType)) {
                    return sc -> sc.consumeMulti((Class) realType);
                } else if (MultiClassItem.class.isAssignableFrom(realType)) {
                    return sc -> sc.consumeMulti((Class) realType, (Class) classArg);
                } else if (SimpleItem.class.isAssignableFrom(realType)) {
                    return sc -> List.of(sc.consume((Class) realType));
                } else if (SimpleClassItem.class.isAssignableFrom(realType)) {
                    return sc -> List.of(sc.consume((Class) realType, (Class) classArg));
                } else {
                    throw log.cannotConsume(clazz);
                }
            } else if (SimpleItem.class.isAssignableFrom(realType)) {
                return sc -> sc.consume((Class) realType);
            } else if (SimpleClassItem.class.isAssignableFrom(realType)) {
                return sc -> sc.consume((Class) realType, (Class) classArg);
            } else {
                throw log.cannotConsume(clazz);
            }
        }
    };

    /**
     * An injection mapper that delegates to another injection mapper.
     */
    interface Delegating extends InjectionMapper {
        InjectionMapper getDelegate();

        default Consumer<StepContext> handleClass(StepBuilder stepBuilder, Class<?> clazz) throws IllegalArgumentException {
            return getDelegate().handleClass(stepBuilder, clazz);
        }

        default Consumer<StepContext> handleStepMethod(StepBuilder stepBuilder, Method method) throws IllegalArgumentException {
            return getDelegate().handleStepMethod(stepBuilder, method);
        }

        default Function<StepContext, Object> handleParameter(StepBuilder stepBuilder, Executable executable, int paramIndex)
                throws IllegalArgumentException {
            return getDelegate().handleParameter(stepBuilder, executable, paramIndex);
        }

        default BiConsumer<StepContext, Object> handleReturnValue(StepBuilder stepBuilder, Method method)
                throws IllegalArgumentException {
            return getDelegate().handleReturnValue(stepBuilder, method);
        }

        default Function<StepContext, Object> handleField(StepBuilder stepBuilder, Field field)
                throws IllegalArgumentException {
            return getDelegate().handleField(stepBuilder, field);
        }

        default boolean isStepMethod(Method method) {
            return getDelegate().isStepMethod(method);
        }
    }
}
