package com.maxdemarzi;

import com.maxdemarzi.results.LongResult;
import com.maxdemarzi.schema.Labels;
import org.neo4j.graphdb.*;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.roaringbitmap.longlong.Roaring64NavigableMap;

import java.util.Iterator;
import java.util.stream.Stream;

import static com.maxdemarzi.schema.Properties.ID;

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
                nextB.add(r.getOtherNodeId(nodeId));
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
                        nextA.add(r.getOtherNodeId(nodeId));
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
                            nextB.add(r.getOtherNodeId(nodeId));
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

    @Procedure(name = "com.maxdemarzi.wcc", mode = Mode.READ)
    @Description("com.maxdemarzi.wcc(String id)")
    public Stream<LongResult> wcc(@Name("startingNode") Node startingNode) {

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
                nextB.add(r.getOtherNodeId(nodeId));
            }

            while (true) {
                // next even Hop
                if (nextHop(seen, nextA, nextB)) break;
                // next odd Hop
                if (nextHop(seen, nextB, nextA)) break;
            }

            seen.or(nextA);
            seen.or(nextB);

            // remove starting node
            seen.removeLong(startingNode.getId());

            return Stream.of(new LongResult(seen.getLongCardinality()));
        }
    }

    private boolean nextHop(Roaring64NavigableMap seen, Roaring64NavigableMap nextA, Roaring64NavigableMap nextB) {
        Iterator<Long> iterator;
        long nodeId;
        Node node;
        nextB.andNot(seen);
        seen.or(nextB);
        nextA.clear();
        iterator = nextB.iterator();
        while (iterator.hasNext()) {
            nodeId = iterator.next();
            node = db.getNodeById(nodeId);
            for (Relationship r : node.getRelationships()) {
                nextA.add(r.getOtherNodeId(nodeId));
            }
        }
        return nextA.isEmpty();
    }
}
