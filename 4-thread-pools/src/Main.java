import java.util.*;
import java.util.concurrent.*;

public class Main {

    static final int TASKS = 20;

    public static void main(String[] args) throws Exception {

        System.out.println("=== BEFORE ===");

        Set<String> before = ConcurrentHashMap.newKeySet();

        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < TASKS; i++) {
            Thread t = new Thread(() -> {
                before.add(Thread.currentThread().getName());
                sleep();
            });
            threads.add(t);
            t.start();
        }

        for (Thread t : threads) t.join();

        System.out.println("Unique threads: " + before.size());

        System.out.println("\n=== AFTER (BROKEN) ===");

        // TODO:
        // Replace this with an appropriate ExecutorService.
        ExecutorService pool = Executors.newFixedThreadPool(4);

        Set<String> after = ConcurrentHashMap.newKeySet();

        CountDownLatch latch = new CountDownLatch(TASKS);

        for (int i = 0; i < TASKS; i++) {
            pool.submit(() -> {
                after.add(Thread.currentThread().getName());
                sleep();
                latch.countDown();
            });

        }

        latch.await();

        pool.shutdown();


        System.out.println("Unique pool threads: " + after.size());

        if(after.size() >= TASKS){
            System.out.println("❌ Pool configuration ineffective.");
        }else{
            System.out.println("✅ Thread reuse observed.");
        }
    }

    static void sleep(){
        try{
            Thread.sleep(100);
        }catch(Exception ignored){}
    }
}
