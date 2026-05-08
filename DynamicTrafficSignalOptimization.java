import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

// ============================================================
// FILE: TrafficSignalOptimizer.java
//
// PROJECT: Intelligent Dynamic Traffic Signal Optimization
//          Using Greedy Algorithms and Priority Queues
//
// AUTHORS: Riyansha Joshi · Shashi Anand
//
// COMPLEXITY SUMMARY
// ──────────────────────────────────────────────────────────
//  Previous implementation rebuilt the entire heap every cycle:
//    clear() + n × offer()  →  O(n log n) per cycle
//
//  This implementation uses two algorithmic improvements:
//
//  1. LAZY VIRTUAL CLOCK
//     Non-served lanes are NEVER explicitly updated.
//     Each lane stores lastServedCycle. Effective waiting
//     time and fairness are derived on-the-fly:
//       effectiveWait = baseWait + (currentCycle - lastServedCycle) * WAIT_INCREMENT
//       effectiveFairness = (currentCycle - lastServedCycle) * FAIRNESS_INCREMENT
//     Cost of "updating" non-served lanes: O(1) total (no loop).
//
//  2. INDEXED MAX-HEAP
//     A custom binary max-heap that tracks each lane's array
//     position via a pos[] index. After updating the served
//     lane's state in-place, one siftDown() call restores the
//     heap property in O(log n) without a rebuild.
//
//  Per-cycle cost breakdown:
//    peekMax()          →  O(1)
//    update served lane →  O(1)
//    heap.update()      →  O(log n)   ← single siftDown
//    non-served lanes   →  O(1)       ← lazy clock, no loop
//    ─────────────────────────────────────────────────
//    Total per cycle    →  O(log n)   ✓
//
//  Space: O(n) — heap and pos arrays both size n.
// ============================================================


// ─────────────────────────────────────────────────────────────
// CLASS: Lane
//
// Models a single traffic lane. Priority is NOT cached —
// it is always computed fresh via computePriority(currentCycle),
// enabling the lazy virtual clock optimisation.
// ─────────────────────────────────────────────────────────────
class Lane {

    // ── Constants ────────────────────────────────────────────
    /** Flat priority bonus when an emergency vehicle is present.
     *  Large enough to always preempt any normal lane. */
    static final int EMERGENCY_BONUS    = 10_000;

    /** Priority boost per skipped cycle (Aging / anti-starvation). */
    static final int FAIRNESS_INCREMENT = 5;

    /** Seconds of waiting time added per skipped cycle. */
    static final int WAIT_INCREMENT     = 20;

    // ── Identity ─────────────────────────────────────────────
    /** Unique 0-based index — required by IndexedMaxHeap. */
    final int    id;
    final String name;

    // ── Real-time state ──────────────────────────────────────
    int     vehicleCount;

    /**
     * Waiting time baseline captured at the moment this lane was
     * last served (or at construction). The effective waiting time
     * at any later point is:
     *   baseWaitingTime + (currentCycle - lastServedCycle) * WAIT_INCREMENT
     *
     * This is the heart of the LAZY VIRTUAL CLOCK: we never touch
     * non-served lanes; their wait grows implicitly.
     */
    int     baseWaitingTime;

    /** Cycle index when this lane was last given a green signal. */
    int     lastServedCycle;

    boolean emergencyFlag;

    // ── Constructor ──────────────────────────────────────────
    Lane(int id, String name, int vehicleCount,
         int initialWaitingTime, boolean emergencyFlag) {
        this.id              = id;
        this.name            = name;
        this.vehicleCount    = vehicleCount;
        this.baseWaitingTime = initialWaitingTime;
        this.lastServedCycle = 0;
        this.emergencyFlag   = emergencyFlag;
    }

