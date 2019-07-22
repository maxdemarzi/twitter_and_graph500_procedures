package com.maxdemarzi.results;

import org.neo4j.helpers.collection.Pair;

public class ScoreResult {
    public final Long id;
    public final Long score;

    public ScoreResult(Pair<Long,Long> pair) {
        this.id = pair.first();
        this.score = pair.other();
    }
}
