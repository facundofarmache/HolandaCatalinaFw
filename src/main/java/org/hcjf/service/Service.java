package org.hcjf.service;

import org.hcjf.log.Log;
import org.hcjf.properties.SystemProperties;

import java.util.*;
import java.util.concurrent.*;

/**
 * This abstract class contains all the implementations and
 * the interfaces that describe the behavior of the system service.
 * @author javaito
 */
public abstract class Service<C extends ServiceConsumer> {

    protected static final String SERVICE_LOG_TAG = "SERVICE";

    private final String serviceName;
    private final ThreadFactory serviceThreadFactory;
    private final ThreadPoolExecutor serviceExecutor;
    private final Set<ThreadPoolExecutor> registeredExecutors;
    private final Integer priority;

    /**
     * Service constructor.
     * @param serviceName Name of the service, can't be null.
     * @param priority Service execution priority.
     * @throws NullPointerException If the name is null.
     */
    protected Service(String serviceName, Integer priority) {
        if(serviceName == null) {
            throw new NullPointerException("Service name can't be null");
        }
        if(SystemServices.instance.exist(serviceName)) {
            throw new IllegalArgumentException("The service name (" + serviceName + ") is already register");
        }

        this.serviceName = serviceName;
        this.priority = priority;
        this.serviceThreadFactory = createThreadFactory();
        this.serviceExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool(this.serviceThreadFactory);
        this.serviceExecutor.setCorePoolSize(SystemProperties.getInteger(SystemProperties.Service.THREAD_POOL_CORE_SIZE));
        this.serviceExecutor.setMaximumPoolSize(SystemProperties.getInteger(SystemProperties.Service.THREAD_POOL_MAX_SIZE));
        this.serviceExecutor.setKeepAliveTime(SystemProperties.getLong(SystemProperties.Service.THREAD_POOL_KEEP_ALIVE_TIME), TimeUnit.SECONDS);
        this.registeredExecutors = new HashSet<>();
        init();
        if(!getClass().equals(Log.class)) {
            SystemServices.instance.register(this);
        } else {
            SystemServices.instance.setLog((Log)this);
        }
    }

    /**
     * Create the default thread factory for any service.
     * @return Thread factory.
     */
    private ThreadFactory createThreadFactory() {
        return r -> new ServiceThread(r, getServiceName() + UUID.randomUUID());
    }

    /**
     * Return the internal thread pool executor of the service.
     * @return Thread pool executor.
     */
    private ThreadPoolExecutor getServiceExecutor() {
        return serviceExecutor;
    }

    /**
     * This method execute any callable over service thread with a service session.
     * @param callable Callable to execute.
     * @param <R> Expected result.
     * @return Callable's future.
     */
    protected final <R extends Object> Future<R> fork(Callable<R> callable) {
        return fork(callable, getServiceExecutor());
    }

    /**
     * This method execute any callable over service thread with a service session using an
     * custom thread pool executor. This thread pool executor must create only Service thread implementations.
     * @param callable Callable to execute.
     * @param executor Custom thread pool executor.
     * @param <R> Expected return type.
     * @return Callable's future.
     */
    protected final <R extends Object> Future<R> fork(Callable<R> callable, ThreadPoolExecutor executor) {
        if(!executor.equals(serviceExecutor)) {
            synchronized (this) {
                if (!registeredExecutors.contains(executor)) {
                    registeredExecutors.add(executor);
                }
            }
        }

        ServiceSession session = ServiceSession.getGuestSession();
        Map<String, Object> invokerProperties = null;
        if(Thread.currentThread() instanceof ServiceThread) {
            session = ((ServiceThread) Thread.currentThread()).getSession();
            invokerProperties = session.getProperties();
        }
        return executor.submit(new CallableWrapper<>(callable, session, invokerProperties));
    }

    /**
     * This method execute any runnable over service thread with a service session.
     * @param runnable Runnable to execute.
     * @return Runnable's future.
     */
    protected final Future fork(Runnable runnable) {
        return fork(runnable, getServiceExecutor());
    }

    /**
     * This method execute any runnnable over service thread with a service session using an
     * custom thread pool executor. This thread pool executor must create only Service thread implementations.
     * @param runnable Runnable to execute.
     * @param executor Custom thread pool executor.
     * @return Runnable's future.
     */
    protected final Future fork(Runnable runnable, ThreadPoolExecutor executor) {
        if(!executor.equals(serviceExecutor)) {
            synchronized (this) {
                if (!registeredExecutors.contains(executor)) {
                    registeredExecutors.add(executor);
                }
            }
        }

        ServiceSession session = ServiceSession.getGuestSession();
        Map<String, Object> invokerProperties = null;
        if(Thread.currentThread() instanceof ServiceThread) {
            session = ((ServiceThread) Thread.currentThread()).getSession();
            invokerProperties = session.getProperties();
        }
        return executor.submit(new RunnableWrapper(runnable, session, invokerProperties));
    }

