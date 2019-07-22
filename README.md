# Twitter and Graph500 Procedures
Stored Procedures for yet another Benchmark


Instructions
------------ 

This project uses maven, to build a jar-file with the procedure in this
project, simply package the project with maven:

    mvn clean package

This will produce a jar-file, `target/procedures-1.0-SNAPSHOT.jar`,
that can be copied to the `plugin` directory of your Neo4j instance.

    cp target/procedures-1.0-SNAPSHOT.jar neo4j-enterprise-3.5.8/plugins/.
    

Restart your Neo4j Server. Your new Stored Procedures are available:

    CALL com.maxdemarzi.knn(String id, Long distance);
    CALL com.maxdemarzi.knn("1234", 3);

    CALL com.maxdemarzi.wcc(String id);
    CALL com.maxdemarzi.wcc("1234");