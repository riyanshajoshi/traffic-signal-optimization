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
- A **custom Indexed Max-Heap** for O(1) peek and O(log n) in-place updates
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
1. Set current cycle on the heap (lazy clock reference) → O(1)
2. Peek at the max-priority lane                        → O(1)
3. Serve it — update state, set signal GREEN            → O(1)
4. Restore heap with a single targeted sift             → O(log n)
5. Non-served lanes — no update at all (lazy clock)     → O(1)
──────────────────────────────────────────────────────────────
Total per cycle                                         → O(log n) ✓
```

```
┌──────────────────────────────────────────────────────────┐
│                  INDEXED MAX-HEAP                         │
│                                                           │
│  heap[]   [ Lane East | Lane North | Lane South | ... ]  │
│  pos[]    [ id=0→idx  | id=1→idx  | id=2→idx  | ... ]   │
│                                                           │
│  peekMax()  →  O(1)      reads heap[0]                   │
│  update(id) →  O(log n)  pos[id] locates lane instantly, │
│                           then a single siftDown restores │
│                           order without any rebuild       │
└──────────────────────────────────────────────────────────┘
```

---

## Priority Formula

```
Priority = (vehicle_count × effective_waiting_time)
         + effective_fairness_factor
         + EMERGENCY_BONUS      ← added if emergency vehicle present
```

Effective values are derived lazily at comparison time — nothing is stored:

```
skipped               = currentCycle - lastServedCycle
effective_wait        = baseWaitingTime + skipped × 20
effective_fairness    = skipped × 5
```

| Parameter | Description | Value |
|---|---|---|
| `vehicle_count` | Number of vehicles currently waiting | User / sensor input |
| `effective_wait` | Grows automatically each skipped cycle | +20s per skip |
| `EMERGENCY_BONUS` | Flat bonus when emergency vehicle is present | `10,000` |
| `effective_fairness` | Anti-starvation boost derived from skipped cycles | +5 per skip |
| `lastServedCycle` | Cycle index when lane last received green — reset on serve | — |

---

## How O(log n) Is Achieved

Two techniques together eliminate the O(n) and O(n log n) costs of a naive implementation.

### 1. Indexed Max-Heap (`Scheduler.java`)

A custom binary heap maintains a `pos[]` index array alongside `heap[]`:

```
heap[] = [ LaneA,  LaneC,  LaneB,  LaneD  ]
pos[]  = [ id=0→2, id=1→0, id=2→3, id=3→1 ]
```

`pos[lane.id]` always holds the lane's current position in `heap[]`. When a lane's priority changes, the heap locates it in **O(1)** and calls a single **siftDown in O(log n)** — instead of a full O(n log n) rebuild.

### 2. Lazy Virtual Clock (`Lane.java`)

Each lane stores only two anchor values:
- `baseWaitingTime` — the wait at the moment it was last served
- `lastServedCycle` — which cycle it was last served

The effective waiting time and fairness are derived on-demand inside `computePriority(currentCycle)`:

```java
int  skipped       = currentCycle - lastServedCycle;
long effectiveWait = baseWaitingTime + (long) skipped * WAIT_INCREMENT;
long fairness      = (long) skipped * FAIRNESS_INCREMENT;
```

Non-served lanes are **never touched**. Their growing urgency is implicit. This brings the "update non-served lanes" step from **O(n) → O(1)**.

### Result

| Step | Naive | This implementation |
|---|---|---|
| Restore heap order | O(n log n) full rebuild | **O(log n)** single sift |
| Update non-served lanes | O(n) explicit loop | **O(1)** lazy clock |
| **Total per cycle** | **O(n log n)** | **O(log n)** ✓ |

---

## Complexity Analysis

| Operation | Time Complexity | Notes |
|---|---|---|
| `computePriority()` | O(1) | Fixed arithmetic using lazy clock |
| `heap.peekMax()` | O(1) | Reads `heap[0]` |
| `heap.insert()` | O(log n) | siftUp — called at startup only |
| `heap.update()` | **O(log n)** | Targeted siftDown via `pos[]` |
| Update served lane state | O(1) | Field assignments only |
| Update non-served lanes | **O(1)** | Lazy virtual clock — no loop |
| **Total per cycle** | **O(log n)** | ✓ |
| **Space complexity** | **O(n)** | `heap[]` and `pos[]` both size n |

For k cycles across n lanes: **O(k log n)** overall.

---

## Project Structure

```
traffic-signal-optimizer/
│
├── src/
│   ├── Main.java               ← Entry point only; delegates to Simulation
│   ├── Vehicle.java            ← Base class: id, waitingTime
│   ├── EmergencyVehicle.java   ← Extends Vehicle; ServiceType enum (AMBULANCE, FIRE_TRUCK, POLICE)
│   ├── TrafficSignal.java      ← Signal state machine: RED / GREEN / YELLOW
│   ├── Lane.java               ← Core lane model; lazy clock; priority formula
│   ├── Scheduler.java          ← Indexed max-heap; O(log n) insert + update
│   └── Simulation.java         ← Orchestration; demo and interactive modes
│
└── README.md
```

**Responsibility of each class:**

| File | Responsibility |
|---|---|
| `Main.java` | Parse mode choice, delegate to `Simulation` |
| `Vehicle.java` | Track individual vehicle ID and wait time |
| `EmergencyVehicle.java` | Extend `Vehicle` with a `ServiceType`; triggers priority bonus |
| `TrafficSignal.java` | Own signal state (RED/GREEN/YELLOW) and green duration config |
| `Lane.java` | Hold vehicle count, signal, lazy clock anchors; compute priority in O(1) |
| `Scheduler.java` | Indexed max-heap — pure algorithm, no I/O |
| `Simulation.java` | Wire everything together; manage lane list; print results; run scenarios |

---

## Getting Started

### Prerequisites

- **Java 11 or higher**

  ```bash
  java -version
  ```

### Clone, Compile & Run

```bash
# Clone the repository
git clone https://github.com/<your-username>/traffic-signal-optimizer.git
cd traffic-signal-optimizer

