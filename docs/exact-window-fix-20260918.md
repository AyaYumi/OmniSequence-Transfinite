# Exact output window continuation

The previous task is `01a0ae5f-5662-71d1-8a97-89cfe39dd641`.

## Confirmed defect

`CraftingCpuLogic.insert` settles `job.waitingFor` before calling the final
output link. A standalone link returns zero so the machine can return those
items to general network storage. Omni previously replenished its BigInteger
output window only when `insert` returned a positive amount. As a result, the
first long window could settle without exposing the remaining output credits.

The replenishment hook now wraps the actual waiting inventory extraction. Only
a positive MODULATE extraction claims pending credits; SIMULATE leaves them
unchanged. This preserves the original return value and item ownership.

## Verification

- Added a transformed CPU regression with `Long.MAX_VALUE + 10` final output.
- Before the fix, it fails at the assertion that the remaining 11 items enter
  the waiting window (`exact-window-regression-before.log`).
- After the fix, it passes, including simulation, save/restore, and completion
  (`exact-window-regression-after.log`, `EXACT_CPU_SMOKE_PASSED`).
- `gradlew.bat clean build --offline`: passed, 159 tests, no failures/errors.
- Release jar excludes the development-only `ExactCpuSmoke` class.
- Built jar deployed with a timestamped backup to the existing modpack mods
  directory; source and deployed SHA256 matched. UselessMod jar unchanged.

The development server did not include UselessMod. This confirms the CPU
accounting defect and fix, but the complete modpack/machine scenario still
needs an in-game retest. Its latest log is from the previous 21:55–21:59 session.
Restart and submit a fresh over-long job for that test; already-stalled jobs
may retain old waiting state. No player world was edited.
