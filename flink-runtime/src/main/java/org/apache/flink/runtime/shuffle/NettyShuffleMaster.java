/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.shuffle;

import org.apache.flink.api.common.JobID;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.configuration.NettyShuffleEnvironmentOptions;
import org.apache.flink.runtime.io.network.partition.ResultPartitionID;
import org.apache.flink.runtime.io.network.partition.hybrid.tiered.shuffle.TieredInternalShuffleMaster;
import org.apache.flink.runtime.io.network.partition.hybrid.tiered.tier.TierShuffleDescriptor;
import org.apache.flink.runtime.shuffle.NettyShuffleDescriptor.LocalExecutionPartitionConnectionInfo;
import org.apache.flink.runtime.shuffle.NettyShuffleDescriptor.NetworkPartitionConnectionInfo;
import org.apache.flink.runtime.shuffle.NettyShuffleDescriptor.PartitionConnectionInfo;
import org.apache.flink.runtime.util.ConfigurationParserUtils;

import javax.annotation.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.apache.flink.api.common.BatchShuffleMode.ALL_EXCHANGES_HYBRID_FULL;
import static org.apache.flink.api.common.BatchShuffleMode.ALL_EXCHANGES_HYBRID_SELECTIVE;
import static org.apache.flink.configuration.ExecutionOptions.BATCH_SHUFFLE_MODE;
import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/** Default {@link ShuffleMaster} for netty and local file based shuffle implementation. */
public class NettyShuffleMaster implements ShuffleMaster<NettyShuffleDescriptor> {

    private final int buffersPerInputChannel;

    private final int floatingBuffersPerGate;

    private final Optional<Integer> maxRequiredBuffersPerGate;

    private final int sortShuffleMinParallelism;

    private final int sortShuffleMinBuffers;

    private final int networkBufferSize;

    @Nullable private final TieredInternalShuffleMaster tieredInternalShuffleMaster;

    private final Map<JobID, JobShuffleContext> jobShuffleContexts = new HashMap<>();

    public NettyShuffleMaster(ShuffleMasterContext shuffleMasterContext) {
        Configuration conf = shuffleMasterContext.getConfiguration();
        checkNotNull(conf);
        buffersPerInputChannel = 2;
        floatingBuffersPerGate = 8;
        maxRequiredBuffersPerGate =
                conf.getOptional(
                        NettyShuffleEnvironmentOptions.NETWORK_READ_MAX_REQUIRED_BUFFERS_PER_GATE);
        sortShuffleMinParallelism = 1;
        sortShuffleMinBuffers =
                conf.get(NettyShuffleEnvironmentOptions.NETWORK_SORT_SHUFFLE_MIN_BUFFERS);
        networkBufferSize = ConfigurationParserUtils.getPageSize(conf);

        if (isHybridShuffleEnabled(conf)) {
            tieredInternalShuffleMaster = new TieredInternalShuffleMaster(shuffleMasterContext);
        } else {
            tieredInternalShuffleMaster = null;
        }

        checkArgument(
                !maxRequiredBuffersPerGate.isPresent() || maxRequiredBuffersPerGate.get() >= 1,
                String.format(
                        "At least one buffer is required for each gate, please increase the value of %s.",
                        NettyShuffleEnvironmentOptions.NETWORK_READ_MAX_REQUIRED_BUFFERS_PER_GATE
                                .key()));
    }

    @Override
    public CompletableFuture<NettyShuffleDescriptor> registerPartitionWithProducer(
            JobID jobID,
            PartitionDescriptor partitionDescriptor,
            ProducerDescriptor producerDescriptor) {

        ResultPartitionID resultPartitionID =
                new ResultPartitionID(
                        partitionDescriptor.getPartitionId(),
                        producerDescriptor.getProducerExecutionId());

        List<TierShuffleDescriptor> tierShuffleDescriptors = null;
        if (tieredInternalShuffleMaster != null) {
            tierShuffleDescriptors =
                    tieredInternalShuffleMaster.addPartitionAndGetShuffleDescriptor(
                            jobID,
                            partitionDescriptor.getNumberOfSubpartitions(),
                            resultPartitionID);
        }

        NettyShuffleDescriptor shuffleDeploymentDescriptor =
                new NettyShuffleDescriptor(
                        producerDescriptor.getProducerLocation(),
                        createConnectionInfo(
                                producerDescriptor, partitionDescriptor.getConnectionIndex()),
                        resultPartitionID,
                        tierShuffleDescriptors);
        return CompletableFuture.completedFuture(shuffleDeploymentDescriptor);
    }

