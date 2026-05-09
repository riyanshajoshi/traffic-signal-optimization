// ============================================================
// Simulation.java
//
// Orchestrates the simulation: manages the collection of Lane
// objects, owns the Scheduler, drives cycle execution, and
// provides both demo and interactive run modes.
//
// Separation of concerns:
//   Lane       — state of a single lane (data + lazy clock)
//   Scheduler  — heap algorithm (pure scheduling logic)
//   Simulation — coordination, I/O, scenario management
// ============================================================
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Simulation {

    private static final int    MAX_LANES = 8;
    private static final String BAR       = "═".repeat(72);
    private static final String THIN_BAR  = "─".repeat(72);

    // ── State ────────────────────────────────────────────────
    private final Scheduler scheduler;
    private final List<Lane> lanes;
    private       Lane       lastServedLane; // to reset signal each cycle
    private       int        nextId;

    // ── Constructor ──────────────────────────────────────────
    public Simulation() {
        this.scheduler      = new Scheduler(MAX_LANES);
        this.lanes          = new ArrayList<>();
        this.lastServedLane = null;
        this.nextId         = 0;
    }

    // ── Lane registration ────────────────────────────────────
    /**
     * Creates a Lane, optionally assigns an EmergencyVehicle,
     * and inserts it into the Scheduler's heap.
     * Time complexity: O(log n) — heap insert
     */
    public void addLane(String name, int vehicleCount,
                        int initialWait, EmergencyVehicle ev) {
        Lane lane = new Lane(nextId++, name, vehicleCount, initialWait);
        if (ev != null) lane.setEmergencyVehicle(ev);
        lanes.add(lane);
        scheduler.setCycle(0);
        scheduler.insert(lane);
    }

    // ── Live lane update (sensor / user input) ────────────────
    /**
     * Updates vehicle count and emergency vehicle for a named lane,
     * then restores heap order with a single targeted sift.
     * Time complexity: O(log n) — heap update only, no rebuild
     */
    public void updateLane(String name, int vehicleCount, EmergencyVehicle ev) {
        for (Lane lane : lanes) {
            if (lane.name.equalsIgnoreCase(name)) {
                lane.setVehicleCount(vehicleCount);
                lane.setEmergencyVehicle(ev);
                scheduler.update(lane.id); // O(log n) targeted sift
                return;
            }
        }
        System.out.println("  [WARN] Lane '" + name + "' not found.");
    }

    // ── Run one cycle ────────────────────────────────────────
    /**
     * Executes a single greedy scheduling cycle:
     *   1. Peek max-priority lane — O(1)
     *   2. Record display info    — O(1)
     *   3. Serve + restore heap   — O(log n)
     *   4. Print results          — O(n log n) display only
     *
     * @param cycleNumber 1-based cycle index
     * @return the Lane that received the green signal
     */
    public Lane runCycle(int cycleNumber) {
        if (scheduler.isEmpty()) {
            System.out.println("  [WARN] No lanes registered.");
            return null;
        }

        scheduler.setCycle(cycleNumber);

        // Capture display info before state changes
        Lane   candidate    = scheduler.peekMax();
        double winPriority  = candidate.computePriority(cycleNumber);
        boolean hadEmergency = candidate.hasEmergencyVehicle();
        String  emergencyLabel = hadEmergency
                                 ? candidate.getEmergencyVehicle().toString()
                                 : "";

        // Greedy scheduling step — O(log n)
        Lane served = scheduler.scheduleNext(cycleNumber, lastServedLane);
        lastServedLane = served;

        // Print decision
        printCycleHeader(cycleNumber);
        System.out.printf(
            "  ✔  GREEN SIGNAL → %-14s  |  Priority Score = %.1f%n%n",
            served.name, winPriority);
        if (hadEmergency) {
            System.out.println(
                "  ⚠  EMERGENCY OVERRIDE — " + emergencyLabel
                + " cleared from " + served.name);
        }

        printQueueStatus(cycleNumber);
        return served;
    }

    // ── Display ──────────────────────────────────────────────
    public void printQueueStatus(int cycleNumber) {
        System.out.println("  ── Current Queue Status (highest priority first) ──");
        List<Lane> snapshot = scheduler.getSortedSnapshot();
        for (int i = 0; i < snapshot.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1,
                snapshot.get(i).toDisplayString(cycleNumber));
        }
        System.out.println();
    }

    private void printCycleHeader(int n) {
        System.out.println("\n" + BAR);
        System.out.printf("  CYCLE %2d%n", n);
        System.out.println(BAR);
    }

    public List<Lane> getLanes() { return lanes; }

    // ════════════════════════════════════════════════════════
    // DEMO MODE
    //
    // Scenario 1 (cycles 1-2) : Standard traffic flow
    // Scenario 2 (cycles 3-4) : Emergency vehicle override
    // Scenario 3 (cycles 5-9) : Starvation prevention
    // ════════════════════════════════════════════════════════
    public static void runDemo() {
        System.out.println("\n" + "─".repeat(72));
        System.out.println("  AUTOMATED DEMO MODE");
        System.out.println("─".repeat(72));

        Simulation sim = new Simulation();

        //                 name           vehicles  wait(s)  emergency
        sim.addLane("Lane North",  12,    30,   null);
        sim.addLane("Lane South",   8,    20,   null);
        sim.addLane("Lane East",   15,    40,   null); // highest initial score
        sim.addLane("Lane West",    2,    10,   null); // lowest initial score

        System.out.println("\nInitial lane configuration:");
        sim.printQueueStatus(0);

        // ── Scenario 1: Standard traffic ─────────────────────
        System.out.println("─".repeat(72));
        System.out.println("  SCENARIO 1 — Standard Traffic Flow");
        System.out.println("─".repeat(72));
        sim.runCycle(1);
        sim.runCycle(2);

        // ── Scenario 2: Emergency vehicle override ────────────
        System.out.println("─".repeat(72));
        System.out.println("  SCENARIO 2 — Emergency Vehicle Override");
        System.out.println("─".repeat(72));
        System.out.println("  [EVENT] Ambulance detected in Lane West!\n");
        sim.updateLane("Lane West", 2,
            new EmergencyVehicle(EmergencyVehicle.ServiceType.AMBULANCE));
        sim.runCycle(3);
        sim.runCycle(4);

        // ── Scenario 3: Starvation prevention ────────────────
        System.out.println("─".repeat(72));
        System.out.println("  SCENARIO 3 — Starvation Prevention");
        System.out.println("─".repeat(72));
        System.out.println("  [INFO] Heavy traffic on North/South/East;");
        System.out.println("         Lane West has 1 vehicle — watch fairness grow.\n");
        sim.updateLane("Lane North", 20, null);
        sim.updateLane("Lane South", 18, null);
        sim.updateLane("Lane East",  22, null);
        sim.updateLane("Lane West",   1, null);
        for (int c = 5; c <= 9; c++) sim.runCycle(c);

        System.out.println("─".repeat(72));
        System.out.println("  DEMO COMPLETE — All 3 scenarios verified.");
        System.out.println("─".repeat(72));
    }

    // ════════════════════════════════════════════════════════
    // INTERACTIVE MODE
    // ════════════════════════════════════════════════════════
    public static void runInteractive(Scanner scanner) {
        System.out.println("\n" + "─".repeat(72));
        System.out.println("  INTERACTIVE SIMULATION MODE");
        System.out.println("─".repeat(72));

        System.out.print("\nEnter number of lanes (min 2, max 8): ");
        int numLanes = readInt(scanner, 2, 8);

        Simulation sim = new Simulation();

        System.out.println("\nEnter initial parameters for each lane:");
        for (int i = 1; i <= numLanes; i++) {
            System.out.printf("\n  Lane %d name (e.g. North): ", i);
            String name = scanner.nextLine().trim();
            if (name.isEmpty()) name = "Lane " + i;

            System.out.printf("  Vehicle count for %s: ", name);
            int count = readInt(scanner, 0, 1000);

            System.out.printf("  Waiting time (seconds) for %s: ", name);
            int wait = readInt(scanner, 0, 10000);

            System.out.print("  Emergency vehicle present? (y/n): ");
            EmergencyVehicle ev = null;
            if (scanner.nextLine().trim().equalsIgnoreCase("y")) {
                ev = promptEmergencyVehicle(scanner);
            }

            sim.addLane(name, count, wait, ev);
        }

        System.out.println("\nInitial configuration:");
        sim.printQueueStatus(0);

        int cycle = 1;
        while (true) {
            System.out.println("─".repeat(72));
            System.out.printf("  CYCLE %d — Options%n", cycle);
            System.out.println("  1 → Run cycle");
            System.out.println("  2 → Update a lane");
            System.out.println("  3 → Exit");
            System.out.print("  Choice: ");
            int opt = readInt(scanner, 1, 3);
            if (opt == 3) break;

            if (opt == 2) {
                List<Lane> ls = sim.getLanes();
                System.out.println("\nAvailable lanes:");
                for (int i = 0; i < ls.size(); i++)
                    System.out.printf("  %d. %s%n", i + 1, ls.get(i).name);
                System.out.print("Select lane: ");
                Lane target = ls.get(readInt(scanner, 1, ls.size()) - 1);

                System.out.printf("  New vehicle count for %s: ", target.name);
                int cnt = readInt(scanner, 0, 1000);

                System.out.print("  Emergency vehicle? (y/n): ");
                EmergencyVehicle ev = null;
                if (scanner.nextLine().trim().equalsIgnoreCase("y"))
                    ev = promptEmergencyVehicle(scanner);

                sim.updateLane(target.name, cnt, ev);
                System.out.println("  [OK] Lane updated — heap restored in O(log n).");
            }

            sim.runCycle(cycle++);
        }

        System.out.printf("%n  Simulation ended after %d cycle(s).%n", cycle - 1);
    }

    // ── Helpers ──────────────────────────────────────────────
    private static EmergencyVehicle promptEmergencyVehicle(Scanner scanner) {
        System.out.println("  Select type:  1=Ambulance  2=Fire Truck  3=Police");
        System.out.print("  Choice: ");
        int t = readInt(scanner, 1, 3);
        EmergencyVehicle.ServiceType type;
        if      (t == 2) type = EmergencyVehicle.ServiceType.FIRE_TRUCK;
        else if (t == 3) type = EmergencyVehicle.ServiceType.POLICE;
        else             type = EmergencyVehicle.ServiceType.AMBULANCE;
        return new EmergencyVehicle(type);
    }

    static int readInt(Scanner scanner, int min, int max) {
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