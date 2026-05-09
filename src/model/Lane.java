// ============================================================
// Lane.java
//
// Models a single traffic lane at an intersection.
// Owns a TrafficSignal and optionally an EmergencyVehicle.
//
// LAZY VIRTUAL CLOCK
// ──────────────────
// Rather than looping over every non-served lane to increment
// waiting time each cycle (O(n)), each lane stores only:
//   • baseWaitingTime  — wait at the moment it was last served
//   • lastServedCycle  — cycle index when it last got green
//
// Effective values are derived on-the-fly in O(1):
//   skipped        = currentCycle - lastServedCycle
//   effectiveWait  = baseWaitingTime + skipped × WAIT_INCREMENT
//   effectiveFairness = skipped × FAIRNESS_INCREMENT
//
// This keeps the per-cycle cost of non-served lanes at O(1),
// which is what allows the overall scheduling step to be O(log n).
// ============================================================
public class Lane {

    // ── Priority constants ───────────────────────────────────
    /** Flat bonus added when an emergency vehicle is present.
     *  High enough to always preempt any normal lane. */
    public static final int EMERGENCY_BONUS    = 10_000;

    /** Priority added per skipped cycle (Aging / anti-starvation). */
    public static final int FAIRNESS_INCREMENT = 5;

    /** Seconds of waiting time added per skipped cycle. */
    public static final int WAIT_INCREMENT     = 20;

    /** Vehicles that clear per second of green time. */
    public static final double DEPARTURE_RATE  = 0.5;

    // ── Identity ─────────────────────────────────────────────
    /** 0-based index used by Scheduler's pos[] array. */
    public final int    id;
    public final String name;

    // ── Composition ──────────────────────────────────────────
    private final TrafficSignal   signal;
    private       EmergencyVehicle emergencyVehicle; // null if none present

    // ── Real-time state ──────────────────────────────────────
    private int vehicleCount;

    // Lazy clock anchors — updated only when THIS lane is served.
    private int baseWaitingTime;
    private int lastServedCycle;

    // ── Constructor ──────────────────────────────────────────
    public Lane(int id, String name, int vehicleCount, int initialWaitingTime) {
        this.id               = id;
        this.name             = name;
        this.vehicleCount     = vehicleCount;
        this.baseWaitingTime  = initialWaitingTime;
        this.lastServedCycle  = 0;
        this.signal           = new TrafficSignal();
        this.emergencyVehicle = null;
    }

    // ── Priority computation  O(1) ───────────────────────────
    /**
     * Computes the composite priority score using the lazy clock.
     *
     *   Priority = (vehicleCount × effectiveWait)
     *              + effectiveFairness
     *              + EMERGENCY_BONUS  (if applicable)
     *
     * Called inside Scheduler comparisons — never stores the result,
     * ensuring non-served lanes' priorities grow implicitly each cycle
     * without any explicit update loop.
     *
     * Time complexity: O(1)
     */
    public double computePriority(int currentCycle) {
        int  skipped          = currentCycle - lastServedCycle;
        long effectiveWait    = baseWaitingTime + (long) skipped * WAIT_INCREMENT;
        long effectiveFairness = (long) skipped * FAIRNESS_INCREMENT;
        return (double) (vehicleCount * effectiveWait)
               + effectiveFairness
               + (hasEmergencyVehicle() ? EMERGENCY_BONUS : 0);
    }

    // ── Serve this lane ──────────────────────────────────────
    /**
     * Called by Scheduler when this lane wins the scheduling step.
     * Resets the lazy clock, clears the emergency vehicle, and
     * sets the signal to GREEN.
     *
     * Time complexity: O(1)
     */
    public void serve(int cycleNumber) {
        int departed      = (int) (TrafficSignal.GREEN_DURATION * DEPARTURE_RATE);
        vehicleCount      = Math.max(0, vehicleCount - departed);
        baseWaitingTime   = 0;
        lastServedCycle   = cycleNumber; // ← resets lazy clock for this lane
        emergencyVehicle  = null;        // emergency vehicle has passed
        signal.setGreen();
    }

    /** Resets signal to RED (called on all non-served lanes). */
    public void resetSignal() { signal.setRed(); }

    // ── Emergency vehicle management ─────────────────────────
    public void             setEmergencyVehicle(EmergencyVehicle ev) { this.emergencyVehicle = ev; }
    public EmergencyVehicle getEmergencyVehicle()                    { return emergencyVehicle; }
    public boolean          hasEmergencyVehicle()                    { return emergencyVehicle != null; }

    // ── Vehicle count management ─────────────────────────────
    public int  getVehicleCount()        { return vehicleCount; }
    public void setVehicleCount(int cnt) { this.vehicleCount = cnt; }

    // ── Signal access ────────────────────────────────────────
    public TrafficSignal getSignal() { return signal; }

    // ── Display ──────────────────────────────────────────────
    public String toDisplayString(int currentCycle) {
        int  skipped           = currentCycle - lastServedCycle;
        long effectiveWait     = baseWaitingTime + (long) skipped * WAIT_INCREMENT;
        long effectiveFairness = (long) skipped * FAIRNESS_INCREMENT;
        String emLabel         = hasEmergencyVehicle()
                                 ? emergencyVehicle.toString() : "none";
        return String.format(
            "%-14s | vehicles=%3d | wait=%3ds | signal=%-6s"
            + " | emergency=%-18s | fairness=%3d | priority=%8.1f",
            name, vehicleCount, effectiveWait, signal,
            emLabel, effectiveFairness, computePriority(currentCycle));
    }
}