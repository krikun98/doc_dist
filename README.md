# Documentation distribution service

Currently WIP

## Structure

The documentation is stored as a `git` repository.
Products and subproducts are stored as submodules.
The main repository also stores metadata:
the current version and starting page for each product or subproduct.
See `metadata.json` example in `docs.zip`.

Versioning is done with branches, as
tags wouldn't work well with frequent updates.
Documentation writers only need to touch the main repository
if there's a change to the metadata (i.e. a new release
changes the up-to-date version), otherwise they just work
within their product repository.

An update coroutine is launched at startup.
It pulls in any changes to the main documentation repository and all 
branches of all submodules at a fixed interval.

## CLI options:

- `-h`: Help
- `-p`: Port
- `-l`: Host
- `-u`: Update frequency (in minutes)
- `-o`: Documentation repository origin URI
- `-d`: Local documentation repository path

## How to use:

- Unzip `docs.zip` into the `docs` folder
- Run the `run.sh` script

A Docker image is planned for distribution.

## TODO

- Docker distribution image

## Known issues

- Update coroutine may cause data races, granular mutexes (per submodule) will be implemented

