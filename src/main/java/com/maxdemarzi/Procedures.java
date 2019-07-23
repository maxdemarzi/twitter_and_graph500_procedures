package com.maxdemarzi;

import com.maxdemarzi.results.LongResult;
import org.neo4j.graphdb.*;
import org.neo4j.internal.kernel.api.*;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.impl.core.ThreadToStatementContextBridge;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.roaringbitmap.longlong.Roaring64NavigableMap;

import java.util.Iterator;
import java.util.stream.Stream;

public class Procedures {

    // This field declares that we need a GraphDatabaseService
    // as context when any procedure in this class is invoked
    @Context
    public GraphDatabaseService db;

    // This gives us a log instance that outputs messages to the
    // standard log, normally found under `data/log/neo4j.log`
    @Context
    public Log log;

    /*
    match (n1:MyNode)-[:MyEdge*.." + str(depth) + "]->(n2:MyNode) where n1.id={root} return count(distinct n2)
     */


    @Procedure(name = "com.maxdemarzi.knn", mode = Mode.READ)
    @Description("com.maxdemarzi.knn(Node node, Long distance)")
    public Stream<LongResult> knn(@Name("startingNode") Node startingNode, @Name(value = "distance", defaultValue = "1") Long distance) {
        if (distance < 1) return Stream.empty();

        if (startingNode == null) {
            return Stream.empty();
        } else {

            Node node;
            // Initialize bitmaps for iteration
            Roaring64NavigableMap seen = new Roaring64NavigableMap();
            Roaring64NavigableMap nextA = new Roaring64NavigableMap();
            Roaring64NavigableMap nextB = new Roaring64NavigableMap();

            long nodeId = startingNode.getId();
            seen.add(nodeId);
            Iterator<Long> iterator;

            // First Hop
            for (Relationship r : startingNode.getRelationships()) {
                nextB.add(r.getEndNodeId());
            }

            for (int i = 1; i < distance; i++) {
                // next even Hop
                nextB.andNot(seen);
                seen.or(nextB);
                nextA.clear();
                iterator = nextB.iterator();
                while (iterator.hasNext()) {
                    nodeId = iterator.next();
                    node = db.getNodeById(nodeId);
                    for (Relationship r : node.getRelationships()) {
                        nextA.add(r.getEndNodeId());
                    }
                }

                i++;
                if (i < distance) {
                    // next odd Hop
                    nextA.andNot(seen);
                    seen.or(nextA);
                    nextB.clear();
                    iterator = nextA.iterator();
                    while (iterator.hasNext()) {
                        nodeId = iterator.next();
                        node = db.getNodeById(nodeId);
                        for (Relationship r : node.getRelationships()) {
                            nextB.add(r.getEndNodeId());
                        }
                    }
                }
            }

            if ((distance % 2) == 0) {
                seen.or(nextA);
            } else {
                seen.or(nextB);
            }
            // remove starting node
            seen.removeLong(startingNode.getId());

            return Stream.of(new LongResult(seen.getLongCardinality()));
        }
    }


    @Procedure(name = "com.maxdemarzi.knn2", mode = Mode.READ)
    @Description("com.maxdemarzi.knn2(Node node, Long distance)")
    public Stream<LongResult> knn2(@Name("startingNode") Node startingNode, @Name(value = "distance", defaultValue = "1") Long distance) {
        if (distance < 1) return Stream.empty();

        if (startingNode == null) {
            return Stream.empty();
        } else {
            DependencyResolver dependencyResolver = ((GraphDatabaseAPI)db).getDependencyResolver();
            final ThreadToStatementContextBridge ctx = dependencyResolver.resolveDependency(ThreadToStatementContextBridge.class, DependencyResolver.SelectionStrategy.FIRST);
            KernelTransaction ktx = ctx.getKernelTransactionBoundToThisThread(true);
            CursorFactory cursors = ktx.cursors();
            Read read = ktx.dataRead();

            Roaring64NavigableMap seen = new Roaring64NavigableMap();
            Roaring64NavigableMap nextA = new Roaring64NavigableMap();
            Roaring64NavigableMap nextB = new Roaring64NavigableMap();

            seen.add(startingNode.getId());

            RelationshipTraversalCursor rels = cursors.allocateRelationshipTraversalCursor();
            NodeCursor nodeCursor = cursors.allocateNodeCursor();

            read.singleNode(startingNode.getId(), nodeCursor);
            nodeCursor.next();

            // First Hop
            nodeCursor.allRelationships(rels);
            while (rels.next()) {
                nextB.add(rels.targetNodeReference());
            }

            for (int i = 1; i < distance; i++) {
                // Next even Hop
                nextHop(read, seen, nextA, nextB, rels, nodeCursor);

                i++;
                if (i < distance) {
                    // Next odd Hop
                    nextHop(read, seen, nextB, nextA, rels, nodeCursor);
                }
            }

            if ((distance % 2) == 0) {
                seen.or(nextA);
            } else {
                seen.or(nextB);
            }

            // remove starting node
            seen.removeLong(startingNode.getId());

            return Stream.of(new LongResult(seen.getLongCardinality()));
        }
    }

    private void nextHop(Read read, Roaring64NavigableMap seen, Roaring64NavigableMap next, Roaring64NavigableMap current, RelationshipTraversalCursor rels, NodeCursor nodeCursor) {
        Iterator<Long> iterator;
        current.andNot(seen);
        seen.or(current);
        next.clear();

        iterator = current.iterator();
        while (iterator.hasNext()) {
            read.singleNode(iterator.next(), nodeCursor);
            nodeCursor.next();
            nodeCursor.allRelationships(rels);
            while (rels.next()) {
                next.add(rels.targetNodeReference());
            }
        }
    }

}
