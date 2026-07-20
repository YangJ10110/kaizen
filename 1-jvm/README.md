# JVM Runtime Forensics: From `.java` to Deadlock Diagnosis

> **Lab Description:** This lab simulates a hung production JVM process caused by a classic lock-ordering deadlock, requiring the engineer to trace execution from source compilation through ClassLoader resolution to live thread-state inspection. You'll compile broken multi-threaded code, catch it live with `jps`/`jstack`, and validate the fix using thread-dump analysis.

---

### 1. INSTANT ENVIRONMENT BOOTSTRAP (1-CLICK SETUP)

```bash
# Copy and run this entire block in your terminal to create the lab environment


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

- **`.java → .class`**: `javac` compiles source into JVM bytecode; no execution happens at this stage.
- **`ClassLoader → JVM`**: at runtime, the Bootstrap/Platform/Application ClassLoaders load `.class` files on-demand (lazy, delegation-first) into the JVM's method area before any thread executes them.
- **Execution ≠ your code's fault**: thread scheduling, monitor locks (`synchronized`), and GC are all JVM-managed — `jstack`/`jcmd`/`jps` expose this hidden runtime state without touching source.

**Tooling Verification:**

```bash
command -v java javac jps jstack jcmd >/dev/null 2>&1 && echo "✅ Ready" || echo "❌ Missing required CLI tools"
```

---

### 3. SCENARIO-DRIVEN DOCUMENTATION DRILL

**Scenario:** A production Java service reports **100% healthy liveness checks but zero request throughput** — no crash, no exception logs, no CPU usage, just silence. Restarting "fixes" it temporarily.

**Documentation Link:** https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/tooldescr016.html

**Target Section:** "The jstack Utility"

**The Mission:** Find the exact mechanism `jstack` uses to expose this "silent hang" without restarting or modifying the running process.

**Official Passage:**

> "A stack trace of all threads can be useful in diagnosing a number of issues, such as deadlocks or hangs."

**Root Cause Breakdown:** The service isn't crashed — two or more threads are alive but permanently blocked on each other's monitors, so the JVM's scheduler never returns control to handle new requests.

**Architectural Impact:** This constraint exists because `synchronized` blocks acquire monitors in the _order the code calls them_, not a JVM-enforced global order — engineers misconfigure this by nesting locks (`A` then `B` in one thread, `B` then `A`in another) without a consistent acquisition sequence.

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

**Expected Failure Output:** Process hangs indefinitely (no exit), stdout stalls after "waiting for B/A" lines, `jstack` reports `Found one Java-level deadlock:`.

**Recorded Terminal Logs:**

```plaintext
java DeadlockDemo &
sleep 3
jps -l
jstack $(jps -l | grep DeadlockDemo | awk '{print $1}') | grep -A 10 "Found one Java-level deadlock"
[1] 32920
Thread-1 locked A, waiting for B...
Thread-2 locked B, waiting for A...
32928 jdk.jcmd/sun.tools.jps.Jps
32920 DeadlockDemo
Found one Java-level deadlock:
=============================
"Thread-1":
  waiting to lock monitor 0x0000000c80cbcfc0 (object 0x0000000787f177f0, a java.lang.Object),
  which is held by "Thread-2"

"Thread-2":
  waiting to lock monitor 0x0000000c80cbd0a0 (object 0x0000000787f177e0, a java.lang.Object),
  which is held by "Thread-1"

Java stack information for the threads listed above:
```

Realization:
- `jps` is used to find running java applications and lists the Process IDs (PIDs)
- "instrumented JVMs on the target system" just means running java applications
- `jstack` attaches to the specified process or a PID (output of jps), and prints the stack traces of all threads that are attached to the virtual machine, and detects deadlocks.
- and so because both threads are holding the object they are next waiting for (A to B and B to A), they are forever waiting for the object to be available before they can proceed on the next `synchronized` process
- this was detected using jstack (pid) 
- ```
  Found one Java-level deadlock:
=============================
"Thread-1":
  waiting to lock monitor 0x0000000c470bd0a0 (object 0x0000000787f177f0, a java.lang.Object),
  which is held by "Thread-2"

