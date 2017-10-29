package com.cognodyne.dw.cdi;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Priority;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.ws.rs.Path;
import javax.ws.rs.container.DynamicFeature;

import org.jboss.weld.bootstrap.api.CDI11Bootstrap;
import org.jboss.weld.bootstrap.spi.Deployment;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.jboss.weld.resources.spi.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.health.HealthCheck;
import com.cognodyne.dw.common.DeployableWeldService;

import io.dropwizard.Application;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.cli.Command;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.servlets.tasks.Task;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import jersey.repackaged.com.google.common.collect.Lists;

@Singleton
public class CdiBundle implements ConfiguredBundle<CdiConfigurable> {
    private static final Logger logger = LoggerFactory.getLogger(CdiBundle.class);
    @Inject
    private CdiExtension        extension;
    @Inject
    private BeanManager         bm;

    public static <T extends Application<?>> ApplicationStarter<T> application(Class<T> appClass, String... args) {
        return new ApplicationStarter<T>(appClass, args);
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {
        logger.debug("initializing...");
        this.extension.getBeans().stream().filter(bean -> Command.class.isAssignableFrom(bean.getBeanClass())).forEach(bean -> {
            logger.info("adding command:{}...", bean.getBeanClass().getName());
            bootstrap.addCommand((Command) CdiUtil.getReference(bm, bean));
        });
        this.extension.getBeans().stream().filter(bean -> ConfiguredCommand.class.isAssignableFrom(bean.getBeanClass())).forEach(bean -> {
            logger.info("adding command:{}...", bean.getBeanClass().getName());
            bootstrap.addCommand((ConfiguredCommand<?>) CdiUtil.getReference(bm, bean));
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run(CdiConfigurable configuration, Environment environment) throws Exception {
        logger.debug("running...", configuration);
        environment.getApplicationContext().addEventListener(org.jboss.weld.environment.servlet.Listener.using(bm));
        //register healthchecks
        this.extension.getBeans().stream().filter(bean -> HealthCheck.class.isAssignableFrom(bean.getBeanClass()) && CdiUtil.isAnnotationPresent(bean, Named.class)).forEach(bean -> {
            if (configuration.getCdiConfiguration() == null || configuration.getCdiConfiguration().include(bean.getBeanClass())) {
                logger.info("registering healthcheck:{}...", bean.getBeanClass().getName());
                environment.healthChecks().register(CdiUtil.getAnnotation(bean, Named.class).value(), (HealthCheck) CdiUtil.getReference(bm, bean));
            } else {
                logger.info("not registering healthcheck:{} due to the configuration", bean.getBeanClass().getName());
            }
        });
        //register managed
        this.extension.getBeans().stream().filter(bean -> Managed.class.isAssignableFrom(bean.getBeanClass())).forEach(bean -> {
            if (configuration.getCdiConfiguration() == null || configuration.getCdiConfiguration().include(bean.getBeanClass())) {
                logger.info("registering managed:{}...", bean.getBeanClass().getName());
                environment.lifecycle().manage((Managed) CdiUtil.getReference(bm, bean));
            } else {
                logger.info("not registering managed:{} due to the configuration", bean.getBeanClass().getName());
            }
        });
        //register tasks
        this.extension.getBeans().stream().filter(bean -> Task.class.isAssignableFrom(bean.getBeanClass())).forEach(bean -> {
            if (configuration.getCdiConfiguration() == null || configuration.getCdiConfiguration().include(bean.getBeanClass())) {
                logger.info("registering task:{}...", bean.getBeanClass().getName());
                environment.admin().addTask((Task) CdiUtil.getReference(bm, bean));
            } else {
                logger.info("not registering task:{} due to the configuraiton", bean.getBeanClass().getName());
            }
        });
        //register dynamic feature
        this.extension.getBeans().stream().filter(bean -> DynamicFeature.class.isAssignableFrom(bean.getBeanClass())).forEach(bean -> {
            if (configuration.getCdiConfiguration() == null || configuration.getCdiConfiguration().include(bean.getBeanClass())) {
                logger.info("registering dynamic feature:{}...", bean.getBeanClass().getName());
                environment.jersey().register(bean.getBeanClass());
            } else {
                logger.info("not registering dynamic feature:{} due to the configuration", bean.getBeanClass().getName());
            }
        });
        //register servlet filters
        this.extension.getBeans().stream()//
                .filter(bean -> Filter.class.isAssignableFrom(bean.getBeanClass()) && CdiUtil.isAnnotationPresent(bean, WebFilter.class))//
                .sorted((lo, ro) -> {
                    int lhs = CdiUtil.isAnnotationPresent(lo, Priority.class) ? CdiUtil.getAnnotation(lo, Priority.class).value() : Integer.MAX_VALUE;
                    int rhs = CdiUtil.isAnnotationPresent(ro, Priority.class) ? CdiUtil.getAnnotation(ro, Priority.class).value() : Integer.MAX_VALUE;
                    return lhs - rhs;
                }).forEach(bean -> {
                    if (configuration.getCdiConfiguration() == null || configuration.getCdiConfiguration().include(bean.getBeanClass())) {
                        logger.info("registering servlet filter:{}...", bean.getBeanClass().getName());
                        WebFilter anno = CdiUtil.getAnnotation(bean, WebFilter.class);
                        javax.servlet.FilterRegistration.Dynamic filter = environment.servlets().addFilter(anno.filterName(),  (Class<Filter>)bean.getBeanClass());
                        if (anno.urlPatterns() != null && anno.urlPatterns().length != 0) {
                            filter.addMappingForUrlPatterns(EnumSet.copyOf(Arrays.asList(anno.dispatcherTypes())), true, anno.urlPatterns());
                        } else if (anno.value() != null && anno.value().length != 0) {
                            filter.addMappingForUrlPatterns(EnumSet.copyOf(Arrays.asList(anno.dispatcherTypes())), true, anno.value());
                        } else if (anno.servletNames() != null && anno.servletNames().length != 0) {
                            filter.addMappingForUrlPatterns(EnumSet.copyOf(Arrays.asList(anno.dispatcherTypes())), true, anno.servletNames());
                        }
                        filter.setAsyncSupported(anno.asyncSupported());
                        if (anno.initParams() != null && anno.initParams().length != 0) {
                            for (WebInitParam param : anno.initParams()) {
                                filter.setInitParameter(param.name(), param.value());
                            }
                        }
                    } else {
                        logger.info("not registering servlet filter:{} due to the configuartion", bean.getBeanClass().getName());
                    }
                });
        //register servlets
        this.extension.getBeans().stream().filter(bean -> Servlet.class.isAssignableFrom(bean.getBeanClass()) && CdiUtil.isAnnotationPresent(bean, WebServlet.class)).forEach(bean -> {
            if (configuration.getCdiConfiguration() == null || configuration.getCdiConfiguration().include(bean.getBeanClass())) {
                logger.info("registering servlet:{}...", bean.getBeanClass().getName());
                WebServlet anno = CdiUtil.getAnnotation(bean, WebServlet.class);
                javax.servlet.ServletRegistration.Dynamic servlet = environment.servlets().addServlet(anno.name(), (Servlet) CdiUtil.getReference(bm, bean));
                if (anno.urlPatterns() != null && anno.urlPatterns().length != 0) {
                    servlet.addMapping(anno.urlPatterns());
                } else if (anno.value() != null && anno.value().length != 0) {
                    servlet.addMapping(anno.value());
                }
                servlet.setAsyncSupported(anno.asyncSupported());
                servlet.setLoadOnStartup(anno.loadOnStartup());
                if (anno.initParams() != null && anno.initParams().length != 0) {
                    for (WebInitParam param : anno.initParams()) {
                        servlet.setInitParameter(param.name(), param.value());
                    }
                }
            } else {
                logger.info("not registering servlet:{} due to the configuration", bean.getBeanClass().getName());
            }
        });
        //register all Jersey resources
        this.extension.getBeans().stream().filter(b -> CdiUtil.isAnnotationPresent(b, Path.class)).forEach(bean -> {
            if (configuration.getCdiConfiguration() == null || configuration.getCdiConfiguration().include(bean.getBeanClass())) {
                logger.info("registering jersey resource:{}...", bean.getBeanClass().getName());
                environment.jersey().register(bean.getBeanClass());
            } else {
                logger.info("not registering jersey resource:{} due to the configuartion", bean.getBeanClass().getName());
            }
        });
    }

    public static final class ApplicationStarter<T extends Application<?>> {
        private Class<T>                    cls;
        private String[]                    args;
        private List<DeployableWeldService> services = Lists.newArrayList();

        private ApplicationStarter(Class<T> cls, String... args) {
            this.cls = cls;
            this.args = args;
        }

        public ApplicationStarter<T> with(DeployableWeldService service) {
            this.services.add(service);
            return this;
        }

        public void start() throws Exception {
            Weld weld = new Weld() {
                protected Deployment createDeployment(ResourceLoader resourceLoader, CDI11Bootstrap bootstrap) {
                    Deployment deployment = super.createDeployment(resourceLoader, bootstrap);
                    services.stream().forEach(service -> {
                        //                            Class<org.jboss.weld.bootstrap.api.Service> type = (Class<Service>) resourceLoader.classForName(conf.getString("type"));
                        //                            Class<org.jboss.weld.bootstrap.api.Service> impl = (Class<Service>) resourceLoader.classForName(conf.getString("implementation"));
                        deployment.getServices().add(service.getType(), service.getService());
                    });
                    return deployment;
                }
            };
            WeldContainer container = weld.initialize();
            container.select(cls).get().run(args);
        }
    }
}