    @Override
    public void releasePartitionExternally(ShuffleDescriptor shuffleDescriptor) {
        if (tieredInternalShuffleMaster != null) {
            tieredInternalShuffleMaster.releasePartition(shuffleDescriptor);
        }
    }

    private static PartitionConnectionInfo createConnectionInfo(
            ProducerDescriptor producerDescriptor, int connectionIndex) {
        return producerDescriptor.getDataPort() >= 0
                ? NetworkPartitionConnectionInfo.fromProducerDescriptor(
                        producerDescriptor, connectionIndex)
                : LocalExecutionPartitionConnectionInfo.INSTANCE;
    }

    /**
     * JM announces network memory requirement from the calculating result of this method. Please
     * note that the calculating algorithm depends on both I/O details of a vertex and network
     * configuration, which means we should always keep the consistency of configurations between
     * JM, RM and TM in fine-grained resource management, thus to guarantee that the processes of
     * memory announcing and allocating respect each other.
     */
    @Override
    public MemorySize computeShuffleMemorySizeForTask(TaskInputsOutputsDescriptor desc) {
        checkNotNull(desc);

        int numRequiredNetworkBuffers =
                NettyShuffleUtils.computeNetworkBuffersForAnnouncing(
                        buffersPerInputChannel,
                        floatingBuffersPerGate,
                        maxRequiredBuffersPerGate,
                        sortShuffleMinParallelism,
                        sortShuffleMinBuffers,
                        desc.getInputChannelNums(),
                        desc.getPartitionReuseCount(),
                        desc.getSubpartitionNums(),
                        desc.getInputPartitionTypes(),
                        desc.getPartitionTypes());

        return new MemorySize((long) networkBufferSize * numRequiredNetworkBuffers);
    }

    private boolean isHybridShuffleEnabled(Configuration conf) {
        return (conf.get(BATCH_SHUFFLE_MODE) == ALL_EXCHANGES_HYBRID_FULL
                || conf.get(BATCH_SHUFFLE_MODE) == ALL_EXCHANGES_HYBRID_SELECTIVE);
    }

    @Override
    public CompletableFuture<Collection<PartitionWithMetrics>> getPartitionWithMetrics(
            JobID jobId, Duration timeout, Set<ResultPartitionID> expectedPartitions) {
        return checkNotNull(jobShuffleContexts.get(jobId))
                .getPartitionWithMetrics(timeout, expectedPartitions);
    }

    @Override
    public void registerJob(JobShuffleContext context) {
        jobShuffleContexts.put(context.getJobId(), context);
        if (tieredInternalShuffleMaster != null) {
            tieredInternalShuffleMaster.registerJob(context);
        }
    }

    @Override
    public void unregisterJob(JobID jobId) {
        jobShuffleContexts.remove(jobId);
        if (tieredInternalShuffleMaster != null) {
            tieredInternalShuffleMaster.unregisterJob(jobId);
        }
    }

    @Override
    public boolean supportsBatchSnapshot() {
        return true;
    }

    @Override
    public void snapshotState(
            CompletableFuture<ShuffleMasterSnapshot> snapshotFuture,
            ShuffleMasterSnapshotContext context,
            JobID jobId) {
        snapshotFuture.complete(EmptyShuffleMasterSnapshot.getInstance());
    }

    @Override
    public void snapshotState(CompletableFuture<ShuffleMasterSnapshot> snapshotFuture) {
        snapshotFuture.complete(EmptyShuffleMasterSnapshot.getInstance());
    }

    @Override
    public void notifyPartitionRecoveryStarted(JobID jobId) {
        checkNotNull(jobShuffleContexts.get(jobId)).notifyPartitionRecoveryStarted();
    }

    @Override
    public void close() throws Exception {
        if (tieredInternalShuffleMaster != null) {
            tieredInternalShuffleMaster.close();
        }
    }
}
