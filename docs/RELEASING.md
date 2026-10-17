# Releasing a new version

1. Bump `<version>` in `pom.xml`, update `CHANGELOG.md`, build the jar.
2. Commit and push. Make sure `docs/images/` is pushed too (the Spigot page loads its images from GitHub).
3. GitHub -> Releases -> Draft a new release:
   - Tag: `v2.0.0` (must be HIGHER than the installed version, e.g. `v2.0.1` next time)
   - Attach the jar (any name ending in `.jar`; do not attach a "-sources" jar).
   - Publish as a normal release (not draft / pre-release). The in-game updater reads `releases/latest`.
4. Spigot -> your resource -> Post Resource Update:
   - Title: `2.0.0 – The Big Update`, Version: `2.0.0`
   - Message: paste `docs/spigot-update-2.0.0.txt` (without the first two lines).
   - File: upload the jar here too (or choose "external download" -> the GitHub release URL).
5. Spigot -> Edit Resource -> Description: paste `docs/spigot-page.txt` once.