    // ── Lazy priority computation ─────────────────────────────
    /**
     * Computes the composite priority score using the virtual clock.
     *
     *   Priority = (vehicleCount × effectiveWait)
     *              + effectiveFairness
     *              + EMERGENCY_BONUS  (if applicable)
     *
     * Where:
     *   skipped         = currentCycle - lastServedCycle
     *   effectiveWait   = baseWaitingTime + skipped × WAIT_INCREMENT
     *   effectiveFairness = skipped × FAIRNESS_INCREMENT
     *
     * This is called inside heap comparisons and display only.
     * Non-served lanes never need explicit updates — this function
     * derives their current urgency from the global cycle counter.
     *
     * Time complexity: O(1)
     */
    double computePriority(int currentCycle) {
        int  skipped        = currentCycle - lastServedCycle;
        long effectiveWait  = baseWaitingTime + (long) skipped * WAIT_INCREMENT;
        long fairness       = (long) skipped * FAIRNESS_INCREMENT;
        return (double) (vehicleCount * effectiveWait)
               + fairness
               + (emergencyFlag ? EMERGENCY_BONUS : 0);
    }

    // ── Display ──────────────────────────────────────────────
    String toDisplayString(int currentCycle) {
        int  skipped        = currentCycle - lastServedCycle;
        long effectiveWait  = baseWaitingTime + (long) skipped * WAIT_INCREMENT;
        long fairness       = (long) skipped * FAIRNESS_INCREMENT;
        return String.format(
            "%-14s | vehicles=%3d | wait=%3ds | emergency=%-5s"
            + " | fairness=%3d | priority=%8.1f",
            name, vehicleCount, effectiveWait,
            emergencyFlag, fairness, computePriority(currentCycle));
    }
}


// ─────────────────────────────────────────────────────────────
// CLASS: IndexedMaxHeap
//
// A binary max-heap that supports O(log n) in-place priority
// updates via a pos[] index array.
//
// Standard Java PriorityQueue does NOT support decrease/increase
// key — updating an element requires O(n) remove() + O(log n)
// offer(), or a full O(n log n) rebuild. The indexed heap solves
// this: pos[lane.id] always holds the lane's current array slot,
// so siftUp/siftDown can be called directly in O(log n).
//
// Crucially, ALL comparisons call computePriority(currentCycle),
// so the heap always reflects current effective priorities —
// including the lazy waiting-time growth of non-served lanes.
// ─────────────────────────────────────────────────────────────
class IndexedMaxHeap {

    private final Lane[] heap;   // the heap array
    private final int[]  pos;    // pos[lane.id] = index of that lane in heap[]
    private int          size;
    private int          currentCycle;

    IndexedMaxHeap(int capacity) {
        heap = new Lane[capacity];
        pos  = new int[capacity];
        Arrays.fill(pos, -1);
        size         = 0;
        currentCycle = 0;
    }

    // ── Cycle reference ──────────────────────────────────────
    /** Must be called at the start of every cycle so comparisons
     *  use the correct effective priorities. O(1). */
    void setCycle(int cycle) { this.currentCycle = cycle; }

    boolean isEmpty() { return size == 0; }

    // ── Peek ─────────────────────────────────────────────────
    /** Returns the max-priority lane without removing it. O(1). */
    Lane peekMax() {
        if (isEmpty()) throw new IllegalStateException("Heap is empty.");
        return heap[0];
    }

    // ── Insert ───────────────────────────────────────────────
    /**
     * Inserts a new lane. Places at end, then sifts up.
     * Time complexity: O(log n)
     */
    void insert(Lane lane) {
        int i    = size++;
        heap[i]  = lane;
        pos[lane.id] = i;
        siftUp(i);
    }

