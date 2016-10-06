# go-artifactory-scm-plugin

SCM-Plugin for [go](https://www.go.cd/). It treats a directory in [Artifactory](https://www.jfrog.com/artifactory/) as a SCM repository. That allows to choose which version of an artifact shall be deployed by go.

Repositories in Artifactory should follow this pattern:

	/some/dir/to/app/${VERSION}/${FILES}

When configuring a material inside go you should provide the URL up to `app/`.

`${VERSION}` should be sub-directories which are treated as SCM revisions.

On checkout all files in the `${VERSION}` sub-dir are downloaded.
