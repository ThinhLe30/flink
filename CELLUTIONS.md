<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

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
