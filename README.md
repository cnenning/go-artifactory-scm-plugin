# go-artifactory-scm-plugin

Started as [SCM-Plugin](https://docs.go.cd/current/extension_points/scm_extension.html) for [go](https://www.go.cd/). In meantime a [Package-Repository-Plugin](https://docs.go.cd/current/extension_points/package_repository_extension.html) is provided, too.

The SCM-Plugin treats a directory in [Artifactory](https://www.jfrog.com/artifactory/) as a SCM repository. That allows to choose which version of an artifact shall be deployed by go. With Package-Repository-Plugin you can just deploy latest version.


## Installation
Place the [JAR file(s)](https://github.com/cnenning/go-artifactory-scm-plugin/releases) in directory `plugins/external` of your go-server. See [go documentation](https://docs.go.cd/current/extension_points/plugin_user_guide.html) for more details.


## SCM Configuration
Repositories in Artifactory should follow this pattern:

	/some/dir/to/app/${VERSION}/${FILES}

When configuring a material inside go you should provide the URL up to `app/`.

`${VERSION}` should be sub-directories which are treated as SCM revisions.

On checkout all files in the `${VERSION}` sub-dir are downloaded. Optionally you can define a regex to download just some files.


## SCM Usage
![screenshot of material config](https://cloud.githubusercontent.com/assets/15086255/20215868/9ac746dc-a817-11e6-986b-5964d8a2b8dd.png)

After a pipeline has been created you can add an `Artifactory SCM` material.


## Package Configuration
go demands a Package-Repository definition in admin section. You can specify a common base URL for your artifacts there.

For pipeline materials you have to provide a path and filename regex to uniquely identify an artifact.

Package-Repository-Plugins cannot download files themselfs but provide variables to be used in custom scripts. This plugin provides filename and complete URL (location) of found artifact. Both 'as is' and with URL encoded filename.

You may use groups in regex to extract version number. E.g. a filename `foo_1.2.3.ext` may be matched with regex `(foo_)(.*)(\.ext)`. Each group is provided as variable.

