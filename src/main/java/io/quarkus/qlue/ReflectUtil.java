package io.quarkus.qlue;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Member;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 */
final class ReflectUtil {
    private ReflectUtil() {
    }

    public static boolean rawTypeIs(Type type, Class<?> clazz) {
        return type instanceof Class<?> && clazz == type
                || type instanceof ParameterizedType pt && pt.getRawType() == clazz
                || type instanceof GenericArrayType gat && clazz.isArray()
                        && rawTypeIs(gat.getGenericComponentType(), clazz.getComponentType());
    }

    public static boolean rawTypeExtends(Type type, Class<?> clazz) {
        return type instanceof Class<?> && clazz.isAssignableFrom((Class<?>) type)
                || type instanceof ParameterizedType pt && rawTypeExtends(pt.getRawType(), clazz)
                || type instanceof GenericArrayType gat
                        && rawTypeExtends(gat.getGenericComponentType(), clazz.getComponentType());
    }

    public static boolean isListOf(Type type, Class<?> nestedType) {
        return isThingOf(type, List.class, nestedType);
    }

    public static boolean isConsumerOf(Type type, Class<?> nestedType) {
        return isThingOf(type, Consumer.class, nestedType);
    }

    public static boolean isSupplierOf(Type type, Class<?> nestedType) {
        return isThingOf(type, Supplier.class, nestedType);
    }

    public static boolean isSupplierOfOptionalOf(Type type, Class<?> nestedType) {
        return type instanceof ParameterizedType && rawTypeIs(type, Supplier.class)
                && isOptionalOf(typeOfParameter(type, 0), nestedType);
    }

    public static boolean isOptionalOf(Type type, Class<?> nestedType) {
        return isThingOf(type, Optional.class, nestedType);
    }

    public static boolean isThingOf(Type type, Class<?> thing, Class<?> nestedType) {
        return type instanceof ParameterizedType && rawTypeIs(type, thing)
                && rawTypeExtends(typeOfParameter(type, 0), nestedType);
    }

    public static Class<?> rawTypeOf(final Type type) {
        if (type instanceof Class<?> c) {
            return c;
        } else if (type instanceof ParameterizedType pt) {
            return rawTypeOf(pt.getRawType());
        } else if (type instanceof GenericArrayType gat) {
            return Array.newInstance(rawTypeOf(gat.getGenericComponentType()), 0).getClass();
        } else {
            throw new IllegalArgumentException("Type has no raw type class: " + type);
        }
    }

    private static final Class<?>[] NO_CLASSES = new Class[0];

    public static Class<?>[] rawTypesOfDestructive(final Type[] types) {
        if (types.length == 0) {
            return NO_CLASSES;
        }
        Type t;
        Class<?> r;
        for (int i = 0; i < types.length; i++) {
            t = types[i];
            r = rawTypeOf(t);
            if (r != t) {
                types[i] = r;
            }
        }
        return Arrays.copyOf(types, types.length, Class[].class);
    }

    public static Type typeOfParameter(final Type type, final int paramIdx) {
        if (type instanceof ParameterizedType pt) {
            return pt.getActualTypeArguments()[paramIdx];
        } else {
            throw new IllegalArgumentException("Type is not parameterized: " + type);
        }
    }

    public static Class<?> rawTypeOfParameter(final Type type, final int paramIdx) {
        return rawTypeOf(typeOfParameter(type, paramIdx));
    }

    public static InstantiationError toError(final InstantiationException e) {
        final InstantiationError error = new InstantiationError(e.getMessage());
        error.setStackTrace(e.getStackTrace());
        return error;
    }

    public static IllegalAccessError toError(final IllegalAccessException e) {
        final IllegalAccessError error = new IllegalAccessError(e.getMessage());
        error.setStackTrace(e.getStackTrace());
        return error;
    }

    public static NoSuchMethodError toError(final NoSuchMethodException e) {
        final NoSuchMethodError error = new NoSuchMethodError(e.getMessage());
        error.setStackTrace(e.getStackTrace());
        return error;
    }

    public static NoSuchFieldError toError(final NoSuchFieldException e) {
        final NoSuchFieldError error = new NoSuchFieldError(e.getMessage());
        error.setStackTrace(e.getStackTrace());
        return error;
    }

    public static IllegalArgumentException reportError(AnnotatedElement e, String fmt, Object... args) {
        if (e instanceof Member m) {
            return new IllegalArgumentException(
                    String.format(fmt, args) + " at " + e + " of " + m.getDeclaringClass());
        } else if (e instanceof Parameter p) {
            return new IllegalArgumentException(
                    String.format(fmt, args) + " at " + e + " of " + p.getDeclaringExecutable() + " of "
                            + p.getDeclaringExecutable().getDeclaringClass());
        } else {
            return new IllegalArgumentException(String.format(fmt, args) + " at " + e);
        }
    }
}
