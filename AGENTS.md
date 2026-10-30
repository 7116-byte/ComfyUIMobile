# ComfyUI Mobile development

- The active Git root is `E:\AI-Programme\ComfyUI_app`. This is a Kotlin / Jetpack Compose Android client, not the ComfyUI server.
- Read `docs/development.md` for the local toolchain and `docs/recovery-2026-10-30.md` for the migration evidence and preserved technical material.
- Run `./dev.ps1 check` for the bridge regressions, JVM tests, signed release build, lintVitalRelease, and APK identity checks. It uses local offline dependencies by default.
- `.local/` contains the original private signing key/configuration, SDK, JDK, dependencies and private recovery archive. Keep it ignored. Never print signing secrets or upload `.local/`.
- Preserve the original release certificate. An environment override `COMFY_SIGNING_PROPERTIES` may select another local configuration; do not create a replacement key for releases.
- This UI revision does not authorize changes to the ComfyUI server, models, workflows, VPN or tunnels. Test generation and phone installation only when requested. Report JVM/build evidence separately from actual phone acceptance.
- Saved server workflows, temporary imports, local drafts and editable working copies have different persistence rules. Preserve them when changing the UI. Existing technical boundaries are recorded in `.local/recovery/01_给AI的项目交接.txt`.
