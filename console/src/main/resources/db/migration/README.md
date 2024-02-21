<!--
Copyright 2024 Logan Magee

SPDX-License-Identifier: AGPL-3.0-only
-->

# Migrations

## Making changes

When adding a versioned migration, please reference [Flyway's documentation].

Existing migration scripts can be modified before Parcelo includes them in a release. However, once
a script is included in a release, it MUST NOT be modified at all from that point forward. Parcelo
verifies migration checksums on startup, so even slight, non-functional modifications (such as
modifying a code comment) will result in verification failure and Parcelo will not start.

[Flyway's documentation]: https://documentation.red-gate.com/fd/migrations-184127470.html