    /**
     * Return the service name.
     * @return Service name.
     */
    public final String getServiceName() {
        return serviceName;
    }

    /**
     * Return the service priority.
     * @return Service priority.
     */
    public final Integer getPriority() {
        return priority;
    }

    /**
     * This method will be called immediately after
     * of the execution of the service's constructor method
     */
    protected void init(){};

    /**
     * This method will be called for the global shutdown process
     * in each stage.
     * @param stage Shutdown stage.
     */
    protected void shutdown(ShutdownStage stage) {}

    /**
     * This method will be called for the global shutdown process
     * when the process try to finalize the registered thread pool executors.
     * @param executor Thread pool executor to finalize.
     */
    protected void shutdownRegisteredExecutor(ThreadPoolExecutor executor) {}

    /**
     * This method register the consumer in the service.
     * @param consumer Object with the logic to consume the service.
     * @throws RuntimeException It contains exceptions generated by
     * the particular logic of each implementation.
     */
    public abstract void registerConsumer(C consumer);

    /**
     * Unregister a specific consumer.
     * @param consumer Consumer to unregister.
     */
    public abstract void unregisterConsumer(C consumer);

    /**
     * This method start the global shutdown process.
     */
    public static final void systemShutdown() {
        SystemServices.instance.shutdown();
    }

    /**
     * This method is the gateway to the service subsystem from context out of
     * the hcjf domain.
     * @param runnable Custom runnable.
     * @param session Custom session.
     */
    public static final void run(Runnable runnable, ServiceSession session) {
        RunnableWrapper serviceRunnable = new RunnableWrapper(runnable, session);
        SystemServices.instance.serviceExecutor.execute(serviceRunnable);
    }

    /**
     * This internal class contains all the services registered
     * in the system.
     */
    private static class SystemServices {

        private static final SystemServices instance;

        static {
            instance = new SystemServices();
        }

        private final ThreadPoolExecutor serviceExecutor;
        private final Map<String, Service> services;
        private Log log;

