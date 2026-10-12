# JSONTestSuite snapshot

`test_parsing/` is a verbatim copy of [nst/JSONTestSuite](https://github.com/nst/JSONTestSuite) at commit `1ef36fa`
(2024-11-22), MIT licensed (see `LICENSE`). Files named `y_*` must be accepted, `n_*` rejected, and `i_*` are
implementation-defined. `GhostJsonTestSuiteTest` runs them on every channel; its deviation lists document where Ghost
differs and why.

## json.org JSON_checker suite

`json-org/` holds the 36 files of the original suite published by Douglas Crockford at
<https://www.json.org/JSON_checker/test.zip> (`pass1`-`pass3`, `fail1`-`fail33`), unchanged. The archive ships without a
license file; the files are tiny test inputs that parsers have redistributed for years. `GhostJsonOrgTestSuiteTest`
runs them on every channel; `fail1` (a bare string at the root, valid since RFC 8259) and `fail18` (20 nested arrays,
a depth limit the suite leaves optional) are accepted on purpose.
