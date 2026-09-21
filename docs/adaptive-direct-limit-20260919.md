# Adaptive native batch window

This supersedes `omni_direct_native_batch_y` and its fixed 1–2Y ceiling.
Production admission now selects a learned BigInteger output window for each
verified physical sender/grid route. There is no fixed logical-amount maximum
in this controller.

Policy:

- Start at `omni_direct_native_initial_batch_y` (default 1Y), which is a seed,
  not an upper bound.
- If a return clears the queue, fills at least half the current window, finishes
  within `omni_direct_native_target_return_us` (default 500 us), and is no more
  than two ticks old, double the window at most once every five ticks.
- Slow returns, latency over two ticks, excessive outstanding output, or failed
  native commits halve the window, at most once per tick.
- Underfilled requests do not grow the window. Idle routes restart conservatively
  after 200 ticks. A new grid/route starts a fresh controller.
- New admissions reserve at most two current windows including the source's
  existing output backlog; stale queues stop new admission until they drain.
- The minimum viable scaled-task output is retained as a floor, preventing a
  shrunken window from permanently excluding one indivisible smart-doubling task.
- Shared direct-return time exhaustion yields until the next tick; it does not
  grant an unbounded amount of server-thread work.

The learned window uses aggregate output units, consistently with the furnace's
backlog measurement. Materials, native machine thread/input windows, energy,
native throttling and per-tick CPU/return work budgets still apply. The native
long segment-count projection saturates safely; the BigInteger control state
itself does not overflow or acquire a fixed Y ceiling.

Capacity and commit both consult the scoped window. A changed limit can reject
commit before input ownership moves. Unknown routes, foreign grids, final output
and ordinary providers retain the existing native path.

Validation: `AdaptiveDirectBatchLimitTest` covers repeated growth beyond long²,
same-tick growth suppression, underfilled requests, overload/backlog shrinkage,
recovery, indivisible-task floors, idle reset and overflow-safe projection.
`adaptive-direct-final-runtime.log` verifies the actual adapter uses the learned
window (including a observed decrease to 0.5Y in the fixture), while exact direct
delivery, partial acceptance, rollback and state transport still pass.
The fixed-ceiling config key is removed from the active modpack config; its old
value is carried forward only as the initial seed. Actual modpack throughput and
the eventual learned window require a restarted in-game workload.
