# Releasing Braid

This is the maintainer runbook for publishing Braid through the Maven Central
Publisher Portal. A manual GitHub Actions workflow verifies releases without
credentials and, after explicit confirmation, uploads a signed user-managed
deployment. Final publication remains a manual action in the Central Portal.

## Published artifacts

Both artifacts use the same `VERSION_NAME` value.

| Module | Maven coordinate | Contents |
| --- | --- | --- |
| `braid` | `io.github.k1een:braid` | Core ListAdapter, delegate, and ViewBinding APIs. |
| `braid-paging` | `io.github.k1een:braid-paging` | Paging 3 integration; exposes `braid` transitively. |

The `sample` application is never published.

Every Central publication must contain a release AAR, production sources JAR,
Dokka HTML javadoc JAR, POM, Gradle Module Metadata, and signatures for all
required files.

## Prerequisites

Before attempting a release, confirm all of the following:

1. The Central Publisher Portal account is active.
2. The `io.github.k1een` namespace is verified for that account.
3. A current Portal user token has been generated and stored in a password
   manager or CI secret store. Portal token values must not be logged.
4. A GPG signing key is available, its fingerprint has been verified, and its
   public key has been distributed to a public keyserver.
5. The release is built from a clean, current `main` branch with green CI.
6. The intended version and release notes have been reviewed.

The private GPG key must be exported in an in-memory format accepted by Gradle
Signing. Do not import it into the repository or store it as a tracked file.

## GitHub release environment

The GitHub Environment named `release` allows deployments only from `main` and
contains these Environment secrets:

| GitHub secret | Gradle environment variable |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | `ORG_GRADLE_PROJECT_mavenCentralUsername` |
| `MAVEN_CENTRAL_PASSWORD` | `ORG_GRADLE_PROJECT_mavenCentralPassword` |
| `SIGNING_IN_MEMORY_KEY` | `ORG_GRADLE_PROJECT_signingInMemoryKey` |
| `SIGNING_IN_MEMORY_KEY_ID` | `ORG_GRADLE_PROJECT_signingInMemoryKeyId` |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` |

Only the workflow's `upload` job uses the `release` Environment. The `verify`
job, including every dry run, has no access to these secrets.

The remote upload step also enables these non-secret Gradle properties:

```text
ORG_GRADLE_PROJECT_mavenCentralPublishing=true
ORG_GRADLE_PROJECT_mavenCentralAutomaticPublishing=false
ORG_GRADLE_PROJECT_signAllPublications=true
```

`mavenCentralAutomaticPublishing=false` keeps the deployment in the Portal for
manual inspection. Ordinary local builds and pull-request CI do not set these
properties, so they neither contact Central nor require signing credentials.

## Local publication verification

Start from a clean worktree and run the complete local gate without any
credentials:

```shell
./gradlew clean publishingCheck
./gradlew qualityCheck
```

`publishingCheck` publishes both artifacts only to
`build/test-maven-repository` and then validates:

- one release AAR, sources JAR, Dokka javadoc JAR, POM, and `.module` file per
  artifact;
- complete POM project, license, developer, SCM, and issue metadata;
- RecyclerView as a consumer dependency of `braid`;
- the same-version `braid` dependency and Paging runtime dependency of
  `braid-paging`;
- archive integrity and the absence of tests, sample code, generated bindings,
  debug artifacts, APKs, and local project notation.

The default local version is `0.1.0-SNAPSHOT`. The repository is deleted before
every check, is removed by `clean`, and is never uploaded or written to
`~/.m2`.

Before a release, also build a standalone temporary Android consumer against
the local repository. It should compile homogeneous and heterogeneous lists,
`statefulViewBinding`, and `braidPagingDataAdapter`, including inherited Paging
APIs. The consumer must use Maven coordinates only, not project dependencies,
local AAR paths, or an included build.

## Selecting a version

`VERSION_NAME` is the single version source for both publications. The checked-in
default remains a snapshot for local development. Pass a release version
explicitly:

```shell
./gradlew -PVERSION_NAME=0.1.0-alpha01 publishingCheck
```

Before upload, verify that the selected version:

- is not a `SNAPSHOT`;
- matches the approved release notes and tag plan;
- has never been released under either Braid coordinate;
- resolves to the same value in both generated POM files.

Do not derive release versions from timestamps, commit hashes, `versionCode`, or
the latest Git tag.

## GitHub Actions release workflow

The workflow file must be present in the default branch before GitHub exposes
the manual action. Prepare and upload a release as follows:

1. Confirm the workflow commit is in `main` and CI is green.
2. Open **Actions -> Release to Maven Central -> Run workflow**.
3. Start with `version = 0.1.0-alpha01`, `upload = false`, and an empty
   `confirmation` value.
4. Confirm the `verify` job is green and the `upload` job is skipped.
5. Run the workflow again with the exact same version, `upload = true`, and
   `confirmation = PUBLISH 0.1.0-alpha01`.
6. Approve the `upload` job if required by the `release` Environment rules.
7. Wait for the signed user-managed deployment upload to complete.
8. Open **Central Portal -> Deployments**.
9. Wait for Central validation to finish.
10. Inspect both modules, signatures, POM metadata, and dependency metadata.
11. Click **Publish** manually only after every validation and inspection passes.
12. Wait until both coordinates resolve from Maven Central.
13. Only then create the Git tag and GitHub Release as a separate task.

The workflow rejects runs outside `main`, invalid or snapshot versions, an
incorrect upload confirmation, an existing `v<version>` Git tag, and a version
already published under either coordinate. The `release` Environment is
restricted to `main`; its secrets are unavailable to dry runs. Automatic
publication is disabled, and Maven Central coordinates are immutable after
publication.

## Manual upload fallback

If GitHub Actions is unavailable, a maintainer may use the local path from a
clean, current `main` checkout after completing the same verification and
confirmation steps. Set the Portal token, in-memory signing key, and non-secret
activation properties only in the current release shell, then upload both
module publications with the same explicit version:

```shell
./gradlew -PVERSION_NAME=0.1.0-alpha01 \
    :braid:publishToMavenCentral \
    :braid-paging:publishToMavenCentral
