# JVM Runtime Forensics: From `.java` to Deadlock Diagnosis

> **Lab Description:** This lab simulates a hung production JVM process caused by a classic lock-ordering deadlock, requiring the engineer to trace execution from source compilation through ClassLoader resolution to live thread-state inspection. You'll compile broken multi-threaded code, catch it live with `jps`/`jstack`, and validate the fix using thread-dump analysis.

---

### 1. INSTANT ENVIRONMENT BOOTSTRAP (1-CLICK SETUP)

```bash
# Copy and run this entire block in your terminal to create the lab environment
mkdir -p lab-sandbox && cd lab-sandbox

cat << 'EOF' > DeadlockDemo.java
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
            synchronized (LOCK_B) {
                System.out.println("Thread-2 locked B, waiting for A...");
                sleep();
                synchronized (LOCK_A) {
                    System.out.println("Thread-2 got A");
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
EOF

echo "✅ Lab environment initialized in ./lab-sandbox"
```

---

### 2. CONTEXT & TOOLING BASELINE

**The Core Mechanism:**
- **`.java → .class`**: `javac` compiles source into JVM bytecode; no execution happens at this stage.
- **`ClassLoader → JVM`**: at runtime, the Bootstrap/Platform/Application ClassLoaders load `.class` files on-demand (lazy, delegation-first) into the JVM's method area before any thread executes them.
- **Execution ≠ your code's fault**: thread scheduling, monitor locks (`synchronized`), and GC are all JVM-managed — `jstack`/`jcmd`/`jps` expose this hidden runtime state without touching source.

**Tooling Verification:**
```bash
command -v java javac jps jstack jcmd >/dev/null 2>&1 && echo "✅ Ready" || echo "❌ Missing required CLI tools"
```

---

### 3. SCENARIO-DRIVEN DOCUMENTATION DRILL

**Scenario:** A production Java service reports **100% healthy liveness checks but zero request throughput** — no crash, no exception logs, no CPU usage, just silence. Restarting "fixes" it temporarily.

**Documentation Link:** https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/tooldescr016.html

**Target Section:** "The jstack Utility"

**The Mission:** Find the exact mechanism `jstack` uses to expose this "silent hang" without restarting or modifying the running process.

**Official Passage:**
> "A stack trace of all threads can be useful in diagnosing a number of issues, such as deadlocks or hangs."

**Root Cause Breakdown:** The service isn't crashed — two or more threads are alive but permanently blocked on each other's monitors, so the JVM's scheduler never returns control to handle new requests.

**Architectural Impact:** This constraint exists because `synchronized` blocks acquire monitors in the *order the code calls them*, not a JVM-enforced global order — engineers misconfigure this by nesting locks (`A` then `B` in one thread, `B` then `A` in another) without a consistent acquisition sequence.

---

### 4. THE "BEFORE VS. AFTER" EXPERIMENT

**Milestone 1: Observe the Failure State**

**Action:**
```bash
javac DeadlockDemo.java
java DeadlockDemo &
sleep 3
jps -l
jstack $(jps -l | grep DeadlockDemo | awk '{print $1}') | grep -A 10 "Found one Java-level deadlock"
```

**Expected Failure Output:** Process hangs indefinitely (no exit), stdout stalls after "waiting for B/A" lines, `jstack` reports `Found one Java-level deadlock:`.

**Recorded Terminal Logs:**
```plaintext
<!-- PASTE YOUR TERMINAL LOGS / OUTPUT HERE -->
```

---

**Milestone 2: Apply the Remediation**

**Action:** Edit `DeadlockDemo.java` — force **both threads to acquire locks in the same order** (`LOCK_A` then `LOCK_B`) in Thread-2's runnable, eliminating the circular wait.

**Verification Command:**
```bash
kill %1 2>/dev/null
javac DeadlockDemo.java
timeout 5 java DeadlockDemo; echo "EXIT_CODE:$?"
```

**Recorded Terminal Logs:**
```plaintext
<!-- PASTE YOUR VERIFIED TERMINAL LOGS / OUTPUT HERE -->
```

**The Realization:** <!-- Write 1 sentence explaining why this change resolved the root cause -->

---

### 5. FRICTION MAP & GOTCHAS

| Error / Unexpected Symptom | Root Cause | 1-Line Recovery Command |
|---|---|---|
| `jps` shows no `DeadlockDemo` process | Background `&` process exited/crashed before `jps` ran | `java DeadlockDemo & sleep 1 && jps -l` |
| `jstack` prints nothing after "Found one Java-level deadlock" | `grep -A 10` window too small for full trace | `jstack <pid> \| tee dump.txt` then inspect full file |
| Fixed version still hangs | Only one thread's lock order was changed | Verify **both** `synchronized` blocks nest `LOCK_A` before `LOCK_B` |
| `kill %1` says "no such job" | Process wasn't backgrounded in current shell session | `pkill -f DeadlockDemo` |

---

### 6. AI GRADER CONTRACT

**Target Output Tokens:** Milestone 1 log **must** contain the literal string `Found one Java-level deadlock`. Milestone 2 log **must** contain `EXIT_CODE:0` and must **not** contain `deadlock`.

**Key Realization Validation:** Statement must reference **consistent/uniform lock acquisition order** (or equivalent phrasing like "same order," "matching sequence") as the reason the circular wait was broken — generic answers ("I fixed the code") fail.

**Pass Criteria:** All 4 conditions met — (1) deadlock token present in M1, (2) `EXIT_CODE:0` present in M2, (3) no deadlock token in M2, (4) realization statement correctly identifies lock-ordering as root cause. 4/4 = PASS, <4 = FAIL with specific missing-token feedback.
