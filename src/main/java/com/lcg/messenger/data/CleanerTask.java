package com.lcg.messenger.data;

import com.lcg.messenger.async.DemoAsyncService;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.sql.Timestamp;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

public class CleanerTask implements org.quartz.Job {
    public CleanerTask() {
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        System.out.println("------------------------------------------cleanup started---------------------------------------");
        System.out.println(new Timestamp(System.currentTimeMillis()));
        DemoAsyncService.persistentResponse.cleanUp();
        System.out.println("------------------------------------------cleanup finished--------------------------------------");
    }

    public static void startTask() {
        System.out.println("startTask() @CleanerTask");
        try {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            // define the job and tie it to our MyJob class
            JobDetail job = newJob(CleanerTask.class).withIdentity("cleaningJob", "cleanerGroup").build();
            // Trigger the job to run now, and then repeat every hour
            Trigger trigger = newTrigger().withIdentity("cleaningTrigger", "cleanerGroup").startNow().withSchedule(simpleSchedule().withIntervalInSeconds(3600).repeatForever()).build();
            // Tell quartz to schedule the job using our trigger
            scheduler.scheduleJob(job, trigger);
            scheduler.start();
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }
}
