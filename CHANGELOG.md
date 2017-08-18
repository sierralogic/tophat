# Change Log
All notable changes to tophat will be documented in this file. 

This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

### Added
- Thread macros `ok->`, `some-ok->`, `some-success->`, `ok->>`, `some-ok->>` and `some-success->>` 

### Updated
- Updated README with thread macro examples

## [0.1.4] - 2017-08

### Added
- Lifting functions (take normal functions and wrap them to return HTTP response document with exception handling)

### Changed
- Updated README to cover `lift` and `lift-custom` examples

## [0.1.3] - 2017-07-30

### Added
- Macros `if-let-ok`, `when-let-ok`, and `when-let-not-ok`

### Changed
- Updated README to reflect new `let` macro functionality
- Updated README with more response/request examples
- Updated README with a new middleware handler section with examples

## 0.1.2 - 2017-07-30

### Added
- Core tophat functionality

[Unreleased]: https://github.com/sierralogic/tophat/compare/v0.1.4...HEAD
[0.1.4]: https://github.com/sierralogic/tophat/compare/v0.1.3...v0.1.4
[0.1.3]: https://github.com/sierralogic/tophat/compare/v0.1.2...v0.1.3
