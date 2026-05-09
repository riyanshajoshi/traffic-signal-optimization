// ============================================================
// Scheduler.java
//
// Implements a custom INDEXED MAX-HEAP for O(log n) per-cycle
// traffic lane scheduling.
//
// WHY A CUSTOM HEAP?
// ──────────────────
// Java's PriorityQueue does not support in-place key updates.
// Changing a lane's priority requires:
//   (a) O(n) remove() scan + O(log n) re-offer, or
//   (b) O(n log n) full clear() + rebuild.
//
// The indexed heap solves this with a pos[] index array:
//   pos[lane.id] = current position of that lane in heap[]
//
// When a lane's priority changes, we locate it in O(1) via pos[]
// and restore the heap with a single siftDown in O(log n).
//
// COMPLEXITY GUARANTEE
// ─────────────────────
//   insert()  →  O(log n)   siftUp from the bottom
//   peekMax() →  O(1)       reads heap[0]
//   update()  →  O(log n)   one siftUp + one siftDown
//   scheduleNext() overall → O(log n) per cycle
// ============================================================
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Scheduler {

    // ── Internal heap storage ────────────────────────────────
    private final Lane[] heap;  // binary max-heap of lanes

    /**
     * pos[lane.id] = current index of that lane in heap[].
     * Updated on every swap, allowing O(1) lane lookup.
     */
    private final int[]  pos;

    private int size;
    private int currentCycle;

    // ── Constructor ──────────────────────────────────────────
    public Scheduler(int capacity) {
        heap = new Lane[capacity];
        pos  = new int[capacity];
        Arrays.fill(pos, -1); // -1 means "not in heap"
        size         = 0;
        currentCycle = 0;
    }

    // ── Cycle reference ──────────────────────────────────────
    /**
     * Sets the current cycle so all priority comparisons inside
     * siftUp / siftDown use the correct effective values from the
     * lazy virtual clock. Must be called before each cycle.
     * Time complexity: O(1)
     */
    public void setCycle(int cycle) { this.currentCycle = cycle; }
    public int  getCurrentCycle()   { return currentCycle; }

    // ── State queries ────────────────────────────────────────
    public boolean isEmpty() { return size == 0; }
    public int     size()    { return size; }

    // ── Insert — O(log n) ────────────────────────────────────
    /**
     * Adds a lane to the heap. Places it at the end, then
     * sifts up until the max-heap property is restored.
     * Time complexity: O(log n)
     */
    public void insert(Lane lane) {
        int i        = size++;
        heap[i]      = lane;
        pos[lane.id] = i;
        siftUp(i);
    }

    // ── Peek — O(1) ─────────────────────────────────────────
    /**
     * Returns the highest-priority lane without removing it.
     * In a max-heap, the root (index 0) always holds the maximum.
     * Time complexity: O(1)
     */
    public Lane peekMax() {
        if (isEmpty()) throw new IllegalStateException("Scheduler has no lanes.");
        return heap[0];
    }

    // ── Schedule next — O(log n) ─────────────────────────────
    /**
     * Executes one scheduling step:
     *   1. Peek max lane           → O(1)
     *   2. Serve it (update state) → O(1)
     *   3. Restore heap via update → O(log n)
     *
     * Non-served lanes are NEVER touched here. Their priority
     * grows implicitly via the lazy clock inside computePriority().
     *
     * Time complexity: O(log n)
     *
     * @param cycleNumber the current 1-based cycle index
     * @param prevServed  the lane served last cycle (to reset to RED), or null
     * @return the lane that received the green signal
     */
    public Lane scheduleNext(int cycleNumber, Lane prevServed) {
        setCycle(cycleNumber);

        // Reset previously served lane's signal to RED — O(1)
        if (prevServed != null) prevServed.resetSignal();

        // Greedy choice: always serve the max-priority lane — O(1)
        Lane served = peekMax();
        served.serve(cycleNumber); // sets GREEN, resets lazy clock — O(1)

        // Restore heap after served lane's priority dropped — O(log n)
        update(served.id);

        return served;
    }

    // ── Targeted update — O(log n) ───────────────────────────
    /**
     * Restores heap property after a lane's priority changes in-place.
     * Uses pos[laneId] to locate the lane in O(1), then sifts in
     * whichever direction is needed. Only one direction will actually
     * move the element; the other returns immediately.
     *
     * Time complexity: O(log n)
     */
    public void update(int laneId) {
        int p = pos[laneId];
        if (p < 0 || p >= size) return;
        siftUp(p);
        siftDown(pos[laneId]); // re-read pos: siftUp may have moved the lane
    }

    // ── Snapshot for display ─────────────────────────────────
    /**
     * Returns a sorted copy of all lanes for display purposes.
     * NOT part of the scheduling algorithm.
     * Time complexity: O(n log n)
     */
    public List<Lane> getSortedSnapshot() {
        List<Lane> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(heap[i]);
        list.sort((a, b) -> Double.compare(
            b.computePriority(currentCycle),
            a.computePriority(currentCycle)));
        return list;
    }

    // ── Internal: priority comparison — O(1) ────────────────
    /** True if heap[i] has strictly higher priority than heap[j]. */
    private boolean higherPriority(int i, int j) {
        return heap[i].computePriority(currentCycle)
             > heap[j].computePriority(currentCycle);
    }

    // ── Internal: siftUp — O(log n) ─────────────────────────
    /**
     * Moves element at index i upward until the parent has equal
     * or higher priority. Traverses at most log₂(n) levels.
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

    // ── Internal: siftDown — O(log n) ───────────────────────
    /**
     * Moves element at index i downward, swapping with the larger
     * child at each level until the heap property holds.
     * Traverses at most log₂(n) levels.
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

    // ── Internal: swap — O(1) ───────────────────────────────
    /** Swaps two heap positions and keeps pos[] consistent. */
    private void swap(int i, int j) {
        pos[heap[i].id] = j;
        pos[heap[j].id] = i;
        Lane tmp = heap[i];
        heap[i] = heap[j];
        heap[j] = tmp;
    }
}