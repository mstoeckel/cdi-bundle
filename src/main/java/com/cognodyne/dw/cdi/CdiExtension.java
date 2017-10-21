package com.cognodyne.dw.cdi;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessBean;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cognodyne.dw.cdi.annotation.Startup;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

class CdiExtension implements Extension {
    private static final Logger logger   = LoggerFactory.getLogger(CdiExtension.class);
    private Set<Bean<?>>        beans    = Sets.newHashSet();
    private List<Bean<?>>       startups = Lists.newArrayList();

    public Set<Bean<?>> getBeans() {
        return this.beans;
    }

    public List<Bean<?>> getStartups() {
        return this.startups;
    }

    @SuppressWarnings("unused")
    private <X> void onProcessBean(@Observes ProcessBean<X> event, BeanManager beanManager) {
        logger.debug("onProcessBean:{}", event.getBean());
        Bean<X> bean = event.getBean();
        this.beans.add(bean);
    }

    @SuppressWarnings("unused")
    private void onAfterDeploymentValidation(@Observes AfterDeploymentValidation event, BeanManager beanManager) {
        this.startups = orderDependencies(this.getBeans().stream()//
                .filter(bean -> (CdiUtil.isAnnotationPresent(bean, ApplicationScoped.class) || CdiUtil.isAnnotationPresent(bean, Singleton.class)) && CdiUtil.isAnnotationPresent(bean, Startup.class))//
                .collect(Collectors.toList()));
        logger.debug("ordered startup beans:{}", this.startups);
        this.startups.stream().forEach(bean -> {
            beanManager.getReference(bean, bean.getBeanClass(), beanManager.createCreationalContext(bean)).toString();
        });
    }

    private <T> List<Bean<? extends T>> orderDependencies(List<Bean<? extends T>> list) {
        //first create a map of beans by class
        Map<Class<?>, Bean<? extends T>> beans = Maps.newHashMap();
        for (Bean<? extends T> bean : list) {
            beans.put(bean.getBeanClass(), bean);
        }
        Graph<T> graph = new Graph<T>(beans);
        //next we get the sorted node and turn it into list of beans
        return graph.getSorted().stream()//
                .map(new Function<Node<T>, Bean<? extends T>>() {
                    public Bean<? extends T> apply(Node<T> t) {
                        return t.bean;
                    }
                })//
                .collect(Collectors.toList());
    }

    private static class Graph<T> {
        private Set<Node<T>> nodes = Sets.newHashSet();

        public Graph(Map<Class<?>, Bean<? extends T>> beans) {
            Map<Class<?>, Node<T>> nodeMap = Maps.newHashMap();
            for (Entry<Class<?>, Bean<? extends T>> entry : beans.entrySet()) {
                Node<T> node = nodeMap.get(entry.getKey());
                if (node == null) {
                    node = new Node<T>(entry.getValue());
                    nodeMap.put(entry.getKey(), node);
                }
                nodes.add(node);
                Startup anno = CdiUtil.getAnnotation(entry.getValue(), Startup.class);
                if (anno != null) {
                    for (Class<?> cls : anno.after()) {
                        Bean<? extends T> bean = beans.get(cls);
                        if (bean == null) {
                            throw new UnsatisfiedDependencyException(cls + " not found");
                        }
                        Node<T> dependsOnNode = nodeMap.get(cls);
                        if (dependsOnNode == null) {
                            dependsOnNode = new Node<T>(bean);
                            nodeMap.put(cls, dependsOnNode);
                        }
                        node.dependsOn.add(dependsOnNode);
                    }
                }
            }
        }

        // implementation of Kahn's topological sort algorithm  https://en.wikipedia.org/wiki/Topological_sorting
        public List<Node<T>> getSorted() {
            List<Node<T>> list = Lists.newArrayList();
            Set<Node<T>> set = nodes.stream().filter(new Predicate<Node<T>>() {
                public boolean test(Node<T> node) {
                    return !hasIncomingEdge(node);
                }
            }).collect(Collectors.toSet());
            while (!set.isEmpty()) {
                Node<T> node = removeAny(set);
                list.add(node);
                for (Node<T> dependsOn : ImmutableSet.copyOf(node.dependsOn)) {
                    node.dependsOn.remove(dependsOn);
                    if (!hasIncomingEdge(dependsOn)) {
                        set.add(dependsOn);
                    }
                }
            }
            Optional<Node<T>> node = nodes.stream().filter(new Predicate<Node<T>>() {
                @Override
                public boolean test(Node<T> t) {
                    return !t.dependsOn.isEmpty();
                }
            }).findAny();
            if (node.isPresent()) {
                throw new CircularDependencyDetectedException("Circular dependency detected between " + node.get().bean.getBeanClass() + " and " + node.get().dependsOn.stream()//
                        .map(new Function<Node<T>, Class<?>>() {
                            public Class<?> apply(Node<T> t) {
                                return t.bean.getBeanClass();
                            }
                        })//
                        .collect(Collectors.toSet()));
            }
            return Lists.reverse(list);
        }

        private boolean hasIncomingEdge(Node<T> n) {
            for (Node<T> node : nodes) {
                if (node.dependsOn.contains(n)) {
                    return true;
                }
            }
            return false;
        }

        private Node<T> removeAny(Set<Node<T>> set) {
            Node<T> node = set.iterator().next();
            set.remove(node);
            return node;
        }
    }

    private static class Node<T> {
        private Bean<? extends T> bean;
        private Set<Node<T>>      dependsOn = Sets.newHashSet();

        private Node(Bean<? extends T> bean) {
            this.bean = bean;
        }

        @Override
        public int hashCode() {
            return bean.getBeanClass().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Node<?> other = (Node<?>) obj;
            return this.bean.getBeanClass().equals(other.bean.getBeanClass());
        }
    }
}
