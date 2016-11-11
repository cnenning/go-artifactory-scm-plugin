# go-artifactory-scm-plugin

SCM-Plugin for [go](https://www.go.cd/). It treats a directory in [Artifactory](https://www.jfrog.com/artifactory/) as a SCM repository. That allows to choose which version of an artifact shall be deployed by go.

Repositories in Artifactory should follow this pattern:

	/some/dir/to/app/${VERSION}/${FILES}

When configuring a material inside go you should provide the URL up to `app/`.

`${VERSION}` should be sub-directories which are treated as SCM revisions.

On checkout all files in the `${VERSION}` sub-dir are downloaded.


## Installation
Place the [JAR file](/cnenning/go-artifactory-scm-plugin/releases) in directory `plugins/external` of your go-server. See [go documentation](https://docs.go.cd/current/extension_points/plugin_user_guide.html) for more detail.


## Usage
![screenshot of material config](https://cloud.githubusercontent.com/assets/15086255/20215868/9ac746dc-a817-11e6-986b-5964d8a2b8dd.png)

After a pipeline has been created you can add an `Artifactory SCM` material.
