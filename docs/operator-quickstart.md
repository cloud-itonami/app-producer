# operator quickstart — app-producer

Walked end to end on 2026-08-18 from a fresh worktree of `cloud-itonami/main`
at `389fd48`. Every command below was run; the output shown is what it printed.

**Read this first:** nothing in this repository builds, and that is not a
misconfiguration you can fix locally — there is no build manifest to fix. See
§3. The useful operations here are *reading the record* and *verifying custody*.

Environment used: macOS 15 (`Darwin 25.3.0`), `node v26.3.0`, `nbb v1.4.210`,
`tsc 5.9.2`, `esbuild 0.28.0`, `git 2.x`.

## 1. Clone

```bash
git clone git@github.com:cloud-itonami/app-producer
cd app-producer
```

Inside the west superproject the checkout already exists at
`orgs/cloud-itonami/app-producer`, and its git remote is named `cloud-itonami`,
not `origin` — `git fetch origin` fails there with *"repository does not
exist"*. Use `git fetch cloud-itonami` and `cloud-itonami/main`.

## 2. See everything there is

```bash
$ git ls-files
NOTICE
PROJECT.jsonld
README.edn
appview/README.md
cdn/producer-ui/src/lib/grpc/transport.ts
cdn/producer-ui/src/lib/server/storyboardService.ts
migration.edn

$ git ls-files -z | xargs -0 wc -c | tail -1
    7433 total
```

Seven files, 7,433 bytes. That is the whole repository — there is no hidden
subtree and no submodule.

## 3. Confirm that nothing builds (and why)

```bash
$ git ls-files | grep -Ei 'package\.json|tsconfig|svelte\.config|vite\.config|deps\.edn|shadow-cljs'
(no output)
```

No build or dependency manifest of any kind. So the two TypeScript files cannot
resolve their imports:

```bash
$ esbuild --bundle cdn/producer-ui/src/lib/server/storyboardService.ts --outfile=/dev/null
✘ [ERROR] Could not resolve "@connectrpc/connect"
✘ [ERROR] Could not resolve "$lib/grpc/transport"
2 errors

$ esbuild --bundle cdn/producer-ui/src/lib/grpc/transport.ts --outfile=/dev/null
✘ [ERROR] Could not resolve "$app/environment"
1 warning and 1 error
```

A typecheck agrees. Pass modern flags explicitly, because with no `tsconfig.json`
`tsc` defaults to ES5 and adds errors that say more about the missing config than
about the code:

```bash
$ tsc --noEmit --strict --target es2022 --module es2022 --moduleResolution bundler \
    cdn/producer-ui/src/lib/grpc/transport.ts \
    cdn/producer-ui/src/lib/server/storyboardService.ts
# 9 errors, of which 3 are TS2307 "Cannot find module"
```

Run it without those flags and you get 15 errors instead; the extra six are
ES5-target artifacts (`Promise`, `Array.prototype.find`). **Do not report the
larger number as a defect count.**

`npm install` is not a step here — there is no `package.json` to install from.

## 4. Verify custody against the monorepo it came from

This is the one check that proves the repository is intact. `migration.edn`
pins the source:

```bash
$ cat migration.edn
{:schema "etzhayyim.migration/extracted-v1"
 :source {:repo "etzhayyim/root"
          :path "60-apps/etzhayyim-project-producer"
          :revision "691c245da48f3acb11dd757218f189ff2482b1c8"
          :git-tree "37a4aac95d8eeedb76096b73fa9122b919c798ae"
          :tracked-files 5 :bytes 6744} ...}
```

With a checkout of `etzhayyim/root` available (in the superproject it is at
`orgs/etzhayyim/root`), compare blob SHAs path-for-path:

```bash
R=~/github/com-junkawasaki
REV=691c245da48f3acb11dd757218f189ff2482b1c8
for f in NOTICE PROJECT.jsonld appview/README.md \
         cdn/producer-ui/src/lib/grpc/transport.ts \
         cdn/producer-ui/src/lib/server/storyboardService.ts; do
  dst=$(git rev-parse "HEAD:$f")
  src=$(git -C $R/orgs/etzhayyim/root rev-parse "$REV:60-apps/etzhayyim-project-producer/$f")
  [ "$dst" = "$src" ] && echo "ok   $f" || echo "DIFF $f"
done
```

Result on 2026-08-18: **5 identical, 0 differing.** The upstream subtree is also
exactly 5 files / 6,744 bytes with tree `37a4aac9`, so all four numbers in
`migration.edn` are correct.

The repository has 7 files rather than 5 because the extraction added
`README.edn` and `migration.edn` itself. That is not a discrepancy.

## 5. Re-measure everything this documentation claims

```bash
nbb docs/verify-docs-claims.cljs
```

- `0` — every claim re-measured and matched.
- `1` — a claim is now false. The message names which.
- `3` — **the check could not be made**: not run from the repository root, `git`
  unavailable, or no commits. Distinct from 0 on purpose, so that a run which
  never measured anything cannot be mistaken for a clean one.

The custody check of §4 needs a second repository and so is *not* folded into
the verifier: if `etzhayyim/root` were merely absent, a naive check would return
the same value as one that found a mismatch. Run §4 by hand.

## 6. What you cannot answer from here

- **Where the producer service actually runs.** `appview/README.md` names the App
  service `producer-services-8y9ctzzc` and a facade `producer-mcp-component`, and
  lists the latter as implemented. Neither is in this repository.
- **Whether `com.etzhayyim.apps.producer.*` XRPC methods still exist.** The
  client edge in `transport.ts` names them; no lexicon here defines them.
- **What this project is for.** `PROJECT.jsonld` has `"description": "TBD"`,
  `status: "Planned"`, no capability terms, and both survival indicators `null`.

These are owner questions, recorded in
[`adr/2608181500-app-producer-inherited-gaps.edn`](adr/2608181500-app-producer-inherited-gaps.edn).