"Thread-2":
  waiting to lock monitor 0x0000000c470bd180 (object 0x0000000787f177e0, a java.lang.Object),
  which is held by "Thread-1"
  ```
- and furthered strengthen by:
```
Java stack information for the threads listed above:
===================================================
"Thread-1":
        at DeadlockDemo.lambda$main$0(DeadlockDemo.java:11)
        - waiting to lock <0x0000000787f177f0> (a java.lang.Object)
        - locked <0x0000000787f177e0> (a java.lang.Object)
        at DeadlockDemo$$Lambda/0x00007ffe01000a18.run(Unknown Source)
        at java.lang.Thread.runWith(java.base@24.0.1/Thread.java:1460)
        at java.lang.Thread.run(java.base@24.0.1/Thread.java:1447)
"Thread-2":
        at DeadlockDemo.lambda$main$1(DeadlockDemo.java:21)
        - waiting to lock <0x0000000787f177e0> (a java.lang.Object)
        - locked <0x0000000787f177f0> (a java.lang.Object)
        at DeadlockDemo$$Lambda/0x00007ffe01000c48.run(Unknown Source)
        at java.lang.Thread.runWith(java.base@24.0.1/Thread.java:1460)
        at java.lang.Thread.run(java.base@24.0.1/Thread.java:1447)

Found 1 deadlock.
```

- at Thread 1: it locked `<0x0000000787f177e0>` and waiting to lock `<0x0000000787f177f0>`
- at Thread 2: it locked `<0x0000000787f177f0>` (which is thread 1 is waiting for) and is waiting to lock `<0x0000000787f177e0>` which is thread 1 locked to.

---

**Milestone 2: Apply the Remediation**

**Action:** Edit `DeadlockDemo.java` — force **both threads to acquire locks in the same order** (`LOCK_A` then `LOCK_B`) in Thread-2's runnable, eliminating the circular wait.

**Verification Command:**

```bash
kill %1 2>/dev/null
javac DeadlockDemo.java
timeout 5 java DeadlockDemo; echo "EXIT_CODE:$?"
```

**Recorded Terminal Logs (After applying the fix):**

```plaintext
 ~/Projects/kaizen/1-jvm  1_jvm ?1 ------------------------- INT  15m 49s  15:52:25
> nvim DeadlockDemo.java

 ~/Projects/kaizen/1-jvm  1_jvm ?1 ---------------------------------- 32s  15:53:04
> javac DeadlockDemo.java

 ~/Projects/kaizen/1-jvm  1_jvm ?1 --------------------------------------- 15:53:11
 ~/Projects/kaizen/1-jvm  1_jvm ?1 --------------------------------------- 15:54:44
> java DeadlockDemo; echo "EXIT_CODE:$?"
Thread-1 locked A, waiting for B...
Thread-1 got B
Thread-2 locked A waiting for B...
Thread-2 got B
EXIT_CODE:0
```

**The Fix**

```java
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
```

**The Realization:** The deadlock occurred because the sequence of the synchronized access caused the required next object (T1: A, T2: B) to be locked before it can be accessed, explained in my earlier realization (added), thus following the instruction of using the same order for both threads enabled it to be sequential, so both threads can actually finish using the object first before locking it, making it free for the next thread. 

---

### 5. FRICTION MAP & GOTCHAS

|Error / Unexpected Symptom|Root Cause|1-Line Recovery Command|
|---|---|---|
|`jps` shows no `DeadlockDemo` process|Background `&` process exited/crashed before `jps` ran|`java DeadlockDemo & sleep 1 && jps -l`|
|`jstack` prints nothing after "Found one Java-level deadlock"|`grep -A 10` window too small for full trace|`jstack <pid> \| tee dump.txt` then inspect full file|
|Fixed version still hangs|Only one thread's lock order was changed|Verify **both** `synchronized` blocks nest `LOCK_A` before `LOCK_B`|
|`kill %1` says "no such job"|Process wasn't backgrounded in current shell session|`pkill -f DeadlockDemo`|

---

### 6. AI GRADER CONTRACT

**Target Output Tokens:** Milestone 1 log **must** contain the literal string `Found one Java-level deadlock`. Milestone 2 log **must**contain `EXIT_CODE:0` and must **not** contain `deadlock`.

**Key Realization Validation:** Statement must reference **consistent/uniform lock acquisition order** (or equivalent phrasing like "same order," "matching sequence") as the reason the circular wait was broken — generic answers ("I fixed the code") fail.

**Pass Criteria:** All 4 conditions met — (1) deadlock token present in M1, (2) `EXIT_CODE:0` present in M2, (3) no deadlock token in M2, (4) realization statement correctly identifies lock-ordering as root cause. 4/4 = PASS, <4 = FAIL with specific missing-token feedback.

---

### 7. BONUS MILESTONE — CLASSLOADER COLLISION

**Milestone 3: Observe a ClassLoader Collision**

**Context:** The same class (`PluginTask`) exists in two directories — `./out` (on the default classpath) and `./plugins` (loaded by a custom `URLClassLoader`). A custom ClassLoader with no parent delegation loads `PluginTask` from `./plugins`, while the compiled code references `PluginTask` which the Application ClassLoader loads from `./out`. To the JVM, these are two completely unrelated types.

**Action:**

```bash
cd 1-jvm
./run.sh
java -cp out ClassLoaderDemo &
sleep 1
```

**Expected Output:**

```
Class collision detected: class PluginTask cannot be cast to class PluginTask (PluginTask is in unnamed module of loader java.net.URLClassLoader @<hash>; PluginTask is in unnamed module of loader 'app')
```

**Inspection with jcmd:**

```bash
jps -l | grep ClassLoaderDemo
jcmd <pid> VM.classloaders
```

**Expected Inspection Output:**

```
<pid>:
+-- <bootstrap>
      |     
      +-- "platform", jdk.internal.loader.ClassLoaders$PlatformClassLoader
      |     |     
      |     +-- "app", jdk.internal.loader.ClassLoaders$AppClassLoader
      |                 
      +-- java.net.URLClassLoader