```

With `mavenCentralAutomaticPublishing=false`, these tasks upload a deployment
but do not release it automatically. Do not run any Central task during normal
development or pull-request validation.

Always use both `publishToMavenCentral` tasks in one Gradle invocation so the
plugin can create one user-managed deployment. Do not use
`publishAndReleaseToMavenCentral`; automatic publication is intentionally
disabled.

## Portal validation and manual publication

In the Central Publisher Portal, inspect the deployment before publishing:

1. Confirm both coordinates and the exact version.
2. Confirm the release AAR, sources JAR, javadoc JAR, POM, and signatures for
   both artifacts.
3. Review Central validation results and correct every error.
4. Inspect the generated dependency metadata, especially the same-version
   Paging-to-core dependency.
5. Publish the deployment manually only after all checks pass.

After publication, wait until the artifacts resolve from Maven Central in a
clean consumer that does not use `mavenLocal()` or the test repository. Tags,
GitHub Releases, README installation coordinates, and badges belong to a
separate post-publication step; do not create them before Central succeeds.

## Failure and recovery

If validation fails before publication:

1. Do not create a tag or GitHub Release.
2. Keep the deployment unreleased or drop it through the Portal.
3. Fix the source or metadata on a new commit.
4. Rerun `publishingCheck`, `qualityCheck`, tests, and assemblies.
5. Upload again only after reviewing whether the version may still be reused.

Once Central publishes a coordinate and version, it is immutable. Never attempt
to overwrite, replace, or silently reuse it. If a published artifact is wrong,
prepare a new version and document the correction.

## Never commit

Never commit or print any of the following:

- Central username, password, or Portal token;
- private GPG key material or its password;
- authorization headers or encoded credentials;
- signing property values;
- generated local Maven repositories or temporary consumers;
- release-environment files containing secrets.

Keep release credentials in the Portal, a password manager, and the GitHub
Environment named `release` only.
