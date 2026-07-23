import java.util.*;

public class Fixed {
    // FIX: no static accumulation. The list is scoped to each loop
    // iteration, so once a reference goes out of scope with no other
    // live references pointing to it, the object becomes eligible for GC.
    public static void main(String[] args) throws Exception {
        int i = 0;
        while (i < 200) {
            List<byte[]> scratch = new ArrayList<>();
            scratch.add(new byte[1_000_000]); // 1MB chunk
            i++;
            if (i % 5 == 0) {
                System.out.println("Processed chunks: " + i);
            }
            // scratch goes out of scope here -> unreachable -> GC eligible
            Thread.sleep(20);
        }
        System.out.println("DONE_NO_OOM");
    }
}
