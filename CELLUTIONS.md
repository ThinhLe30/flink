# Cellutions Contributing Guidelines

## Working with branches and tags

1. Checkout a versioned tag e.g. `release-1.18.0`
2. Append `cellutions/` to tag name to create a new branch e.g. `cellutions/release-1.18.0`
3. Make modifications and commit on this branch
4. When releasing a new version, create a new tag from this branch with syntax
   `release-<flink_version>-cellutions-<cellutions_version>` where `cellutions_version` starts from
   1 and increments by 1 for each new tag

## Building and deployment

```bash
# if there are code changes, perhaps don't skip tests
mvn clean package -DskipTests -Dspotless.check.skip=true -pl :flink-s3-fs-presto -am 

mvn deploy:deploy-file \                                                                                                                                                                                      ─╯
  -Dfile=flink-filesystems/flink-s3-fs-presto/target/flink-s3-fs-presto-1.18.0.jar \
  -Durl=<path_to_your_repository> \
  -DgroupId=<your_group_id> \
  -DartifactId=<your_artifact_id> \
  -Dversion=<flink_version>-cellutions-<cellutions_version> \
  -Dpackaging=jar \
  -DrepositoryId=<your_repository_id>
```