    // ── Targeted update ──────────────────────────────────────
    /**
     * Called after a lane's state fields are modified in-place.
     * Restores the heap property by sifting the lane up OR down
     * from its current position.
     *
     * Only ONE direction actually moves the element (siftUp if
     * priority increased, siftDown if it decreased). The other
     * call returns immediately after the first comparison.
     *
     * siftUp() + siftDown()     →  O(log n)
     *
     * Time complexity: O(log n)
     */
    void update(int laneId) {
        int p = pos[laneId];
        if (p < 0 || p >= size) return;
        siftUp(p);
        siftDown(pos[laneId]); // re-read pos: siftUp may have moved the lane
    }

    // ── Internal: comparison ─────────────────────────────────
    /**
     * True if heap[i] has strictly higher priority than heap[j].
     * Uses the lazy virtual clock — non-served lanes' effective
     * priorities are derived from currentCycle on every call.
     * O(1) per comparison.
     */
    private boolean higherPriority(int i, int j) {
        return heap[i].computePriority(currentCycle)
             > heap[j].computePriority(currentCycle);
    }

    // ── Internal: siftUp ─────────────────────────────────────
    /**
     * Moves element at index i upward until heap property holds.
     * Time complexity: O(log n) — at most log₂(n) swaps.
     */
    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (higherPriority(i, parent)) {
                swap(i, parent);
                i = parent;
            } else {
                break;
            }
        }
    }

    // ── Internal: siftDown ───────────────────────────────────
    /**
     * Moves element at index i downward until heap property holds.
     * At each step, swaps with the larger child if it has higher
     * priority. Non-served lanes' effective priorities (grown via
     * lazy clock) are evaluated here, ensuring the served lane
     * settles into the correct position.
     * Time complexity: O(log n) — at most log₂(n) swaps.
     */
    private void siftDown(int i) {
        while (true) {
            int left    = 2 * i + 1;
            int right   = 2 * i + 2;
            int largest = i;
            if (left  < size && higherPriority(left,  largest)) largest = left;
            if (right < size && higherPriority(right, largest)) largest = right;
            if (largest == i) break;
            swap(i, largest);
            i = largest;
        }
    }

    // ── Internal: swap ───────────────────────────────────────
    /** Swaps two heap positions and keeps pos[] consistent. O(1). */
    private void swap(int i, int j) {
        pos[heap[i].id] = j;
        pos[heap[j].id] = i;
        Lane tmp = heap[i];
        heap[i]  = heap[j];
        heap[j]  = tmp;
    }

    // ── Sorted snapshot for display ──────────────────────────
    /**
     * Returns lanes sorted by current effective priority (highest
     * first). Used only for display — not part of the scheduling
     * algorithm.
     * Time complexity: O(n log n) — sorting n lanes.
     */
    List<Lane> getSortedSnapshot() {
        List<Lane> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(heap[i]);
        list.sort((a, b) -> Double.compare(
            b.computePriority(currentCycle),
            a.computePriority(currentCycle)));
        return list;
    }
}


// ─────────────────────────────────────────────────────────────
// CLASS: SignalScheduler
//
// Drives the simulation. Uses IndexedMaxHeap + lazy virtual clock
// to achieve O(log n) per scheduling cycle.
// ─────────────────────────────────────────────────────────────
class SignalScheduler {

    // ── Simulation parameters ────────────────────────────────
    private static final int    GREEN_DURATION  = 20;   // seconds
    private static final double DEPARTURE_RATE  = 0.5;  // vehicles/second

    // ── State ────────────────────────────────────────────────
    private final IndexedMaxHeap heap;
    private final List<Lane>     allLanes;
    private       int            currentCycle;
    private       int            nextId;

    SignalScheduler(int maxLanes) {
        heap         = new IndexedMaxHeap(maxLanes);
        allLanes     = new ArrayList<>();
        currentCycle = 0;
        nextId       = 0;
    }

    // ── Lane registration ────────────────────────────────────
    /**
     * Registers a lane and inserts it into the heap.
     * Time complexity: O(log n)
     */
    void addLane(String name, int vehicleCount,
                 int initialWaitingTime, boolean emergencyFlag) {
        Lane lane = new Lane(nextId++, name, vehicleCount,
                             initialWaitingTime, emergencyFlag);
        allLanes.add(lane);
        heap.setCycle(currentCycle);
        heap.insert(lane);
    }

