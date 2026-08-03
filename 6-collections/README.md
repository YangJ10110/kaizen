# Java Collections: Why `contains()` Is Lying to Your Latency Budget

> **Lab Description:** A membership-check service backed by `ArrayList` silently degrades to O(n²) as its blocklist grows, because `List.contains()` is a linear scan. You'll measure the collapse, swap the backing structure to `HashSet`, and prove the fix with wall-clock numbers.

### 1. INSTANT ENVIRONMENT BOOTSTRAP (1-CLICK SETUP)

```bash
# Copy and run this entire block in your terminal to create the lab environment
mkdir -p lab-sandbox && cd lab-sandbox
cat << 'EOF' > Benchmark.java
import java.util.*;

public class Benchmark {
    public static void main(String[] args) {
        List<String> blocklist = new ArrayList<>();
        for (int i = 0; i < 20000; i++) {
            blocklist.add("user-" + i);
        }

        long start = System.nanoTime();
        int hits = 0;
        for (int i = 0; i < 20000; i++) {
            if (blocklist.contains("user-" + i)) {
                hits++;
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("HITS:" + hits);
        System.out.println("ELAPSED_MS:" + elapsedMs);
        System.out.println(elapsedMs > 300 ? "RESULT:SLOW" : "RESULT:FAST");
    }
}
EOF
echo "✅ Lab environment initialized in ./lab-sandbox"
```

### 2. CONTEXT & TOOLING BASELINE

**The Core Mechanism:**

- `ArrayList.contains()` walks every element until it finds a match — O(n) per call, no shortcuts.
- Call that inside a loop of n lookups and you get O(n²) total — the classic "it worked fine in the demo" bug.
- `HashSet.contains()` hashes the key and jumps straight to its bucket — average O(1) per call, regardless of set size.

**Tooling Verification:**

```bash
command -v javac java >/dev/null 2>&1 && echo "✅ Ready" || echo "❌ Missing required CLI tools"
```

### 3. SCENARIO-DRIVEN DOCUMENTATION DRILL

**Scenario:** Your blocklist-check service passed load testing at 2,000 entries. At 20,000 entries in production, p99 latency for a single membership check jumped from <1ms to over 100ms, and CPU on the check pods is pegged.

**Documentation Link:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html

**Target Section:** Class `HashSet<E>` — class description

**The Mission:** Open the docs and find the sentence that states the algorithmic complexity guarantee `HashSet` gives you for `add`, `remove`, `contains`, and `size` — the exact guarantee `ArrayList` does not give you.

**Official Passage:**

> "This class offers constant time performance for the basic operations (add, remove, contains and size), assuming the hash function disperses the elements properly among the buckets."

**Root Cause Breakdown:** `ArrayList` gives no such guarantee for `contains()` — it's documented as a linear search. Swapping the backing collection from `List` to `Set` is what buys back the constant-time guarantee your service needs.

**Architectural Impact:** Engineers default to `ArrayList` because it's the first autocomplete suggestion and preserves insertion order, then reach for `.contains()` without checking what data structure backs it. The bug is invisible below a few thousand elements and catastrophic above it — exactly the shape of a production incident that never shows up in a small-scale demo.

### 4. THE "BEFORE VS. AFTER" EXPERIMENT

**Milestone 1: Observe the Failure State**

- Action:

    ```bash
    javac Benchmark.java && java Benchmark
    ```

- Expected Failure Output: `RESULT:SLOW`

Recorded Terminal Logs:

```
<!-- PASTE YOUR TERMINAL LOGS / OUTPUT HERE -->
```

**Milestone 2: Apply the Remediation**

- Action: In `Benchmark.java`, change the declaration and construction of `blocklist` from `List<String> blocklist = new ArrayList<>();` to `Set<String> blocklist = new HashSet<>();` (no other lines change — `Set` also exposes `contains()`).
- Verification Command:

    ```bash
    javac Benchmark.java && java Benchmark
    ```


Recorded Terminal Logs:

```
<!-- PASTE YOUR VERIFIED TERMINAL LOGS / OUTPUT HERE -->
```

**The Realization:** <!-- Engineer writes 1 sentence on why this change resolved the root cause -->

### 5. FRICTION MAP & GOTCHAS

|Error / Unexpected Symptom|Root Cause|1-Line Recovery Command|
|---|---|---|
|`incompatible types: HashSet<String> cannot be converted to List<String>`|Only the right-hand `new ArrayList<>()` was changed to `new HashSet<>()`; the left-hand declared type is still `List<String>`, which `HashSet` doesn't implement|`sed -i 's/List<String> blocklist/Set<String> blocklist/' Benchmark.java`|
|`RESULT:SLOW` still prints after the edit|Stale `.class` file from before the edit was run instead of a fresh compile|`rm -f Benchmark.class && javac Benchmark.java && java Benchmark`|
|`HITS:` is less than 20000 after switching to `HashSet`|Duplicate insertion assumption broke — shouldn't happen here since all 20000 ids are unique, but signals a logic change beyond the declared fix|`git diff Benchmark.java` (or re-diff against the bootstrap block above) to confirm only the one declaration line changed|

### 6. AI GRADER CONTRACT

- **Target Output Tokens:** Milestone 1 log must contain `RESULT:SLOW` and `HITS:20000`. Milestone 2 log must contain `RESULT:FAST` and `HITS:20000`, with `ELAPSED_MS` in Milestone 2 numerically lower than `ELAPSED_MS` in Milestone 1.
- **Key Realization Validation:** The one-sentence Realization must reference both (a) `HashSet`/hashing giving O(1) average-case `contains()` and (b) `ArrayList`/linear scan giving O(n) `contains()` — a realization naming only one collection fails.
- **Pass Criteria:** Pass requires both milestone logs present with correct tokens, a strictly lower `ELAPSED_MS` in Milestone 2, and a Realization sentence that passes the Key Realization Validation above. Any missing token, reversed timing, or unexplained additional code change (per the friction map's `git diff` check) is a fail.