# app-producer

`etzhayyim producer`, extracted from the etzhayyim monorepo. **This repository is
a project record plus two orphaned UI fragments. It is not a runnable
application, and nothing in it can currently be built, typechecked, or tested.**

Every number on this page was measured on 2026-08-18 (macOS 15, node v26.3.0,
tsc 5.9.2, esbuild 0.28.0) and is re-measured by
[`docs/verify-docs-claims.cljs`](docs/verify-docs-claims.cljs). Run that before
trusting any of them.

| path | tracked | state |
|---|---|---|
| `cdn/producer-ui/src/lib/` | 2 files / 2,732 B | two SvelteKit fragments. **No build manifest exists anywhere in this repository**, so neither file can be resolved, compiled, or run here |
| `PROJECT.jsonld` | 2,958 B | project record. `description` is `"TBD"`, `status` is `"Planned"`, `capabilities.terms` is empty, and both `survivalIndicators` carry `value: null` |
| `appview/README.md` | 541 B | migration plan. Lists `producer-mcp-component` under **実装済み** (implemented); that component is not in this repository |
| `NOTICE` | 513 B | Apache-2.0 + etzhayyim Charter Rider v3.1. Directs the reader to `CHARTER-RIDER.md`, which is not in this repository |
| `README.edn` + `migration.edn` | 689 B | canonical EDN records, added by the extraction |

Measured at `389fd48`, before this documentation was added: **7 tracked files,
7,433 bytes, 3 commits**, last commit 2026-08-11. There are zero test files, and
this commit adds none.

Start at [`docs/operator-quickstart.md`](docs/operator-quickstart.md). What needs
an owner's decision is recorded in
[`docs/adr/2608181500-app-producer-inherited-gaps.edn`](docs/adr/2608181500-app-producer-inherited-gaps.edn).

## Custody is exact — nothing here was broken by the extraction

`migration.edn` says it copied 5 files / 6,744 bytes from `etzhayyim/root`
at revision `691c245d`, subtree `60-apps/etzhayyim-project-producer`, git-tree
`37a4aac9`. All four claims verify:

- that subtree at that revision holds exactly **5 files totalling 6,744 bytes**,
- its tree object is **`37a4aac95d8eeedb76096b73fa9122b919c798ae`**,
- and all **5 blobs are byte-identical here**, SHA-for-SHA.

So every defect below is inherited from the monorepo, not introduced by the
migration, and the fix for each belongs upstream as well as here. The two files
`migration.edn` does not count (`README.edn`, `migration.edn`) are the canonical
records the extraction itself added — the count is correct, not short by two.

## Why nothing builds

There is **no `package.json`, `tsconfig.json`, `svelte.config.js`,
`vite.config.ts`, `deps.edn`, or `shadow-cljs.edn`** anywhere in the repository —
zero build or dependency manifests of any kind. The two TypeScript files import
three modules, and none of them can resolve:

| specifier | needs | in this repo |
|---|---|---|
| `$app/environment` | SvelteKit's generated alias | absent |
| `$lib/grpc/transport` | SvelteKit's `$lib` alias, which would have to point at `cdn/producer-ui/src/lib` | absent |
| `@connectrpc/connect` | npm dependency | undeclared and uninstalled |

Measured, not asserted — `esbuild --bundle` reports exactly these:

```
$ esbuild --bundle cdn/producer-ui/src/lib/server/storyboardService.ts --outfile=/dev/null
✘ [ERROR] Could not resolve "@connectrpc/connect"
✘ [ERROR] Could not resolve "$lib/grpc/transport"
2 errors

$ esbuild --bundle cdn/producer-ui/src/lib/grpc/transport.ts --outfile=/dev/null
✘ [ERROR] Could not resolve "$app/environment"
1 warning and 1 error
```

`tsc --noEmit --strict --target es2022 --module es2022 --moduleResolution bundler`
over both files exits non-zero with **9 errors, 3 of them TS2307** (the three
unresolvable specifiers above). The other 6 follow from the same cause or from
the absent manifest: 4 × TS18046 are the `catch` block failing to narrow on
`error instanceof ConnectError` because `ConnectError` never resolved, and
2 (TS2339 `import.meta.env`, TS2580 `process`) are the ambient types a
`vite/client` + `@types/node` setup would have supplied.

**Do not read the raw `tsc` error count as a code-quality signal.** Run without
those flags — i.e. with the ES5 defaults you get when no `tsconfig.json` exists —
the same two files report 15 errors, and the extra six (`Promise` requires
ES2015, `Array.prototype.find` missing) are artifacts of the missing config, not
defects in the source.

## What this repository is not

`cdn/producer-ui/src/lib/grpc/transport.ts` posts to
`com.etzhayyim.apps.producer.<method>` over XRPC and reads a Clerk `__session`
cookie for its bearer token; `storyboardService.ts` maps Connect RPC codes onto
HTTP status codes for a `StoryboardService`. Both are *client edges* of a
service that lives somewhere else — neither implements the producer service, and
this repository contains no server, no lexicon, and no schema for it.

`README.edn` places the boundary: `:role :media-production-application`, with
`:media-service "cloud-itonami/media"`. `appview/README.md` names the App service
as `producer-services-8y9ctzzc` and the facade as `producer-mcp-component`.
Neither is in this repository; the second is listed there as already implemented.

## Verifying

```bash
nbb docs/verify-docs-claims.cljs
```

Exit `0` = every claim re-measured and matched, `1` = a claim is now false,
**`3` = the check could not be made at all** (not run from the repository root,
`git` unavailable, no commits). 3 is deliberately distinct from both: a check
that could not run must not be readable as a check that passed.

Custody, the toolchain versions, and the exact commands are in
[`docs/operator-quickstart.md`](docs/operator-quickstart.md).
