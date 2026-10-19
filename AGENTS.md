# Project instructions

- `plan.txt` is maintained by the user. Do not edit, format, replace, or restore it unless the user explicitly requests changes to that file. A request to update a design or implementation plan does not authorize editing `plan.txt`.
- The Git-managed Android project is `android/`. Open that directory in Android Studio. Do not silently edit or synchronize the older copy in `C:/Users/TAKUMI/AndroidStudioProjects/PlanDMk1`.
- Design and implementation documents are in `docs/superpowers/plans/`. Distinguish planned behavior from implemented and tested behavior.
- The experimental PWA and non-storing relay are in `lab/`.
- Do not commit lab connection codes, private keys, signing keys, local configuration, device data, generated APKs, build directories, or dependencies. The existing Android debug public CA certificate is deliberately tracked because the debug configuration references it.
- Preserve existing records and source changes. Do not delete or reset unrelated work.
- Explain required device operations and expected wait time before a physical-device test. Do not block independent development on a second Android device.
