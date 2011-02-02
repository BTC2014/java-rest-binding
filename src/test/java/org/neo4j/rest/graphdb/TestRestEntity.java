package org.neo4j.rest.graphdb;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.graphdb.*;

import java.net.URI;
import java.util.Arrays;
import java.util.Date;

public class TestRestEntity {
    private static GraphDatabaseService graphDb;

    private static final String SERVER_ROOT_URI = "http://localhost:7474/db/data/";

    @BeforeClass
    public static void startDb() throws Exception {
        graphDb = new RestGraphDatabase( new URI( SERVER_ROOT_URI ) );
    }

    @AfterClass
    public static void shutdownDb() {
        graphDb.shutdown();
    }

    @Test
    public void testSetProperty() {
        graphDb.getReferenceNode().setProperty( "name", "test" );
        Node node = graphDb.getReferenceNode();
        Assert.assertEquals( "test", node.getProperty( "name" ) );
    }

    @Test
    public void testSetStringArrayProperty() {
        graphDb.getReferenceNode().setProperty( "name", new String[]{"test"} );
        Node node = graphDb.getReferenceNode();
        Assert.assertArrayEquals( new String[]{"test"}, (String[])node.getProperty( "name" ) );
    }
    @Test
    public void testSetDoubleArrayProperty() {
        double[] data = {0, 1, 2};
        graphDb.getReferenceNode().setProperty( "data", data );
        Node node = graphDb.getReferenceNode();
        Assert.assertTrue("same double array",Arrays.equals( data, (double[])node.getProperty( "data" ) ));
    }

    @Test
    public void testRemoveProperty() {
        Node node = graphDb.getReferenceNode();
        node.setProperty( "name", "test" );
        Assert.assertEquals( "test", node.getProperty( "name" ) );
        node.removeProperty( "name" );
        Assert.assertEquals( false, node.hasProperty( "name" ) );
    }


    @Test
    public void testSetPropertyOnRelationship() {
        Node refNode = graphDb.getReferenceNode();
        Node node = graphDb.createNode();
        Relationship rel = refNode.createRelationshipTo( node, Type.TEST );
        rel.setProperty( "name", "test" );
        Assert.assertEquals( "test", rel.getProperty( "name" ) );
        Relationship foundRelationship = IsRelationshipToNodeMatcher.relationshipFromTo( refNode.getRelationships( Type.TEST, Direction.OUTGOING ), refNode, node );
        Assert.assertEquals( "test", foundRelationship.getProperty( "name" ) );
    }

    @Test
    public void testRemovePropertyOnRelationship() {
        Node refNode = graphDb.getReferenceNode();
        Node node = graphDb.createNode();
        Relationship rel = refNode.createRelationshipTo( node, Type.TEST );
        rel.setProperty( "name", "test" );
        Assert.assertEquals( "test", rel.getProperty( "name" ) );
        Relationship foundRelationship = IsRelationshipToNodeMatcher.relationshipFromTo( refNode.getRelationships( Type.TEST, Direction.OUTGOING ), refNode, node );
        Assert.assertEquals( "test", foundRelationship.getProperty( "name" ) );
        rel.removeProperty( "name" );
        Assert.assertEquals( false, rel.hasProperty( "name" ) );
        Relationship foundRelationship2 = IsRelationshipToNodeMatcher.relationshipFromTo( refNode.getRelationships( Type.TEST, Direction.OUTGOING ), refNode, node );
        Assert.assertEquals( false, foundRelationship2.hasProperty( "name" ) );
    }

}
