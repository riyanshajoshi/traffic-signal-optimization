# 🚦 Intelligent Dynamic Traffic Signal Optimization
### Using Greedy Algorithms & Priority Queues

> **Language:** Java 11+  
> **Dependencies:** `java.util` only — no external libraries

---

## 📋 Table of Contents

- [Overview](#overview)
- [Problem Statement](#problem-statement)
- [Algorithm Design](#algorithm-design)
- [Priority Formula](#priority-formula)
- [How O(log n) Is Achieved](#how-olog-n-is-achieved)
- [Complexity Analysis](#complexity-analysis)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Running the Program](#running-the-program)
- [Demo Scenarios](#demo-scenarios)
- [Sample Output](#sample-output)
- [Future Enhancements](#future-enhancements)

---

## Overview

Urban traffic congestion is one of the most pressing challenges in modern cities. Traditional **fixed-time** traffic signals allocate equal green durations to every lane regardless of actual traffic density — causing unnecessary delays, unfair lane servicing, and critical failures during emergencies.

This project models the traffic signal control problem as a **real-time resource scheduling problem** and solves it using:

- A **Greedy Algorithm** that always serves the most urgent lane first
- A **custom Indexed Max-Heap** for O(log n) lane selection *and* O(log n) in-place updates
- A **Lazy Virtual Clock** so non-served lanes never need explicit updates
- An **Aging / Fairness mechanism** to prevent starvation of low-traffic lanes
- An **Emergency Override** system for immediate ambulance / fire truck prioritisation

---

## Problem Statement

> *How can traffic signals be dynamically optimised in real time to minimise vehicle waiting time, ensure fairness across all lanes, and prioritise emergency vehicles — using efficient algorithmic techniques?*

**Limitations of existing approaches:**

| Approach | Limitation |
|---|---|
| Fixed-time signals | Ignores real-time traffic density; relies on historical data only |
| Sensor-based adaptive | Considers vehicle count alone; no fairness or emergency guarantee |
| Manual control | Labour-intensive, inconsistent, not scalable |

---

## Algorithm Design

Each traffic lane is modelled as a **task competing for a shared resource** — the green signal. At every decision interval:

```
1. Set the current cycle on the heap              → O(1)
2. Peek at the max-priority lane                  → O(1)
3. Allocate green signal to that lane
4. Update the served lane's state in-place        → O(1)
5. Restore heap with a single targeted sift       → O(log n)
6. Non-served lanes need no update at all         → O(1)  [lazy clock]
─────────────────────────────────────────────────────────
Total per cycle                                   → O(log n)  ✓
```

```
┌──────────────────────────────────────────────────────────┐
│                  INDEXED MAX-HEAP                        │
│                                                          │
│  heap[]   [ Lane East | Lane North | Lane South | ... ]  │
│  pos[]    [ id=0→idx  | id=1→idx  | id=2→idx  | ... ]    │
│                                                          │
│  peekMax()  →  O(1)   reads heap[0]                      │
│  update(id) →  O(log n)  uses pos[id] to locate lane,    │
│                           then siftDown in place         │
│                                                          │
│  No clear(). No rebuild(). No loop over other lanes.     │
└──────────────────────────────────────────────────────────┘
```

---

## Priority Formula

```
Priority = (vehicle_count × effective_waiting_time)
         + effective_fairness_factor
         + EMERGENCY_BONUS      ← added if emergency vehicle present
```

Where effective values are derived lazily at comparison time:

```
skipped               = currentCycle - lastServedCycle
effective_wait        = baseWaitingTime + skipped × 20
effective_fairness    = skipped × 5
```

| Parameter | Description | Value |
|---|---|---|
| `vehicle_count` | Number of vehicles currently waiting | User / sensor input |
| `effective_wait` | Derived wait — grows automatically each skipped cycle | +20s per skip |
| `EMERGENCY_BONUS` | Flat bonus when emergency vehicle is present | `10,000` |
| `effective_fairness` | Anti-starvation boost — derived from skipped cycles | +5 per skip |
| `lastServedCycle` | Cycle index when the lane last received a green signal | Reset on serve |

**Why this formula works:**
- `vehicle_count × effective_wait` captures both congestion severity and time-urgency
- `EMERGENCY_BONUS` is large enough to always preempt any normal lane
- `effective_fairness` mirrors OS process **Aging** — a neglected lane's score grows until it wins
- All effective values are **computed on demand**, not stored — this is what makes the lazy clock possible

---

## How O(log n) Is Achieved

This is the core algorithmic contribution. A naive implementation (like Java's built-in `PriorityQueue`) hits **O(n log n)** per cycle because it cannot update an element's key in-place — you must remove it, change it, and re-insert, or clear and rebuild the entire heap.

Two techniques eliminate this:

### 1. Indexed Max-Heap

A custom binary heap that maintains a `pos[]` index array alongside `heap[]`:

```
heap[] = [ LaneA, LaneC, LaneB, LaneD ]
pos[]  = [ id=0 → 2,   id=1 → 0,   id=2 → 3,   id=3 → 1 ]
          (LaneA at idx 2) (LaneB at idx 0) ...
```

`pos[lane.id]` always holds the lane's current position in `heap[]`.  
When a lane's priority changes, the heap can locate it in **O(1)** and restore order with a single **siftDown in O(log n)** — instead of a full O(n log n) rebuild.

### 2. Lazy Virtual Clock

Instead of looping over every non-served lane to increment `waitingTime` and `fairnessFactor` each cycle (which costs **O(n)**), each lane stores only:

- `baseWaitingTime` — the wait at the last moment it was served
- `lastServedCycle` — which cycle it was last served

The effective waiting time and fairness are then derived on-the-fly inside `computePriority(currentCycle)`:

```java
int  skipped       = currentCycle - lastServedCycle;
long effectiveWait = baseWaitingTime + (long) skipped * WAIT_INCREMENT;
long fairness      = (long) skipped * FAIRNESS_INCREMENT;
```

Non-served lanes are **never touched**. Their growing urgency is implicit in the formula. This brings the "update non-served lanes" step from **O(n) → O(1)**.

### Combined result

| Step | Naive  | This implementation |
|---|---|---|
| Extract max | O(log n) | O(1) — peek only |
| Update served lane | O(1) | O(1) |
| Restore heap order | O(n log n) — full rebuild | **O(log n)** — single sift |
| Update non-served lanes | O(n) — explicit loop | **O(1)** — lazy clock |
| **Total per cycle** | **O(n log n)** | **O(log n)** ✓ |

---

## Complexity Analysis

| Operation | Time Complexity | Notes |
|---|---|---|
| `computePriority()` | O(1) | Fixed arithmetic using lazy clock |
| `heap.peekMax()` | O(1) | Reads `heap[0]` |
| `heap.insert()` | O(log n) | siftUp — called at startup only |
| `heap.update()` | **O(log n)** | Targeted siftDown via `pos[]` index |
| Update served lane state | O(1) | Field assignments only |
| Update non-served lanes | **O(1)** | Lazy virtual clock — no loop |
| **Total per cycle** | **O(log n)** | ✓ Matches synopsis claim |
| **Space complexity** | **O(n)** | `heap[]` and `pos[]` both size n |

For k cycles across n lanes: **O(k log n)** overall.

---

## Project Structure

```
📦 traffic-signal-optimizer
 ┣ 📄 TrafficSignalOptimizer.java   ← Single-file implementation (4 classes)
 ┗ 📄 README.md
```

**Classes inside `TrafficSignalOptimizer.java`:**

```
Lane                       — Models a single traffic lane
 ├── vehicleCount          — Vehicles currently waiting
 ├── baseWaitingTime       — Wait baseline at last serve time
 ├── lastServedCycle       — Cycle index when last served (lazy clock anchor)
 ├── emergencyFlag         — Emergency vehicle present?
 └── computePriority(cycle)— Derives effective priority on-the-fly  →  O(1)

IndexedMaxHeap             — Custom binary max-heap with O(log n) updates
 ├── heap[]                — The heap array of Lane objects
 ├── pos[]                 — pos[lane.id] = current index in heap[]
 ├── peekMax()             — Read top lane without removing        →  O(1)
 ├── insert()              — Add a lane, siftUp                    →  O(log n)
 ├── update(laneId)        — Restore order after in-place change   →  O(log n)
 ├── siftUp()              — Move element upward                   →  O(log n)
 └── siftDown()            — Move element downward                 →  O(log n)

SignalScheduler            — Orchestrates the simulation
 ├── addLane()             — Register a lane                       →  O(log n)
 ├── runCycle()            — One full O(log n) scheduling step
 └── updateLane()          — Live sensor/user update               →  O(log n)

TrafficSignalOptimizer     — Entry point (main method)
 ├── runDemoMode()          — 3 automated test scenarios
 └── runInteractiveMode()   — Console-driven live simulation
```

---

## Getting Started

### Prerequisites

- **Java 11 or higher**

  ```bash
  java -version
  ```

- No build tools or external dependencies required.

### Clone & Compile

```bash
# Clone the repository
git clone https://github.com/<your-username>/traffic-signal-optimizer.git
cd traffic-signal-optimizer

# Compile
javac TrafficSignalOptimizer.java
```

---

## Running the Program

```bash
java TrafficSignalOptimizer
```

You will be prompted to choose a mode:

```
Select mode:
  1 → Automated Demo (tests all 3 scenarios)
  2 → Interactive Simulation (console input)
```

### Mode 1 — Automated Demo

Runs 9 pre-configured cycles covering all three test scenarios with no user input required. Best for quickly verifying correctness.

### Mode 2 — Interactive Simulation

Lets you define your own lanes and drive the simulation cycle-by-cycle:

```
Enter number of lanes (min 2, max 8): 3

  Lane 1 name (e.g. North): North
  Vehicle count for North: 12
  Initial waiting time (seconds) for North: 30
  Emergency vehicle present? (y/n): n
  ...

CYCLE 1 — Options
  1 → Run cycle with current data
  2 → Update a lane before cycle
  3 → Exit simulation
```

---

## Demo Scenarios

### Scenario 1 — Standard Traffic Flow *(Cycles 1–2)*

Verifies that the greedy choice correctly selects the lane with the highest `vehicle_count × effective_wait` product each cycle, and that the lazy clock naturally grows priorities of waiting lanes between serves.

| Cycle | Winner | Priority Score | Reason |
|---|---|---|---|
| 1 | Lane East | 600.0 | 15 vehicles × 40s wait |
| 2 | Lane North | 605.0 | Lazy clock grew its effective wait to 50s |

### Scenario 2 — Emergency Vehicle Override *(Cycle 3)*

An ambulance is injected into **Lane West** via `updateLane()` — which triggers a single O(log n) heap sift, not a rebuild. The `EMERGENCY_BONUS = 10,000` immediately elevates it above all other lanes.

| Cycle | Winner | Priority Score | Reason |
|---|---|---|---|
| 3 | **Lane West 🚑** | **10,155.0** | Emergency bonus overrides all others |

### Scenario 3 — Starvation Prevention *(Cycles 5–9)*

Heavy traffic is loaded onto North/South/East while West has only 1 vehicle. The lazy fairness formula (`skipped × 5`) accumulates each cycle West is skipped, demonstrating that even the lightest lane is eventually served without any explicit loop over non-served lanes.

> This mirrors the **Aging** technique from OS process scheduling — implemented here at zero per-cycle cost via the lazy virtual clock.

---

## Sample Output

```
Intelligent Dynamic Traffic Signal Optimization
Using Greedy Algorithms & Priority Queues

Select mode:
  1 → Automated Demo (tests all 3 scenarios)
  2 → Interactive Simulation (console input)
Enter choice [1/2]: 1

Initial lane configuration:
  ── Current Queue Status (highest priority first) ──
  1. Lane East       | vehicles= 15 | wait= 40s | emergency=false | fairness=  0 | priority=   600.0
  2. Lane North      | vehicles= 12 | wait= 30s | emergency=false | fairness=  0 | priority=   360.0
  3. Lane South      | vehicles=  8 | wait= 20s | emergency=false | fairness=  0 | priority=   160.0
  4. Lane West       | vehicles=  2 | wait= 10s | emergency=false | fairness=  0 | priority=    20.0

════════════════════════════════════════════════════════════════════════
  CYCLE  1
════════════════════════════════════════════════════════════════════════
  ✔  GREEN SIGNAL → Lane East        |  Priority Score = 600.0

  ── Current Queue Status ──
  1. Lane North      | vehicles= 12 | wait= 50s | emergency=false | fairness=  5 | priority=   605.0
  2. Lane South      | vehicles=  8 | wait= 40s | emergency=false | fairness=  5 | priority=   325.0
  3. Lane West       | vehicles=  2 | wait= 30s | emergency=false | fairness=  5 | priority=    65.0
  4. Lane East       | vehicles=  5 | wait=  0s | emergency=false | fairness=  0 | priority=     0.0

  [EVENT] Ambulance detected in Lane West!

════════════════════════════════════════════════════════════════════════
  CYCLE  3
════════════════════════════════════════════════════════════════════════
  ✔  GREEN SIGNAL → Lane West        |  Priority Score = 10155.0
  ⚠  EMERGENCY VEHICLE OVERRIDE ACTIVE for Lane West
```

---

## Future Enhancements

- **IoT Sensor Integration** — Replace console input with live data from induction loops, radar, or camera-based vehicle detection
- **Machine Learning** — Predict traffic patterns to dynamically tune priority weights
- **Multi-intersection Coordination** — Extend across a road network using graph algorithms (Dijkstra / A\*) for global signal optimisation
- **GPS Emergency Detection** — Detect approaching emergency vehicles earlier via real-time GPS dispatch feeds
- **Real Dataset Validation** — Benchmark against publicly available city traffic datasets