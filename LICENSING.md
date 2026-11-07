# Broker- Licensing Policy

**Current proposed policy:** Proprietary source-available, evaluation by
reading the public code only. This policy is intended to apply prospectively
to new copyrightable material and future versions subject to the license
holder's rights.

> **Legal-review notice:** This is a proposed licensing transition. The
> custom terms in [LICENSE](LICENSE) have not been reviewed by qualified
> legal counsel. Review the transition and any third-party or contributor
> rights before merging the licensing PR or relying on the terms in sales.

## What visitors may do

Visitors and prospective customers may read and inspect Broker- source code
on GitHub to evaluate its architecture, implementation style, documentation,
and test coverage. GitHub's own Terms of Service may also allow viewing,
displaying, and forking repositories through GitHub.

**Viewing is not a grant to run or integrate Covered Material.** Apart from
rights under earlier valid licenses, mandatory law, GitHub platform terms,
or a separate written agreement, the new Covered Material is not licensed
for compiling, testing, deploying, copying elsewhere, modifying, embedding,
redistributing, sublicensing, or selling.

For permission to evaluate through a running proof of concept, to use the
software in a commercial environment, or to negotiate enterprise terms,
contact the repository owner through an appropriate business channel.
A public GitHub issue is suitable for an initial non-confidential inquiry;
do not share proprietary or confidential information in public issues.

## Crucial exception: previous MIT releases

Broker- was publicly distributed under the MIT License before this proposed
transition. For example, the `main` revision
[`134d341ff7fc75b820cbe94f12746d83db8fb640`](https://github.com/Lean-dev1/Broker-/tree/134d341ff7fc75b820cbe94f12746d83db8fb640)
contains the original MIT license:

[`LICENSE` at the previous MIT-licensed revision](https://github.com/Lean-dev1/Broker-/blob/134d341ff7fc75b820cbe94f12746d83db8fb640/LICENSE).

Those permissions are not retroactively withdrawn. Parties who obtained
copies under that license can generally continue to use those versions
subject to the original MIT conditions. Changing the repository's license
notice **does not make the already-released matching engine or ledger code
exclusive**. Do not describe the existing historical code as if others
could no longer exercise their MIT rights.

The prospective proprietary restrictions are intended for new, independently
copyrightable material lawfully published under the replacement terms.
Where an older MIT-licensed component remains available under MIT, its
previously granted rights remain available.

## Source visibility, GitHub, and practical limits

A public GitHub repository does not guarantee exclusive access to the
source. GitHub users retain the viewing and forking permissions that
GitHub grants under its Terms of Service; historical forks and copies may
continue to exist. Publishing code also exposes it to inspection and
potential unauthorized copying that license terms alone cannot technically
prevent.

Licensing limits unauthorized uses of covered expression; it does not
create ownership over abstract algorithms, public ideas, or independent
implementations. GitHub's own service and AI-related content permissions
should also be assessed separately when deciding what to publish.

For high-value future proprietary infrastructure, evaluate keeping
commercial additions in a private repository and publishing a deliberately
limited demonstration, specifications, or benchmarks instead.

## Commercial licensing and contribution hygiene

A commercial agreement should specify allowed environments, source access,
redistribution, permitted integrations, support, updates, indemnities,
security obligations, and treatment of customer modifications.

Before accepting third-party code contributions, verify that all necessary
licensing or assignment permissions have been granted. Dependencies and
third-party code retain their existing license obligations, regardless of
this repository's top-level policy.

## Legal references

- [GitHub documentation: Licensing a repository](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository)
- [GitHub Terms of Service](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service)
- [GitHub Choose a License: No license](https://choosealicense.com/no-permission/)

These notes are documentation of an intended business policy, not legal
advice or a substitute for professional review.
