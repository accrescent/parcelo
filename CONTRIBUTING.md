<!--
Copyright 2024 Logan Magee

SPDX-License-Identifier: AGPL-3.0-only
-->

# Contributing guidelines

Thank you for your interest in contributing to Parcelo! Please read below for development setup
steps.

## Development setup

The recommended and supported development environment for Parcelo is Intellij IDEA (the community
edition is fine). You will also need to install npm and the protobuf compiler. This repository comes
with convenience IDEA run configurations to ease development. However, you will need to configure a
few things yourself. After installing IDEA and cloning the repository, follow the steps below to get
started.

1. Open the project in IDEA and install the required EnvFile plugin as prompted.
2. Navigate to your [GitHub developer settings] and click "Register a new application."
3. Set the "Authorization callback URL" field to `http://localhost:8080/auth/github/callback`. Fill
   in the other fields with whatever you like.
4. Click "Register application."
5. Copy the client ID from the resulting page into a file named `.env` with the contents
   `GITHUB_OAUTH2_CLIENT_ID=${client_id}`, replacing `${client_id}` with your client ID.
6. Click "Generate a new client secret" and copy the secret into a new line in `.env` with the
   contents `GITHUB_OAUTH2_CLIENT_SECRET=${client_secret}`, replacing `${client_secret}` with your
   client secret.
7. Navigate to `https://api.github.com/users/${username}` where `${username}` is your GitHub
   username. Copy the `id` field into a new line in `.env` as `DEBUG_USER_GITHUB_ID=${id}` where
   `${id}` is the ID you copied.
8. Change to the `frontend directory` and run `npm ci`.

You should now be able to run the console and repository servers in IDEA by selecting the
"development" run configuration and running the project. The frontend can be served locally by
running `ng serve` in the `frontend` directory.

The environment variables in the included IDEA run configurations may be modified as needed.
However, we don't recommend doing this unless you know what you're doing. The defaults should work
fine for most use cases.

## Contributor license agreement

Contributing to Parcelo requires signing a Contributor License Agreement (CLA). To sign [Parcelo's
CLA], make a PR, and our CLA bot will direct you.

[GitHub developer settings]: https://github.com/settings/developers
[Parcelo's CLA]: https://gist.github.com/lberrymage/21603f43c6c018001e31d441125ad5de
