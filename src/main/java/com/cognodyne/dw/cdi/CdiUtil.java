package com.cognodyne.dw.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.regex.Pattern;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import com.google.common.collect.Sets;

public class CdiUtil {
    public static BeanManager getBeanManager() {
        return CDI.current().getBeanManager();
    }

    public static boolean isAnnotationPresent(Bean<?> bean, Class<? extends Annotation> annotationClass) {
        return isAnnotationPresent(bean.getTypes(), annotationClass);
    }

    public static boolean isAnnotationPresent(Set<Type> types, Class<? extends Annotation> annotationClass) {
        for (Type type : types) {
            Class<?> typeClass = type.getClass();
            if (type instanceof Class) {
                typeClass = (Class<?>) type;
            } else if (type instanceof ParameterizedType) {
                ParameterizedType ptype = (ParameterizedType) type;
                typeClass = (Class<?>) ptype.getRawType();
            }
            if (typeClass.isAnnotationPresent(annotationClass)) {
                return true;
            }
        }
        return false;
    }

    public static <T extends Annotation> T getAnnotation(Bean<?> bean, Class<T> annotationClass) {
        return getAnnotation(bean.getTypes(), annotationClass);
    }

    public static <T extends Annotation> T getAnnotation(Set<Type> types, Class<T> annotationClass) {
        for (Type type : types) {
            Class<?> typeClass = type.getClass();
            if (type instanceof Class) {
                typeClass = (Class<?>) type;
            } else if (type instanceof ParameterizedType) {
                ParameterizedType ptype = (ParameterizedType) type;
                typeClass = (Class<?>) ptype.getRawType();
            }
            if (typeClass.isAnnotationPresent(annotationClass)) {
                return typeClass.getAnnotation(annotationClass);
            }
        }
        return null;
    }

    public static Set<Method> getMethods(Bean<?> bean, String methodNameRegEx, Set<Class<? extends Annotation>> annotationClasses) {
        Pattern pattern = Pattern.compile(methodNameRegEx);
        Set<Method> result = Sets.newHashSet();
        for (Type type : bean.getTypes()) {
            Class<?> typeClass = type.getClass();
            if (type instanceof Class) {
                typeClass = (Class<?>) type;
            } else if (type instanceof ParameterizedType) {
                ParameterizedType ptype = (ParameterizedType) type;
                typeClass = (Class<?>) ptype.getRawType();
            }
            for (Method method : typeClass.getDeclaredMethods()) {
                if (pattern.matcher(method.getName()).matches()) {
                    for (Class<? extends Annotation> anno : annotationClasses) {
                        if (method.isAnnotationPresent(anno)) {
                            result.add(method);
                        }
                    }
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getReference(Class<? extends T> cls, Annotation... qualifiers) {
        Set<Bean<?>> beans = getBeanManager().getBeans(cls, qualifiers);
        if (beans == null || beans.isEmpty()) {
            return null;
        }
        return getReference((Bean<T>) beans.iterator().next());
    }

    public static <T> T getReference(Bean<T> bean) {
        return getReference(getBeanManager(), bean);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getReference(BeanManager bm, Bean<T> bean) {
        CreationalContext<T> ctx = bm.createCreationalContext(bean);
        return (T) bm.getReference(bean, bean.getBeanClass(), ctx);
    }

    public static <T> T create(Bean<T> bean) {
        return create(getBeanManager(), bean);
    }

    public static <T> T create(BeanManager bm, Bean<T> bean) {
        CreationalContext<T> ctx = bm.createCreationalContext(bean);
        return bean.create(ctx);
    }

    public static <T> void destroy(Bean<T> bean) {
        destroy(getBeanManager(), bean);
    }

    public static <T> void destroy(BeanManager bm, Bean<T> bean) {
        bean.destroy(getReference(bm, bean), bm.createCreationalContext(bean));
    }
}
