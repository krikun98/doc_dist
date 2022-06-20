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

- Run the `run.sh` script

Alternatively,

- `docker build -t doc_dist .`
- `docker run -p 8080:8080 doc_dist`

In both cases the sample documentation project will be pulled in automatically
from the default `-o` option.

## TODO

- Tests
- Security credentials management system (for JGit to access private repos)

## Known issues

- JGit is overly enthusiastic with its logging
