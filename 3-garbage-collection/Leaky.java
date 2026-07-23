import java.util.*;

public class Leaky {
    // BUG: static field roots this list at the GC root set forever —
    // every element added here is permanently reachable and can never
    // become eligible for garbage collection.
    static final List<byte[]> cache = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        int i = 0;
        while (true) {
            cache.add(new byte[1_000_000]); // 1MB chunk, never released
            i++;
            if (i % 5 == 0) {
                System.out.println("Allocated chunks: " + i);
            }
            Thread.sleep(20);
        }
    }
}
