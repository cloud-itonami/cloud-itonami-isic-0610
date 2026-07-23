# Contributing

`cloud-itonami-isic-0610` accepts contributions to the OSS blueprint, the
well-safety governor's checks, jurisdiction citations in `crude.facts`,
policy tests, documentation and operator model.

## Development
This vertical is SELF-CONTAINED: the well-safety range checks (reservoir-
pressure window, annular-vs-MAASP, water cut, H2S-vs-IDLH) live as pure
functions in `crude.registry`, on top of the generic
`kotoba-lang/langgraph`/`kotoba-lang/langchain` actor runtime. There is no
separate bespoke domain capability library to wrap.

```bash
clojure -M:dev:test
clojure -M:lint
```

## Rules
- Do not commit real well, lease/concession or production data.
- Keep well lift and production settlement behind the Well Safety Governor.
- Treat well-safety workflows as high-risk: add tests for reservoir-pressure,
  annular-pressure/MAASP, water-cut, H2S/IDLH, integrity-flag and
  double-actuation checks.
- Adding a jurisdiction to `crude.facts/catalog` requires a REAL, verifiable
  official spec-basis citation (owner authority, legal basis, provenance
  URL, required-evidence set) -- never fabricate a jurisdiction's
  requirements to make coverage look bigger.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