        /**
         * Constructor.
         */
        private SystemServices() {
            this.serviceExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool(
                    runnable -> new ServiceThread(runnable, SystemProperties.get(SystemProperties.Service.STATIC_THREAD_NAME)));
            this.serviceExecutor.setCorePoolSize(SystemProperties.getInteger(SystemProperties.Service.STATIC_THREAD_POOL_CORE_SIZE));
            this.serviceExecutor.setMaximumPoolSize(SystemProperties.getInteger(SystemProperties.Service.STATIC_THREAD_POOL_MAX_SIZE));
            this.serviceExecutor.setKeepAliveTime(SystemProperties.getLong(SystemProperties.Service.STATIC_THREAD_POOL_KEEP_ALIVE_TIME), TimeUnit.SECONDS);
            services = new HashMap<>();

            //Adding service shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    shutdown();
                }
            });
        }

        /**
         * Set the unique instance of the system log service.
         * @param log Instance of the system log.
         */
        public void setLog(Log log) {
            this.log = log;
        }

        /**
         * This method save the instance of the service in the internal
         * map indexed by the name of the service.
         * @param service Instance of the service.
         */
        private void register(Service service) {
            services.put(service.getServiceName(), service);
            Log.i(Service.SERVICE_LOG_TAG, "Service registered: %s", service.getServiceName());
        }

        /**
         * Return true if the service name exist in the registered services.
         * @param serviceName Name of the service.
         * @return Return true if exist and false in otherwise
         */
        private boolean exist(String serviceName) {
            return services.containsKey(serviceName);
        }

        /**
         * Start the shutdown process for all the services registered
         */
        private void shutdown() {
            Set<Service> sortedServices = new TreeSet<>((s1, s2) -> {
                int result = s1.getPriority() - s2.getPriority();

                if(result == 0) {
                    result = s1.hashCode() - s2.hashCode();
                }

                return result * -1;
            });

            int errors = 0;
            Log.i(Service.SERVICE_LOG_TAG, "Starting shutdown");
            sortedServices.addAll(services.values());
            for(Service<?> service : sortedServices) {
                Log.i(Service.SERVICE_LOG_TAG, "Starting service shutdown (%s)", service.getServiceName());
                Log.i(Service.SERVICE_LOG_TAG, "Starting service shutdown custom process");
                try {
                    service.shutdown(ShutdownStage.START);
                    Log.i(Service.SERVICE_LOG_TAG, "Start stage: Shutdown custom process done");
                } catch (Exception ex) {
                    Log.i(Service.SERVICE_LOG_TAG, "Start stage: Shutdown custom process done with errors", ex);
                    errors++;
                }

                Log.i(Service.SERVICE_LOG_TAG, "Ending custom executors");
                service.registeredExecutors.forEach(service::shutdownRegisteredExecutor);
                Log.i(Service.SERVICE_LOG_TAG, "Custom executors finalized");

                try {
                    service.shutdown(ShutdownStage.END);
                    Log.i(Service.SERVICE_LOG_TAG, "End stage: Shutdown custom process done");
                } catch (Exception ex) {
                    Log.i(Service.SERVICE_LOG_TAG, "End stage: Shutdown custom process done with errors", ex);
                    errors++;
                }

                Log.i(Service.SERVICE_LOG_TAG, "Ending main service executor");
                service.serviceExecutor.shutdown();
                while(!service.serviceExecutor.isTerminated()) {
                    try {
                        Thread.sleep(SystemProperties.getLong(
                                SystemProperties.Service.SHUTDOWN_TIME_OUT));
                    } catch (InterruptedException e) {}
                }
                Log.i(Service.SERVICE_LOG_TAG, "Main service executor finalized");
            }

            Log.i(Service.SERVICE_LOG_TAG, "Shutdown");
            try {
                ((Service)log).shutdown(ShutdownStage.START);
                ((Service)log).serviceExecutor.shutdown();
                while(!((Service)log).serviceExecutor.isTerminated()) {
                    try {
                        Thread.sleep(SystemProperties.getLong(
                                SystemProperties.Service.SHUTDOWN_TIME_OUT));
                    } catch (InterruptedException e) {}
                }
                ((Service)log).shutdown(ShutdownStage.END);
            } catch (Exception ex) {
                errors++;
            }

            Runtime.getRuntime().halt(errors);
        }

    }

    /**
     * Enum all the shutting down stages
     */
    protected enum ShutdownStage {

        START,

        END

    }

    /**
     * This comparator gets priority to service runnable.
     */
    public static class RunnableWrapperComparator implements Comparator<Runnable> {

        @Override
        public int compare(Runnable o1, Runnable o2) {
            int result = (int)(((RunnableWrapper)o1).creationTime - ((RunnableWrapper)o2).creationTime) * -1;

            if(result == 0) {
                result = o1.hashCode() - o2.hashCode();
            }

            return result;
        }
    }

    /**
     * This wrapper encapsulate any other runnable in order to set the environment
     * session on the service thread.
     */
    private static class RunnableWrapper implements Runnable {

        private final Runnable runnable;
        private final ServiceSession session;
        private final Map<String, Object> invokerProperties;
        private final long creationTime;

        public RunnableWrapper(Runnable runnable, ServiceSession session) {
            this(runnable, session, new HashMap<>());
        }

        public RunnableWrapper(Runnable runnable, ServiceSession session, Map<String, Object> invokerProperties) {
            this.runnable = runnable;
            this.invokerProperties = invokerProperties;
            this.creationTime = System.currentTimeMillis();
            if(session != null) {
                this.session = session;
            } else {
                this.session = ServiceSession.getGuestSession();
            }
        }

        @Override
        public void run() {
            if(!(Thread.currentThread() instanceof ServiceThread)) {
                throw new IllegalArgumentException("All the service executions must be over ServiceThread implementation");
            }

            try {
                ((ServiceThread) Thread.currentThread()).setSession(session);
                if(invokerProperties != null) {
                    session.putAll(invokerProperties);
                }
                runnable.run();
            } finally {
                ((ServiceThread) Thread.currentThread()).setSession(null);
            }
        }

    }

    /**
     * This wrapper encapsulate any other callable in order to set the environment
     * session on the service thread.
     */
    private static class CallableWrapper<O extends Object> implements Callable<O> {

        private final Callable<O> callable;
        private final ServiceSession session;
        private final Map<String, Object> invokerProperties;

        public CallableWrapper(Callable<O> callable, ServiceSession session, Map<String, Object> invokerProperties) {
            this.callable = callable;
            this.invokerProperties = invokerProperties;
            if(session != null) {
                this.session = session;
            } else {
                this.session = ServiceSession.getGuestSession();
            }
        }

        @Override
        public O call() throws Exception {
            if(!(Thread.currentThread() instanceof ServiceThread)) {
                throw new IllegalArgumentException("All the service executions must be over ServiceThread implementation");
            }

            try {
                ((ServiceThread) Thread.currentThread()).setSession(session);
                if(invokerProperties != null) {
                    session.putAll(invokerProperties);
                }
                return callable.call();
            } finally {
                ((ServiceThread) Thread.currentThread()).setSession(null);
            }
        }

    }
}