```

The custom `java.net.URLClassLoader` sits alongside the built-in loaders. It and the `'app'` loader each hold their own copy of `PluginTask` — two classes with the same name but treated as unrelated types by the JVM.

**Recorded Terminal Logs:**

```plaintext
java -cp out ClassLoaderDemo
Class collision detected: class PluginTask cannot be cast to class PluginTask (PluginTask is in unnamed module of loader java.net.URLClassLoader @2b2fa4f7; PluginTask is in unnamed module of loader 'app')
```

**Realization:**

- tried deleting the `.class` file in the /out or default directory 
```
rm out/PluginTask.class
```
- then re-running the java command for the (same java class for classloaderdemo) and got a `java.lang.NoClassDefFoundError: PluginTask` error, next thing i tried is re-running the javac command in the 1_jvm directory, and not out or plugin like `run.sh`, it generated the class file for PluginTask and ClassLoaderDemo in the `1_jvm` directory and still has a collission, next thing i tried is to delete the classloader and plugintask classes on /out (so both /plugins and /out is empty) running the javac and java still fails as it now cant find `PluginTask`. So theres a dillemma on how to fix this. if the goal is to load the classes from the /plugins class, i need to find a way for the generated classes to be in plugins, so in a way they dont interfere or collide, how do i do that?
- i have a missing context, i know when javac is used, it generates a class because of this reference `private static PluginTask unused;` of PluginTask.java, but on its generation, it generates it on the same directory, not the plugins, maybe the fix is placing it on plugins?
- `mv PluginTask.java ./plugins`
- new issue:
- ```
  javac ClassLoaderDemo.java
ClassLoaderDemo.java:6: error: cannot find symbol
    private static PluginTask unused;
                   ^
  symbol:   class PluginTask
  location: class ClassLoaderDemo
ClassLoaderDemo.java:17: error: cannot find symbol
            PluginTask task = (PluginTask) instance;
            ^
  symbol:   class PluginTask
  location: class ClassLoaderDemo
ClassLoaderDemo.java:17: error: cannot find symbol
            PluginTask task = (PluginTask) instance;
                               ^
  symbol:   class PluginTask
  location: class ClassLoaderDemo
3 errors
  ```
- now it cant find it, so how does javac finds it dependency along its directory?
- do i need to turn plugintask into a class beforehand?
```
 ~/Projects/kaizen/1-jvm  1_jvm ?1 --------------------------------------- 16:38:44
> cd plugins

 ~/Projects/kaizen/1-jvm/plugins  1_jvm ?1 ------------------------------- 16:39:37
> javac PluginTask.java
```
- same error
- im missing fundamental knowledge to implement a fix
- Based from the documentation 

>"The `ClassLoader` class uses a delegation model to search for classes and resources. Each instance of `ClassLoader` has an associated parent class loader. When requested to find a class or resource, a `ClassLoader` instance will usually delegate the search for the class or resource to its parent class loader before attempting to find the class or resource itself."

- meaning, the parent of $AppClassLoader, which is $PlatformClassLoader, is tasked to find the resource PluginTask
	- and because the pwd of the ClassLoaderDemo is in /out ` private static PluginTask unused`, it will first find it there and it exists, and when the custom class loader loads the PluginTask class from ./plugins and used on `PluginTask task = (PluginTask) instance;` theres two existing types 
		- unused
		- Object instance from 
```java
URLClassLoader pluginLoader = new URLClassLoader(
            new URL[]{new File("./plugins").toURI().toURL()},
            null
        );
        Class<?> clazz = Class.forName("PluginTask", true, pluginLoader);
        Object instance = clazz.getDeclaredConstructor().newInstance();
