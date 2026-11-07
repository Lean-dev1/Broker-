# ADR-0006 — Prospective source-available proprietary licensing

- **Status:** Proposed; requires legal review before adoption
- **Date:** 2026-11-07
- **Scope:** Licensing and distribution policy only; no runtime code changes

## Context

Broker- is a publicly hosted Java matching-engine simulator intended to
demonstrate technical quality and potentially support future commercial
licensing to financial-technology organizations.

The repository was publicly distributed under the permissive MIT License.
The project owner now prefers public source visibility for prospective
customers **without a general grant to deploy or reuse new proprietary
implementations**.

## Decision proposed

Replace the top-level MIT license notice with custom
`SOURCE-AVAILABLE EVALUATION TERMS` for future Covered Material, while
preserving an explicit acknowledgment of prior MIT rights.

The permitted use is reading source in GitHub for technical evaluation.
No independent permission to run, integrate, modify, distribute, or
commercialize newly Covered Material is granted. Interested organizations
must negotiate a separate written agreement.

Publish a prominent English-language notice in the repository README and
a `LICENSING.md` policy explaining scope, legal limits, and commercial
contact options.

## Reasons

- Source visibility allows engineering evaluation before a sales discussion.
- A prospective proprietary grant provides a licensing model for future
  original code distinct from permissive open-source distribution.
- An explicit transition statement avoids misleading stakeholders about
  rights previously granted under MIT.
- A separate legal review prevents adopting bespoke contract terms as if
  they were standard, legally vetted licenses.

## Alternatives

| Option | Why not selected at present |
| --- | --- |
| Keep MIT | Permits commercial use and redistribution of the licensed versions |
| GPL/AGPL | Still allows commercial use subject to copyleft requirements |
| BSL 1.1 | Offers a delayed open-source transition, which is not the current objective |
| Private repository only | Stronger access control but reduced public code-review visibility |
| Source-available evaluation terms | Closest to the stated objective but requires custom legal review |

## Constraints, risks, and non-goals

1. **No retroactive relicensing:** previously distributed MIT versions retain
   their original grant. Changing `LICENSE` does not withdraw those rights.
2. **Hosting-platform rights:** GitHub's Terms of Service allow public viewing
   and forking and grant GitHub certain hosting/content permissions.
3. **No technical copy prevention:** public access exposes readable source.
   License restrictions are legal conditions, not digital-rights management.
4. **Contributor and dependency rights:** the copyright holder cannot impose
   new terms on third-party content without sufficient rights.
5. **Unreviewed contract:** the proposed terms must be reviewed by counsel
   familiar with the applicable jurisdictions and planned customer contracts.
6. **No engineering impact:** this ADR does not alter trading semantics,
   matching algorithms, ledger integrity, or production readiness.
7. **Avoid overclaiming exclusivity:** code already published under MIT may
   still be used under MIT; new proprietary differentiators must be created
   and tracked prospectively.

## Follow-up

- Complete a legal review of the proposed terms and actual ownership of
  code and contributions before merging.
- Keep a provenance record distinguishing earlier MIT material from new
  proprietary changes.
- Consider a separate private repository for future commercial-only
  components and a public demonstration if stronger exclusivity is needed.
- Review prior permissions granted to third-party indexing projects separately.
