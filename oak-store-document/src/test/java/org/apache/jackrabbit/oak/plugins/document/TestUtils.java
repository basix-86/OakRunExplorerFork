/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.document;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.jackrabbit.guava.common.base.Functions;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.plugins.document.VersionGarbageCollector.VersionGCStats;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.EmptyHook;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.oak.spi.state.NodeStore;
import org.apache.jackrabbit.oak.stats.Clock;
import org.jetbrains.annotations.NotNull;

import static org.apache.jackrabbit.guava.common.base.Functions.compose;
import static org.apache.jackrabbit.guava.common.base.Functions.constant;
import static org.junit.Assert.fail;

public class TestUtils {

    public static final Predicate<UpdateOp> IS_LAST_REV_UPDATE = input ->input != null && isLastRevUpdate(input);

    public static final Function<String, Long> NO_BINARY = compose(constant(-1L), Functions.<String>identity());

    /**
     * Returns {@code true} if the given {@code update} performs a
     * {@code _lastRev} update.
     *
     * @param update the update to check.
     * @return {@code true} if the operation performs an update on
     *          {@code _lastRev}, {@code false} otherwise.
     */
    public static boolean isLastRevUpdate(UpdateOp update) {
        for (Map.Entry<UpdateOp.Key, UpdateOp.Operation> change : update.getChanges().entrySet()) {
            if (!NodeDocument.isLastRevEntry(change.getKey().getName())
                    && !NodeDocument.MODIFIED_IN_SECS.equals(change.getKey().getName())) {
                return false;
            }
        }
        return true;
    }

    public static NodeState merge(NodeStore store, NodeBuilder builder)
            throws CommitFailedException {
        return store.merge(builder, EmptyHook.INSTANCE, CommitInfo.EMPTY);
    }

    public static NodeBuilder createChild(NodeBuilder root, String ... paths){
        for (String path : paths){
            childBuilder(root, path);
        }
        return root;
    }

    public static NodeBuilder childBuilder(NodeBuilder root, String path){
        NodeBuilder nb = root;
        for (String nodeName : PathUtils.elements(path)){
            nb = nb.child(nodeName);
        }
        return nb;
    }

    @NotNull
    public static DocumentNodeState asDocumentState(NodeState state){
        if (state instanceof DocumentNodeState){
            return (DocumentNodeState) state;
        }
        fail("Not of type DocumentNodeState " + state.getClass());
        return null;
    }

    public static void setRevisionClock(Clock c) {
        Revision.setClock(c);
    }

    public static void resetRevisionClockToDefault() {
        Revision.resetClockToDefault();
    }

    public static void setClusterNodeInfoClock(Clock c) {
        ClusterNodeInfo.setClock(c);
    }

    public static void resetClusterNodeInfoClockToDefault() {
        ClusterNodeInfo.resetClockToDefault();
    }

    public static void persistToBranch(NodeBuilder builder) {
        if (builder instanceof DocumentRootBuilder) {
            ((DocumentRootBuilder) builder).persist();
            return;
        }
        fail("Not of type DocumentRootBuilder: " + builder.getClass().getName());
    }

    public static boolean isFinalCommitRootUpdate(UpdateOp update) {
        boolean finalUpdate = true;
        for (Map.Entry<UpdateOp.Key, UpdateOp.Operation> op : update.getChanges().entrySet()) {
            String name = op.getKey().getName();
            if (NodeDocument.isRevisionsEntry(name)
                    || NodeDocument.MODIFIED_IN_SECS.equals(name)) {
                continue;
            }
            finalUpdate = false;
            break;
        }
        return finalUpdate;
    }

    public static void disposeQuietly(DocumentNodeStore ns) {
        try {
            ns.dispose();
        } catch (Exception e) {
            // ignore
        }
    }

    public static int getDeletedDocGCCount(VersionGCStats stats) {
        return stats.deletedDocGCCount;
    }

    public static int getMaxRangeHeight(NodeDocument doc) {
        int height = 0;
        for (Range r : doc.getPreviousRanges().values()) {
            height = Math.max(r.getHeight(), height);
        }
        return height;
    }

    public static Iterator<NodeDocument> getAllPreviousDocs(NodeDocument doc) {
        return doc.getAllPreviousDocs();
    }
}
