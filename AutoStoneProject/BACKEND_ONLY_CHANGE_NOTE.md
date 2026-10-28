# Backend-only dependency trigger fix

This package intentionally preserves the frontend application code and frontend build configuration from the previous project.

Changed for the dependency-trigger fix:
- BackendAutoStone/src/main/java/com/mybank/autostone/service/DependencyResolverService.java
- TEST_CBSGB_MONTHLY_EOD_B_FULL.jil
- examples/TEST_CBSGB_MONTHLY_EOD_B_FULL.jil
- DOWNSTREAM_DEPENDENCY_FIX.md (documentation)

No frontend source, package.json, vite.config.js, or frontend dependency versions were changed in this package.

JIL behavior:
ATS_ACTIVE_C SUCCESS -> ATS_START_C becomes eligible through condition s(ATS_ACTIVE_C)
ATS_START_C SUCCESS -> PREEOD box becomes eligible through condition s(ATS_START_C)

`auto_hold: y` is not present on ATS_START_C or the PREEOD box in the supplied test JIL.
