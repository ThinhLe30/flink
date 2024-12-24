#!/bin/bash

mvn clean package -DskipTests -Dspotless.check.skip=true -pl :flink-s3-fs-presto -am

mvn deploy:deploy-file \                                                                                                                                                                                      ─╯
  -Dfile=flink-filesystems/flink-s3-fs-presto/target/flink-s3-fs-presto-1.18.0.jar \
  -Durl=https://artifacts.vtvlive.vn/repository/Giaitri_Maven_mixed/ \
  -DgroupId=org.apache.flink \
  -DartifactId=flink-s3-fs-base \
  -Dversion=1.18.0-cellutions-1 \
  -Dpackaging=jar \
  -DrepositoryId=Giaitri_Maven_group

