# BigInteger machine parallelism

The UselessMod 2.3.8.2test integration now separates two machine limits:

- `coil_tier_N_threads` is the number of physical BigInteger lanes. One native admission is divided across the currently available lanes, and each lane owns an independent output ledger. Lanes remain occupied until their output ledger drains, so normal AE tasks cannot oversell the same coil slots.
- `coil_tier_N_single_task_parallel` is the per-lane recipe parallelism. The BigInteger material window now includes this factor instead of using only the lane count.

CPU callbacks remain one batch-level callback. The lane group aggregates completion and exact outputs, so splitting the machine work does not duplicate CPU accounting or completion notifications. Cancellation sends one terminal notification for the group.

The modified Useless classes were compiled against the current Java 21/NeoForge runtime class path and inserted into the deployed `useless_mod-1.21.1-2.3.8.2test.jar`. The original JAR is retained beside it with a timestamped backup.
