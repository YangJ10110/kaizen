import java.util.*;

public class Benchmark {
    public static void main(String[] args) {
        long addStart = System.nanoTime();


//        Set<String> blocklist = new HashSet<>();
        List<String> blocklist = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            blocklist.add("user-" + i);
        }
        long addElapsedMs = (System.nanoTime() - addStart) / 1_000_000;
        System.out.println("ADD ELASPED_MS:"  + addElapsedMs);
        long start = System.nanoTime();
        int hits = 0;
        for (int i = 0; i < 20_000; i++) {
            if (blocklist.contains("user-" + i)) {
                hits++;
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("HITS:" + hits);
        System.out.println("ELAPSED_MS:" + elapsedMs);
        System.out.println(elapsedMs > 200 ? "RESULT:SLOW" : "RESULT:FAST");
    }
}