# Compile all source files into out/
javac -d out src/*.java

# Run
java -cp out Main
```

---

## Running the Program

```
Intelligent Dynamic Traffic Signal Optimization
Using Greedy Algorithms & Priority Queues

Select mode:
  1 → Automated Demo
  2 → Interactive Simulation
Choice [1/2]:
```

### Mode 1 — Automated Demo

Runs 9 pre-configured cycles covering all three scenarios. No input required.

### Mode 2 — Interactive Simulation

Define your own lanes and drive the simulation cycle by cycle:

```
Enter number of lanes (min 2, max 8): 3

  Lane 1 name: North
  Vehicle count for North: 12
  Waiting time for North: 30
  Emergency vehicle present? (y/n): n
  ...

CYCLE 1 — Options
  1 → Run cycle
  2 → Update a lane
  3 → Exit
```

---

## Demo Scenarios

### Scenario 1 — Standard Traffic Flow *(Cycles 1–2)*

Verifies the greedy choice selects the lane with the highest `vehicle_count × effective_wait` product, and that the lazy clock naturally grows priorities of waiting lanes.

| Cycle | Winner | Priority | Reason |
|---|---|---|---|
| 1 | Lane East | 600.0 | 15 vehicles × 40s wait |
| 2 | Lane North | 605.0 | Lazy clock grew effective wait to 50s |

### Scenario 2 — Emergency Vehicle Override *(Cycle 3)*

An ambulance is injected into **Lane West** via `updateLane()` — a single O(log n) heap sift, not a rebuild. `EMERGENCY_BONUS = 10,000` immediately elevates it above all lanes.

| Cycle | Winner | Priority | Reason |
|---|---|---|---|
| 3 | **Lane West 🚑** | **10,155.0** | `EmergencyVehicle(AMBULANCE)` bonus |

### Scenario 3 — Starvation Prevention *(Cycles 5–9)*

Heavy traffic is loaded onto North/South/East while West has 1 vehicle. The lazy fairness formula (`skipped × 5`) accumulates each cycle West is skipped — demonstrating that even the lightest lane is eventually served, at zero per-cycle cost.

---

## Sample Output

```
────────────────────────────────────────────────────────────────────────
  SCENARIO 2 — Emergency Vehicle Override
────────────────────────────────────────────────────────────────────────
  [EVENT] Ambulance detected in Lane West!

════════════════════════════════════════════════════════════════════════
  CYCLE  3
════════════════════════════════════════════════════════════════════════
  ✔  GREEN SIGNAL → Lane West        |  Priority Score = 10155.0

  ⚠  EMERGENCY OVERRIDE — AMBULANCE#1 cleared from Lane West

  ── Current Queue Status (highest priority first) ──
  1. Lane South      | vehicles=  8 | wait= 80s | signal=RED    | emergency=none | fairness= 15 | priority=   655.0
  2. Lane East       | vehicles=  5 | wait= 40s | signal=RED    | emergency=none | fairness= 10 | priority=   210.0
  3. Lane North      | vehicles=  2 | wait= 20s | signal=RED    | emergency=none | fairness=  5 | priority=    45.0
  4. Lane West       | vehicles=  0 | wait=  0s | signal=GREEN  | emergency=none | fairness=  0 | priority=     0.0
```

---

## Future Enhancements

- **IoT Sensor Integration** — Replace console input with live data from induction loops, radar, or camera-based detection
- **Machine Learning** — Predict traffic patterns to dynamically tune priority weights
- **Multi-intersection Coordination** — Extend across a road network using Dijkstra / A\* for global signal optimisation
- **GPS Emergency Detection** — Detect approaching emergency vehicles earlier via real-time GPS dispatch feeds
- **Real Dataset Validation** — Benchmark against publicly available city traffic datasets