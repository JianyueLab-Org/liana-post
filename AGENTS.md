Encoding Rules

- All source files must remain UTF-8 without BOM.
- Never use Out-File.
- Never use Set-Content for source code files.
- Preserve original file encoding.
- Preserve LF line endings.

Database Changes

- Follow `docs/database-design-standard.md` for database structure, initialization, seed data, and migration changes.
- Coordinate manifest-related database changes with `docs/manifest-database-redesign-plan.md`.
- These documents distinguish target conventions from the current initialization paths; do not assume the planned database consolidation is already implemented.