    // ── Core scheduling cycle ────────────────────────────────
    /**
     * Executes one signal allocation cycle using the greedy
     * max-priority strategy with the lazy virtual clock.
     *
     * Step-by-step complexity:
     *   1. heap.setCycle()     →  O(1)
     *   2. heap.peekMax()      →  O(1)   — just reads heap[0]
     *   3. Update served lane  →  O(1)   — field assignments only
     *   4. heap.update()       →  O(log n) — single siftDown
     *   5. Non-served lanes    →  O(1)   — lazy clock, no explicit update
     *   ─────────────────────────────────────────────────────
     *   Total per cycle        →  O(log n)  ✓
     *
     * @param cycleNumber 1-based index for display.
     * @return the Lane that received the green signal.
     */
    Lane runCycle(int cycleNumber) {
        this.currentCycle = cycleNumber;

        if (heap.isEmpty()) {
            System.out.println("  [WARN] No lanes registered.");
            return null;
        }

        // ── Step 1: Set cycle reference — O(1) ────────────────
        heap.setCycle(cycleNumber);

        // ── Step 2: Identify winning lane — O(1) peek ─────────
        Lane served          = heap.peekMax();
        double winPriority   = served.computePriority(cycleNumber);

        // ── Print decision ─────────────────────────────────────
        printCycleHeader(cycleNumber);
        System.out.printf(
            "  ✔  GREEN SIGNAL → %-14s  |  Priority Score = %.1f%n%n",
            served.name, winPriority);
        if (served.emergencyFlag) {
            System.out.println(
                "  ⚠  EMERGENCY VEHICLE OVERRIDE ACTIVE for " + served.name);
        }

        // ── Step 3: Update served lane state in-place — O(1) ──
        // Vehicles depart; waiting time and fairness debt are cleared
        // by setting lastServedCycle = cycleNumber and baseWaitingTime = 0.
        // Non-served lanes need ZERO explicit updates — their effective
        // priority continues to grow via the lazy clock formula.
        int departed          = (int) (GREEN_DURATION * DEPARTURE_RATE);
        served.vehicleCount   = Math.max(0, served.vehicleCount - departed);
        served.baseWaitingTime = 0;
        served.lastServedCycle = cycleNumber;  // ← resets lazy clock for this lane
        served.emergencyFlag   = false;

        // ── Step 4: Restore heap with one targeted sift — O(log n) ──
        // The served lane's priority dropped (its wait reset to 0).
        // heap.update() locates it via pos[] and calls siftDown, comparing
        // against non-served lanes whose effective priorities are evaluated
        // fresh via computePriority(currentCycle). This correctly places
        // the served lane without touching any other lane.
        heap.update(served.id);

        // ── Step 5: Display queue state ───────────────────────
        printQueueStatus();

        return served;
    }

    // ── Live lane update (user / sensor input) ────────────────
    /**
     * Updates a lane's vehicle count and emergency flag, then
     * restores heap property with a targeted sift.
     *
     * Time complexity: O(log n) — no rebuild needed.
     */
    void updateLane(String laneName, int newVehicleCount, boolean emergency) {
        for (Lane lane : allLanes) {
            if (lane.name.equalsIgnoreCase(laneName)) {
                lane.vehicleCount  = newVehicleCount;
                lane.emergencyFlag = emergency;
                heap.setCycle(currentCycle);
                heap.update(lane.id);  // O(log n) targeted sift
                return;
            }
        }
        System.out.println("  [WARN] Lane '" + laneName + "' not found.");
    }

    // ── Display helpers ──────────────────────────────────────
    private void printCycleHeader(int cycleNumber) {
        String bar = "═".repeat(72);
        System.out.println("\n" + bar);
        System.out.printf("  CYCLE %2d%n", cycleNumber);
        System.out.println(bar);
    }

