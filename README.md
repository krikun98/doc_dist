# Documentation distribution service

Currently WIP

## Structure

The documentation is stored as a `git` repository.
Products and subproducts are stored as submodules.
The main repository also stores metadata:
the current version and starting page for each product or subproduct.

Versioning is done with branches, as
tags wouldnt' work well with frequent updates.
Documentation writers only need to touch the main repository
if there's a change to the metadata (i.e. a new release
changes the up-to-date version), otherwise they just work
within their product repository. 

A planned update coroutine is going to refresh any changes to the
main documentation repository and all submodules at a fixed interval.

## How to use:

- Unzip `docs.zip` into the `docs` folder
- Run the `run.sh` script

A Docker image is planned for distribution.

## TODO

- CLI options: port, documentation repository link, update period, etc.
- Documentation repository update coroutine

## Known issues

- Navigation between static files doesn't work

