package com.mybank.autostone.scheduler;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * Quartz creates Job instances itself via a no-arg constructor - it never asks Spring for
 * them - so a plain @Component job class would have null dependencies. This factory hooks
 * into that instantiation step and runs the freshly-created instance through Spring's
 * autowiring after the fact, so @Autowired fields (e.g. EventService in AutostoneQuartzJob)
 * actually get populated.
 */
public class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory {

    private AutowireCapableBeanFactory beanFactory;

    public void setApplicationContext(ApplicationContext context) {
        this.beanFactory = context.getAutowireCapableBeanFactory();
    }

    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        Object job = super.createJobInstance(bundle);
        beanFactory.autowireBean(job);
        return job;
    }
}
