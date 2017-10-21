# cdi-bundle
`cdi-bundle` is a Dropwizard bundle for adding [CDI](http://weld.cdi-spec.org) functionality to the Dropwizard applications.

### Usage
Add the following to the `build.gradle`

```
repositories {
	maven { url 'https://dl.bintray.com/cognodyne/maven' }
}

dependencies {
	compile "com.cognodyne.dw:cdi-bundle:$cdiBundleVersion"
}
```

Then, your `MyApplicationConfiguration.java` should look something like the following:

```
public class MyApplicationConfiguration extends Configuration implements CdiConfigurable {
	@Valid
    @JsonProperty("cdi")
    private Optional<CdiConfiguration> cdiConfig = Optional.empty();
    
    public CdiConfiguration getCdiConfiguration() {
    		return cdiConfig.orNull();
    }
}
```

Then, your `MyApplication.java` should look something like the following:

```
@ApplicationScoped
public class MyApplication extends Application<MyApplicationConfiguration> {
	@Inject
    private CdiBundle            cdiBundle;
    
    @Override
    public void initialize(Bootstrap<MyApplicationConfiguration> bootstrap) {
        bootstrap.addBundle(this.cdiBundle);
    }

    @Override
    public void run(ExampleConfiguration configuration, Environment environment) throws Exception {
    }
    
    public static void main(String... args) {
        try {
            CdiBundle.application(MyApplication.class, args).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

```

The above will perform the following:
- Add all subclass of `io.dropwizard.cli.Command` to the Dropwizard Bootstrap
- Add all subclass of `io.dropwizard.cli.ConfiguredCommand` to the Dropwizard Bootstrap
- Register all subclass of `com.codahale.metrics.health.HealthCheck` with `javax.inject.Named` annotation to the `io.dropwizard.setup.Environment.healthChecks()`
- Register all implementing classes of `io.dropwizard.lifecycle.Managed` interface to `io.dropwizard.setup.Environment.lifecycle().manage()`
- Register all subclass of `io.dropwizard.servlets.tasks.Task` to the `io.dropwizard.setup.Environment.admin().addTask()`
- Register all implementing classes of `javax.ws.rs.container.DynamicFeature` interface to `io.dropwizard.setup.Environment.jersey()`
- Add all implementing classes of `javax.servlet.Filter` interface with with `javax.servlet.annotation.WebFilter` annotation to the `io.dropwizard.setup.Environment.servlets().addFilter()`
- Add all implementing classes of `javax.servlet.Servlet` interface with with `javax.servlet.annotation.WebServlet` annotation to the `io.dropwizard.setup.Environment.servlets().addServlet()`
- Register all classes with `javax.ws.rs.Path` annotation to the `io.dropwizard.setup.Environment.jersey()`

The above default behavior can be altered with the appropriate configuration.

```
cdi:
  includes:
    - "com\.cognodyne\.cdi\..*"
  excludes:
    - "com\.cognodyne\..*"
    - "com\.some\.other\..*"
```

The above will exclude all fully qualified classes names matching one of the regular expressions from the `excludes` unless it also matches one of the regular expressions from the `includes`.
All classes are included unless excluded, and `includes` has higher precedent than `excudes`.

If any beans with `javax.inject.Singleton` or `javax.enterprise.context.ApplicationScoped` are also annotated with `com.cognodyne.dw.cdi.annotation.Startup`, then they will be started at the time of application startup.

Any non cdi managed object instances, such as `Bootstrap` or `Configuration`, can be injected via the following mechanism:

```
@ApplicationScoped
public class MyApplication extends Application<MyApplicationConfiguration> {
	@Inject
    private CdiBundle            cdiBundle;
    private ExampleConfiguration configuration;
    
    @Captured
    @Produces
    public ExampleConfiguration getConfiguration() {
    		return this.configuration;
    }
    
    @Override
    public void initialize(Bootstrap<MyApplicationConfiguration> bootstrap) {
        bootstrap.addBundle(this.cdiBundle);
    }

    @Override
    public void run(ExampleConfiguration configuration, Environment environment) throws Exception {
    		this.configuration = configuration;
    }
    
    public static void main(String... args) {
        try {
            CdiBundle.application(MyApplication.class, args).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

Then, `ExampleConfiguration` object can be injected as the following:

```
@Path("/hello")
public class HelloResource implements HelloService {
	@Inject
	@Captured
	private ExampleConfiguration configuration;
	
	@GET
	public String sayHello() {
		return this.configuration.getHelloMessage();
	}
}

```
`com.cognodyne.dw.cdi.annotation.Captured` is necessary to disambiguate the actual config object from the default constructed bean.
