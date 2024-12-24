# How to publish a new version
1. Checkout tag release-1.18.0
2. Create a new branch: cellutions/release-1.18
3. Commit as usual
4. When releasing a new version, create a new tag: e.g. release-1.18.0-cellutions-1, only increase the last number
5. When upgrading to a new patch version, checkout equivalent tag e.g. release-1.18.1, merge to branch cellutions/release-1.18, and make sure to put the correct patch version in new tags as well

# How to deploy a new version

```bash
mvn clean package -DskipTests -Dspotless.check.skip=true -pl :flink-s3-fs-presto -am 

mvn deploy:deploy-file \                                                                                                                                                                                      ─╯
  -Dfile=flink-filesystems/flink-s3-fs-presto/target/flink-s3-fs-presto-1.18.0.jar \
  -Durl=path_to_your_repository \
  -DgroupId=your_group_id \
  -DartifactId=your_artifact_id \
  -Dversion=your_version \
  -Dpackaging=jar \
  -DrepositoryId=your_repository_id

```