```

- and when it was referenced, it caused a classCastException
```java
try {
            PluginTask task = (PluginTask) instance;
            task.run();
        } catch (ClassCastException e) {
            System.out.println("Class collision detected: " + e.getMessage());
        }

```
- explicit casting in this case to convert the PluginTask from generated class to one with a plugin caused an error as they are not subclasses of each other
- bootstrap class loader is just the vm's class loader and typically has no parent and represented as null
- platform class loader - this is the parent that a class loader instance can use, and here all platform classes are visible.
	- java se platform api
	- implementation classes of platform apis
	-  JDK-specific run-time classes that are defined by the platform class loader or its ancestors
	- To allow for upgrading/overriding of modules defined to the platform class loader (like java se apis) then the platform class loader may have to delegate to other class loaders, the application class loader for example.
	- Interpretation: so if we want to override a module we need to delegate it to be loaded to application class loader or a custom class loader?
- Application class order:typically used to define classes on the application class path, module path, and JDK-specific tools.


- actual main cause is when creating the pluginloader the assigned parent was null, so it was on the level as the child of bootstrap class loader
```java
URLClassLoader pluginLoader = new URLClassLoader( 
    new URL[]{new File("./plugins").toURI().toURL()},  
    null);
```

```text
jcmd 46015 VM.classloaders
46015:
+-- <bootstrap>
      |
      +-- "platform", jdk.internal.loader.ClassLoaders$PlatformClassLoader
      |     |
      |     +-- "app", jdk.internal.loader.ClassLoaders$AppClassLoader
      |
      +-- java.net.URLClassLoader
```

Because `null` severed delegation to `AppClassLoader`, `pluginLoader` loaded `PluginTask` directly from `./plugins`. Meanwhile, `AppClassLoader` loaded `PluginTask` from `./out` because `ClassLoaderDemo` referenced `PluginTask` in source code.

To the JVM, `(AppClassLoader, "PluginTask")` and `(URLClassLoader, "PluginTask")` are two completely unrelated types. Casting `(PluginTask) instance` threw a `ClassCastException`.

```java
URLClassLoader pluginLoader = new URLClassLoader(  
    new URL[]{new File("./plugins").toURI().toURL()},  
        ClassLoaderDemo.class.getClassLoader()  
);
```

changing it to: `ClassLoaderDemo.class.getClassLoader()` will resolve the issue:

it succeeded because of **Parent-First Delegation (Class Shadowing)**:

1. When `Class.forName("PluginTask", true, pluginLoader)` ran, `pluginLoader` followed default Java delegation and asked its parent (`AppClassLoader`) first: _"Do you have `PluginTask`?"_
2. `AppClassLoader` checked its classpath (`./out`), found `./out/PluginTask.class`, and loaded it.
3. `pluginLoader` accepted the parent's class and **completely skipped `./plugins/PluginTask.class`**.
4. `./plugins/PluginTask.class` was never loaded into memory at all—it was shadowed by `./out/PluginTask.class`.

Because `instance` was instantiated from the class `AppClassLoader` provided, both `ClassLoaderDemo` and `instance` were using the exact same class type from `./out`. The cast succeeded, but the code in `./plugins` was totally ignored.

```
> jcmd 46064 VM.classloaders
46064:
+-- <bootstrap>
      |
      +-- "platform", jdk.internal.loader.ClassLoaders$PlatformClassLoader
            |
            +-- "app", jdk.internal.loader.ClassLoaders$AppClassLoader
                  |
                  +-- java.net.URLClassLoader
```

Final Realization:
- theres three hierarchies of classloaders
	- bootstrap
	- platform
	- application
- when you create a class loader and want to reference it into a customized way (e.g. plugin) the custom class loader must be parent to one of the AppClassLoader so it could be casted

Because `instance` was instantiated from the class `AppClassLoader` provided, both `ClassLoaderDemo` and `instance` were using the exact same class type from `./out`. The cast succeeded, but the code in `./plugins` was totally ignored.


Converation:

https://gemini.google.com/app/c884943721fae3d9
mac m1 laptop: opencode -s ses_081b79c15ffeTJgSko5gD2Agax 


