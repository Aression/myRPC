package basic;

import java.util.concurrent.*;

public class CustomThreadPoolExample {
    public static void main(String[] args) {
        // 设置自定义参数
        int corePoolSize = 10;
        int maximumPoolSize = 10;
        long keepAliveTime = 10;
        TimeUnit unit = TimeUnit.SECONDS;
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(10);
        
        // 自定义线程工厂，用来命名线程
        ThreadFactory threadFactory = new ThreadFactory() {
            private int counter = 0;
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "MyCustomThread-" + counter++);
            }
        };

        // 使用默认的拒绝策略
        RejectedExecutionHandler handler = new ThreadPoolExecutor.AbortPolicy();

        // 实例化 ThreadPoolExecutor
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                threadFactory,
                handler
        );

        // 创建并提交10个任务
        Future<String>[] futureTasks = new Future[10];
        for (int i = 0; i < 10; i++) {
            futureTasks[i] = executor.submit(new MyTask());
        }

        try {
            // 等待所有任务开始执行
            Thread.sleep(2000);

            // 批量取消任务
            System.out.println("\n等待 2 秒后，尝试取消所有任务...");
            for (Future<String> future : futureTasks) {
                // future.cancel(true) 会向正在执行任务的线程发送中断信号
                future.cancel(true);
            }

            // 再次等待一段时间，让任务有时间响应中断
            Thread.sleep(2000);

            // 获取每个任务的执行结果
            System.out.println("\n获取任务结果...");
            for (int i = 0; i < 10; i++) {
                try {
                    String result = futureTasks[i].get();
                    System.out.println("任务 " + i + " 结果: " + result);
                } catch (CancellationException e) {
                    System.out.println("任务 " + i + " 已被取消，无法获取结果。");
                }
            }
            
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            // 关闭线程池
            executor.shutdownNow();
            System.out.println("\n线程池已关闭。");
        }
    }
}