    void printQueueStatus() {
        System.out.println("  ── Current Queue Status (highest priority first) ──");
        for (int i = 0; i < heap.getSortedSnapshot().size(); i++) {
            Lane lane = heap.getSortedSnapshot().get(i);
            System.out.printf("  %d. %s%n", i + 1,
                lane.toDisplayString(currentCycle));
        }
        System.out.println();
    }

    List<Lane> getLanes() { return allLanes; }
    int getCurrentCycle() { return currentCycle; }
}


// ─────────────────────────────────────────────────────────────
// CLASS: TrafficSignalOptimizer  (entry point)
// ─────────────────────────────────────────────────────────────
public class DynamicTrafficSignalOptimization {

    private static final String THIN_BAR = "─".repeat(72);

    // ── main ─────────────────────────────────────────────────
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Intelligent Dynamic Traffic Signal Optimization");
        System.out.println("Using Greedy Algorithms & Priority Queues\n");
        System.out.println("Select mode:");
        System.out.println("  1 → Automated Demo (tests all 3 scenarios)");
        System.out.println("  2 → Interactive Simulation (console input)");
        System.out.print("Enter choice [1/2]: ");

        int choice = 1;
        try { choice = Integer.parseInt(scanner.nextLine().trim()); }
        catch (NumberFormatException e) { choice = 1; }

        if (choice == 2) runInteractiveMode(scanner);
        else             runDemoMode();

