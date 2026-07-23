public class StopFlagBug {
    private static boolean stopRequested = false;

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            long spins = 0;
            while (!stopRequested) {
                spins++;
                if (spins % 200_000_000 == 0) {
                    System.out.println("spins=" + spins );
                }
            }
            System.out.println("WORKER_EXIT spins=" + spins);
        });

        worker.start();
        Thread.sleep(1000);
        System.out.println("MAIN_SETTING_STOP_FLAG");
        stopRequested = true;

        worker.join(5000);
        System.out.println(worker.isAlive() ? "RESULT=HANG" : "RESULT=EXITED_CLEANLY");
        System.exit(0);
    }
}
