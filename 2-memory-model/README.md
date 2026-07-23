
# Java Memory Model: Visibility, Ordering & the volatile Fix

> **Lab Description:** Simulates a classic stop-flag race condition where a worker thread never observes a write from the main thread due to missing happens-before ordering. You will reproduce the infinite-loop hang, apply `volatile`, and validate the fix with JIT-safe verification.

---

### 1. INSTANT ENVIRONMENT BOOTSTRAP (1-CLICK SETUP)

```bash
# Copy and run this entire block in your terminal to create the lab environment
mkdir -p lab-sandbox && cd lab-sandbox

cat << 'EOF' > StopFlagBug.java
public class StopFlagBug {
    // BUG: not volatile — no happens-before guarantee across threads
    private static boolean stopRequested = false;

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            long spins = 0;
            while (!stopRequested) {
                spins++; // busy-wait, may be cached in a register forever
            }
            System.out.println("WORKER_EXIT spins=" + spins);
        });

        worker.start();
        Thread.sleep(1000);
        System.out.println("MAIN_SETTING_STOP_FLAG");
        stopRequested = true;

        worker.join(5000);
        if (worker.isAlive()) {
            System.out.println("RESULT=HANG worker_thread_state=" + worker.getState());
        } else {
            System.out.println("RESULT=EXITED_CLEANLY");
        }
        System.exit(0);
    }
}
EOF

echo "✅ Lab environment initialized in ./lab-sandbox"
```

---

### 2. CONTEXT & TOOLING BASELINE

**The Core Mechanism:**

- **Visibility ≠ Atomicity.** A write from Thread A is not guaranteed to ever become visible to Thread B without a **happens-before** edge — the JIT is legally allowed to cache `stopRequested` in a CPU register for the lifetime of the loop.
- **`volatile`** establishes a happens-before edge on that single field: every write happens-before every subsequent read (JLS §17.4.5). It does **not** make compound operations (`x++`) atomic.
- **`synchronized`** gives you both mutual exclusion _and_ visibility (via monitor entry/exit), at the cost of blocking. `Atomic*`classes (e.g. `AtomicInteger`) give you visibility _and_ lock-free atomic read-modify-write via CAS.

**Tooling Verification:**

```bash
command -v java javac >/dev/null 2>&1 && echo "✅ Ready" || echo "❌ Missing required CLI tools"
```

---

### 3. SCENARIO-DRIVEN DOCUMENTATION DRILL

**Scenario:** A production worker thread polls a `boolean shutdown` flag set by a shutdown hook on another thread. In dev (low-core-count VM, no JIT warmup) it exits fine. In prod (high core count, long-running JIT-optimized loop) the thread **never terminates** and the process hangs on shutdown.

**Documentation Link:** https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html

**Target Section:** `17.4.5 Happens-before Order`

**The Mission:** Find the single rule in this section that explains why a plain `boolean` write from one thread may never be observed by another thread's loop condition, and why marking the field `volatile` fixes it deterministically.

**Official Passage:**

> <cite index="2-1">A write to a volatile field happens-before every subsequent read of that field.</cite>

**Root Cause Breakdown:** Without this rule, `stopRequested` is a **non-volatile** field, so the JMM gives no ordering guarantee between the main thread's write and the worker thread's read — the compiler is free to hoist the read out of the loop entirely.

**Architectural Impact:** This constraint exists because giving every field automatic cross-thread visibility would kill single-threaded performance (no register caching, no reordering). Engineers misconfigure this by treating `volatile` as a general-purpose lock replacement — it fixes visibility for a _single_ variable, not compound state transitions or multi-field invariants.

---

### 4. THE "BEFORE VS. AFTER" EXPERIMENT

**Milestone 1: Observe the Failure State**

**Action:**

```bash
javac StopFlagBug.java && timeout 8 java StopFlagBug; echo "EXIT_CODE=$?"
```

**Expected Failure Output:** `RESULT=HANG` (or the process is killed by `timeout`, `EXIT_CODE=124`) — the worker thread never sees `stopRequested = true`.

**Recorded Terminal Logs:**

```
<!-- PASTE YOUR TERMINAL LOGS / OUTPUT HERE -->
```

---

**Milestone 2: Apply the Remediation**

**Action:** Edit `StopFlagBug.java` — change the field declaration:

```bash
sed -i.bak 's/private static boolean stopRequested = false;/private static volatile boolean stopRequested = false;/' StopFlagBug.java
```

**Verification Command:**

```bash
javac StopFlagBug.java && timeout 8 java StopFlagBug; echo "EXIT_CODE=$?"
```

**Recorded Terminal Logs:**

```
<!-- PASTE YOUR VERIFIED TERMINAL LOGS / OUTPUT HERE -->
```

**The Realization:**

<!-- Write 1 sentence explaining why this change resolved the root cause -->

---

### 5. FRICTION MAP & GOTCHAS

|Error / Unexpected Symptom|Root Cause|1-Line Recovery Command|
|---|---|---|
|`RESULT=EXITED_CLEANLY`even _before_ adding `volatile`|JIT hadn't warmed up / ran in interpreted mode, so no register caching occurred yet — false negative|Re-run 3–5x: `for i in 1 2 3; do timeout 8 java StopFlagBug; done`|
|`javac` fails with "cannot find symbol"|Ran command outside `lab-sandbox/` directory|`cd lab-sandbox && javac StopFlagBug.java`|
|Fix doesn't take effect|`sed` created `.bak` but you re-ran on the backup, or old `.class`is stale|`rm -f *.class *.bak && javac StopFlagBug.java`|
|Want to restart from scratch|Environment state is polluted|`cd .. && rm -rf lab-sandbox` then re-run Section 1|

---

### 6. AI GRADER CONTRACT

**Target Output Tokens:** Milestone 1 log **must** contain `RESULT=HANG` or `EXIT_CODE=124`. Milestone 2 log **must** contain `RESULT=EXITED_CLEANLY` and `EXIT_CODE=0`, plus `MAIN_SETTING_STOP_FLAG` and `WORKER_EXIT` present in that order.

**Key Realization Validation:** The 1-sentence realization **must** reference `volatile` (or `happens-before`) **and** explicitly name **visibility** (not "speed" or "locking") as the mechanism fixed. Reject answers that only say "it made the code faster" or "it added a lock."

**Pass Criteria:** PASS only if (a) Milestone 1 shows a hang/timeout signature, (b) Milestone 2 shows a clean exit signature, and (c) the realization sentence correctly names visibility/happens-before as the cause. Any missing token = FAIL, resubmit.
