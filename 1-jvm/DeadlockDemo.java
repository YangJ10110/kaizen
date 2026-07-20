public class DeadlockDemo {
    private static final Object LOCK_A = new Object();
    private static final Object LOCK_B = new Object();

    public static void main(String[] args) {
        Thread t1 = new Thread(() -> {
            synchronized (LOCK_A) {
                System.out.println("Thread-1 locked A, waiting for B...");
                sleep();
                synchronized (LOCK_B) {
                    System.out.println("Thread-1 got B");
                }
            }
        }, "Thread-1");

        Thread t2 = new Thread(() -> {
            synchronized (LOCK_A) {
                System.out.println("Thread-2 locked A waiting for B...");
                sleep();
                synchronized (LOCK_B) {
                    System.out.println("Thread-2 got B");
                }
            }
        }, "Thread-2");

        t1.start();
        t2.start();
    }

    private static void sleep() {
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
    }
}
