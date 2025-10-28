package basic;

import java.util.concurrent.Callable;

public class MyTask implements Callable<String> {
    @Override
    public String call() throws Exception {
        System.out.println(Thread.currentThread().getName() + " is running");
        try {
            while(!Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            System.out.println(Thread.currentThread().getName() + " is interrupted");
        }
        return Thread.currentThread().getName() + " is stopped";
    }
}
