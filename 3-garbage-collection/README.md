# JVM Garbage Collection: Diagnosing Unbounded Object Retention

> **Lab Description:** Simulate a JVM heap exhaustion caused by unintentionally retained object references, observe GC behavior via `-Xlog:gc`, then fix the retention bug and verify the heap stabilizes.

### 1. INSTANT ENVIRONMENT BOOTSTRAP (1-CLICK SETUP)

```bash
# Copy and run this entire block in your terminal to create the lab environment
mkdir -p lab-sandbox && cd lab-sandbox

cat << 'EOF' > Leaky.java
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
EOF

cat << 'EOF' > Fixed.java
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
EOF

echo "✅ Lab environment initialized in ./lab-sandbox"
```

### 2. CONTEXT & TOOLING BASELINE

**The Core Mechanism:**

- Objects live on the heap; an object becomes eligible for GC only when no live thread can reach it through any chain of references from a GC root (static fields, stack locals, active threads).
- A `static` collection is itself a GC root's target — anything added to it stays reachable for the JVM's entire lifetime, which is the single most common cause of a slow, silent memory leak.
- Constraining the heap (`-Xmx`) and enabling GC logging (`-Xlog:gc`) turns an invisible leak into an observable, reproducible failure.

**Tooling Verification:**

```bash
command -v java >/dev/null 2>&1 && echo "✅ Ready" || echo "❌ Missing required CLI tools"
java -version
```

### 3. SCENARIO-DRIVEN DOCUMENTATION DRILL

**Scenario:** A production service's heap usage climbs steadily under constant load until it throws `OutOfMemoryError: Java heap space`, even though request volume never spiked. GC logs show Full GCs firing more and more frequently while reclaiming almost nothing.

**Documentation Link:** https://docs.oracle.com/javase/9/gctuning/introduction-garbage-collection-tuning.htm

**Target Section:** What Is a Garbage Collector?

**The Mission:** Before running anything, open the doc above and read the "What Is a Garbage Collector?" section. Find the sentence describing the technique HotSpot collectors use to concentrate effort on the areas of the heap most likely to be reclaimable — this is why short-lived garbage is normally cheap to collect, and why a _long-lived_ accidental root (like `Leaky.cache`) defeats that optimization.

**Official Passage:**

> "Use generational scavenging in conjunction with aging to concentrate their efforts on areas in the heap that most likely contain a lot of reclaimable memory areas."

**Root Cause Breakdown:** `Leaky.cache` is a `static` field, so every `byte[]` appended to it is promoted to permanently-reachable status — the collector correctly identifies these objects as live on every cycle, so generational scavenging finds nothing to reclaim in the young generation and the objects get promoted straight to the old generation instead.

**Architectural Impact:** Generational GC assumes most objects die young; a static cache silently violates that assumption by keeping every object alive indefinitely. Engineers hit this by using `static` collections, caches without eviction, or listener lists that are never unregistered.

### 4. THE "BEFORE VS. AFTER" EXPERIMENT

**Milestone 1: [Observe the Failure State]**

- Action: `java -Xmx64m -Xlog:gc Leaky.java`
- Expected Failure Output: `java.lang.OutOfMemoryError: Java heap space`, preceded by increasingly frequent `Pause Full` GC log lines

Recorded Terminal Logs:

```
<!-- PASTE YOUR TERMINAL LOGS / OUTPUT HERE -->
```

**Milestone 2: [Apply the Remediation]**

- Action: `java -Xmx64m -Xlog:gc Fixed.java`
- Verification Command: same command — confirm it prints `DONE_NO_OOM` with no `OutOfMemoryError`

Recorded Terminal Logs:

```
<!-- PASTE YOUR VERIFIED TERMINAL LOGS / OUTPUT HERE -->
```

**The Realization:** <!-- Engineer writes 1 sentence on why this change resolved the root cause -->

### 5. FRICTION MAP & GOTCHAS

|Error / Unexpected Symptom|Root Cause|1-Line Recovery Command|
|---|---|---|
|`Leaky.java` exits instantly with no OOM|Heap cap too generous for allocation rate|`java -Xmx32m -Xlog:gc Leaky.java`|
|`error: could not find or load main class`|Ran `java` from outside `lab-sandbox`|`cd lab-sandbox && java -Xmx64m -Xlog:gc Leaky.java`|
|GC logs scroll too fast to read|Terminal buffer too small|`java -Xmx64m -Xlog:gc Leaky.java > leaky.log 2>&1; tail -50 leaky.log`|
|`Fixed.java` still runs slow near the end|Normal — GC pauses are expected, just non-fatal|N/A, this is correct behavior|

### 6. AI GRADER CONTRACT

- **Target Output Tokens:** Milestone 1 log must contain `OutOfMemoryError: Java heap space` and at least one `Pause Full` line. Milestone 2 log must contain `DONE_NO_OOM` and must NOT contain `OutOfMemoryError`.
- **Key Realization Validation:** The engineer's sentence must reference that removing the `static` field (or otherwise letting `scratch` go out of scope) let objects become unreachable from any GC root, making them eligible for collection instead of being permanently retained.
- **Pass Criteria:** Both milestone log blocks are non-empty and contain their required tokens, AND the Realization sentence correctly names "reachability from a GC root" (or equivalent phrasing) as the reason the fix worked.

