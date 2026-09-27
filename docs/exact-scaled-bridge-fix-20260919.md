# Native BigInteger smart-doubling units

The 00:32 modpack run loaded the previous waiting-window fix (SHA256
`82A84C7DACDCA8D85B014830366975295CDCA660F5632A2F9BE3AD8C407A964D`) but still stalled.
The log recorded native admission of 2,222,222 task units and only 2,222,222
base crafting operations. The screenshot showed a long-sized waiting count
for compressed solar sails and a much larger pending count.

UselessMod's public BigInteger target requires the original, unwrapped pattern
and one base recipe input prototype. Its commit unwraps smart-doubling patterns
and interprets count as base operations. Omni previously passed a scaled task
prototype and the number of scaled tasks without converting units. The CPU
therefore waited for scaled outputs while the machine produced base outputs.

The bridge now unwraps the explicit UselessMod ScaledPattern contract, divides
each prototype amount exactly by its multiplier, and multiplies task counts
using BigInteger before capacity/admission/commit. Native capacity is divided
back down to whole CPU tasks. Rejected or partial admissions leave caller
inputs untouched. The same base prototype array is used for admit and commit.
Callback expectations use the original task pattern output, without dividing
by an unrelated temporary extraction expansion factor.

Verification:

- Five adapter tests cover output conservation beyond long, whole-task capacity,
  rejected/partial admissions, ordinary patterns, and invalid prototypes.
- A development server loaded the modpack's unchanged UselessMod jar. The actual
  smart-doubling classes and public API interfaces passed the bridge smoke test
  (the target implementation is a controlled test proxy, not a built furnace).
- `NATIVE_SCALED_BRIDGE_SMOKE_PASSED`: 2,222,222 tasks produced
  2,049,638,025,448,349,358,782,760 base output units as expected.
- Actual transformed CPU window, serialization, completion and omniversal
  pattern tests also passed, including a 10^50 order.
- Runtime log: `exact-scaled-runtime.log`; release log: `exact-scaled-release.log`.

Full modpack furnace behavior still requires an in-game retest. Old jobs have
already consumed task counts using the wrong units; restart and submit a fresh
job instead of expecting this bridge conversion to reconstruct that history.
No player-world data or UselessMod code was changed.