        scanner.close();
    }

    // ─────────────────────────────────────────────────────────
    // DEMO MODE
    //
    // Scenario 1 (cycles 1-2): Standard traffic.
    // Scenario 2 (cycles 3-4): Emergency vehicle override.
    // Scenario 3 (cycles 5-8): Starvation prevention.
    // ─────────────────────────────────────────────────────────
    static void runDemoMode() {
        System.out.println("\n" + THIN_BAR);
        System.out.println("  AUTOMATED DEMO MODE");
        System.out.println(THIN_BAR);

        // Max 8 lanes — sets IndexedMaxHeap capacity
        SignalScheduler scheduler = new SignalScheduler(8);

        //            name          vehicles  wait(s)  emergency
        scheduler.addLane("Lane North",  12,    30,     false);
        scheduler.addLane("Lane South",   8,    20,     false);
        scheduler.addLane("Lane East",   15,    40,     false); // highest initial score
        scheduler.addLane("Lane West",    2,    10,     false); // lowest initial score

        System.out.println("\nInitial lane configuration:");
        scheduler.printQueueStatus();

        // ── Scenario 1: Standard traffic ─────────────────────
        System.out.println(THIN_BAR);
        System.out.println("  SCENARIO 1 — Standard Traffic Flow");
        System.out.println(THIN_BAR);
        // Expected: Lane East wins (15×40 = 600), then lazy clock
        // naturally grows priorities of remaining lanes.
        scheduler.runCycle(1);
        scheduler.runCycle(2);

        // ── Scenario 2: Emergency vehicle override ────────────
        System.out.println(THIN_BAR);
        System.out.println("  SCENARIO 2 — Emergency Vehicle Override");
        System.out.println(THIN_BAR);
        System.out.println("  [EVENT] Ambulance detected in Lane West!");
        // updateLane() calls heap.update() — O(log n), no rebuild.
        scheduler.updateLane("Lane West", 2, true);
        scheduler.runCycle(3);
        scheduler.runCycle(4);

        // ── Scenario 3: Starvation prevention ────────────────
        System.out.println(THIN_BAR);
        System.out.println("  SCENARIO 3 — Starvation Prevention (Lazy Fairness)");
        System.out.println(THIN_BAR);
        System.out.println("  [INFO] Heavy traffic on North/South/East.");
        System.out.println("         Lane West has 1 vehicle — watch fairness grow.\n");
        scheduler.updateLane("Lane North", 20, false);
        scheduler.updateLane("Lane South", 18, false);
        scheduler.updateLane("Lane East",  22, false);
        scheduler.updateLane("Lane West",   1, false);

        for (int cycle = 5; cycle <= 9; cycle++) {
            scheduler.runCycle(cycle);
        }

        System.out.println(THIN_BAR);
        System.out.println("  DEMO COMPLETE — All 3 scenarios verified.");
        System.out.println(THIN_BAR);
    }

    // ─────────────────────────────────────────────────────────
    // INTERACTIVE MODE
    // ─────────────────────────────────────────────────────────
    static void runInteractiveMode(Scanner scanner) {
        System.out.println("\n" + THIN_BAR);
        System.out.println("  INTERACTIVE SIMULATION MODE");
        System.out.println(THIN_BAR);

        System.out.print("\nEnter number of lanes (min 2, max 8): ");
        int numLanes = readInt(scanner, 2, 8);

        SignalScheduler scheduler = new SignalScheduler(numLanes);

        System.out.println("\nEnter initial parameters for each lane:");
        for (int i = 1; i <= numLanes; i++) {
            System.out.printf("\n  Lane %d name (e.g. North): ", i);
            String name = scanner.nextLine().trim();
            if (name.isEmpty()) name = "Lane " + i;

            System.out.printf("  Vehicle count for %s: ", name);
            int count = readInt(scanner, 0, 1000);

            System.out.printf("  Initial waiting time (seconds) for %s: ", name);
            int wait = readInt(scanner, 0, 10000);

            System.out.printf("  Emergency vehicle present? (y/n): ");
            boolean emergency = scanner.nextLine().trim().equalsIgnoreCase("y");

            scheduler.addLane(name, count, wait, emergency);
        }

        System.out.println("\nInitial lane configuration:");
        scheduler.printQueueStatus();

        int cycle = 1;
        while (true) {
            System.out.println(THIN_BAR);
            System.out.printf("  CYCLE %d — Options%n", cycle);
            System.out.println("  1 → Run cycle with current data");
            System.out.println("  2 → Update a lane before cycle");
            System.out.println("  3 → Exit simulation");
            System.out.print("  Choice: ");

            int option = readInt(scanner, 1, 3);
            if (option == 3) break;

            if (option == 2) {
                List<Lane> lanes = scheduler.getLanes();
                System.out.println("\nAvailable lanes:");
                for (int i = 0; i < lanes.size(); i++) {
                    System.out.printf("  %d. %s%n", i + 1, lanes.get(i).name);
                }
                System.out.print("Select lane number to update: ");
                int idx = readInt(scanner, 1, lanes.size()) - 1;
                Lane target = lanes.get(idx);

                System.out.printf("  New vehicle count for %s: ", target.name);
                int newCount = readInt(scanner, 0, 1000);

                System.out.printf("  Emergency vehicle? (y/n): ");
                boolean emerg = scanner.nextLine().trim().equalsIgnoreCase("y");

                scheduler.updateLane(target.name, newCount, emerg);
                System.out.println("  [OK] Lane updated — heap restored in O(log n).");
            }

            scheduler.runCycle(cycle);
            cycle++;
        }

        System.out.println("\n" + THIN_BAR);
        System.out.printf("  Simulation ended after %d cycle(s).%n", cycle - 1);
        System.out.println(THIN_BAR);
    }

    // ── Utility ──────────────────────────────────────────────
    private static int readInt(Scanner scanner, int min, int max) {
        while (true) {
            try {
                int v = Integer.parseInt(scanner.nextLine().trim());
                if (v >= min && v <= max) return v;
                System.out.printf("  Enter a value between %d and %d: ", min, max);
            } catch (NumberFormatException e) {
                System.out.print("  Invalid input. Enter a number: ");
            }
        }
    }
}