// ============================================================
// Vehicle.java
//
// Represents a generic vehicle waiting at a traffic lane.
// Extended by EmergencyVehicle for priority overrides.
// ============================================================
public class Vehicle {

    private static int idCounter = 0;

    private final int id;
    private int waitingTime; // seconds spent waiting at the lane

    public Vehicle() {
        this.id          = ++idCounter;
        this.waitingTime = 0;
    }

    // ── Getters / mutators ───────────────────────────────────
    public int  getId()          { return id; }
    public int  getWaitingTime() { return waitingTime; }
    public void addWait(int seconds) { waitingTime += seconds; }

    @Override
    public String toString() {
        return "Vehicle#" + id + "(wait=" + waitingTime + "s)";
    }
}