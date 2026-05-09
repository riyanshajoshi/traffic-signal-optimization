// ============================================================
// TrafficSignal.java
//
// Models the physical signal head at a single lane of an
// intersection. Manages state transitions (RED → GREEN → RED)
// and holds the configured green phase duration.
// ============================================================
public class TrafficSignal {

    public enum State { RED, GREEN, YELLOW }

    // Seconds of green time awarded per scheduling cycle.
    // Vehicles depart at DEPARTURE_RATE = 0.5 veh/s, so
    // 20 s × 0.5 = 10 vehicles clear per green phase.
    public static final int GREEN_DURATION = 20;

    private State currentState;

    public TrafficSignal() {
        this.currentState = State.RED; // all signals start RED
    }

    // ── State transitions ────────────────────────────────────
    public void setGreen()  { currentState = State.GREEN;  }
    public void setRed()    { currentState = State.RED;    }
    public void setYellow() { currentState = State.YELLOW; }

    // ── Queries ──────────────────────────────────────────────
    public State   getState()         { return currentState; }
    public boolean isGreen()          { return currentState == State.GREEN; }
    public int     getGreenDuration() { return GREEN_DURATION; }

    @Override
    public String toString() { return currentState.name(); }
}