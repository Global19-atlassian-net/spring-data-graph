Spring Data - Graph
=======================

The primary goal of the [Spring Data](http://www.springsource.org/spring-data) project is to make it easier to build Spring-powered applications that use new data access technologies such as non-relational databases, map-reduce frameworks, and cloud based data services.
As the name implies, the **Graph ** project provides integration with graph value stores.  The only supported Graph Database now is [Neo4j](http://neo4j.org/).  

The Spring Data Graph project provides a simplified POJO based programming model that reduces that amount of boilerplate code needed to create neo4j applications.  it also provides a 'cross store' persistence solution that can extend existing JPA data models with new parts (properties, entities, relationships) that are stored exclusively in the graph while being transparently integrated with the JPA entities.  This enables for easy and seamless addition of new features that were not available before to JPA-based applications.

Getting Help
------------

This README and the [User Guide](http://static.springsource.org/spring-data/datastore-graph/snapshot-site/reference/html/) are the best places to start learning about Spring Data Graph.  There are also [three sample appilcations](https://github.com/SpringSource/spring-data-graph-examples) briefly described in the reference documentation.

The main project [website](http://www.springsource.org/spring-data) contains links to basic project information such as source code, JavaDocs, Issue tracking, etc.

For more detailed questions, use the [forum](http://forum.springsource.org/forumdisplay.php?f=80). If you are new to Spring as well as to Spring Data, look for information about [Spring projects](http://www.springsource.org/projects). 


# Quick Start

## Neo4j

Maven configuration:

Download the jar though Maven:


       <dependency>
         <groupId>org.springframework.data</groupId>
         <artifactId>spring-data-neo4j</artifactId>
         <version>1.0.0.M1</version>
       </dependency> 
       
       <repository>
         <id>spring-maven-snapshot</id>
         <snapshots><enabled>true</enabled></snapshots>
         <name>Springframework Maven MILESTONE Repository</name>
         <url>http://maven.springframework.org/milestone</url>
       </repository> 

Configure the Aspect-J maven plugin build & library dependency.  Add the following plugin XML to your project's <plugins> config in pom.xml to hook AspectJ into the build process:

      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>aspectj-maven-plugin</artifactId>
        <version>1.0</version>
        <configuration>
          <outxml>true</outxml>
          <aspectLibraries>
            <aspectLibrary>
              <groupId>org.springframework</groupId>
              <artifactId>spring-aspects</artifactId>
            </aspectLibrary>
            <aspectLibrary>
              <groupId>org.springframework.data</groupId>
              <artifactId>spring-data-neo4j</artifactId>
            </aspectLibrary>
          </aspectLibraries>
          <source>1.6</source>
          <target>1.6</target>
        </configuration>
        <executions>
          <execution>
            <goals>
              <goal>compile</goal>
              <goal>test-compile</goal>
            </goals>
          </execution>
        </executions>
        <dependencies>
          <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjrt</artifactId>
            <version>1.6.10.RELEASE</version>
          </dependency>
          <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjtools</artifactId>
            <version>1.6.10.RELEASE</version>
          </dependency>
        </dependencies>
      </plugin>


Spring Configuration:

* Configure Spring Data Graph for Neo4j in your application using Spring 3.0's [Java based bean metadata](http://static.springsource.org/spring/docs/3.0.x/spring-framework-reference/html/new-in-3.html#new-java-configuration)  For XML based configuration refer to the User Guide.

        @Configuration
        public class MyConfig extends AbstractNeo4jConfiguration {

          @Override
          public boolean isUsingCrossStorePersistence() {
              return false;
          }

          @Override
          public GraphDatabaseService graphDatabaseService() {
            return new EmbeddedGraphDatabase("target/neo4j-db");
          }
        }

* Annotate your entity class.  In this case it is a 'World' class that has a relationship to other worlds that are reachable by rocket travel:

        @NodeEntity
        public class World {
      
          @GraphProperty
          private String name;            
        
          @GraphProperty
          private int moons;
        
          @RelatedTo(type = "REACHABLE_BY_ROCKET", 
                       elementClass = World.class, 
                       direction = Direction.BOTH)
          private Set<World> reachableByRocket;

          public World( String name, int moons )
          {
              this.name = name;
              this.moons = moons;
          }

          public String getName()  { return name; }

          public int getMoons() { return moons; }

          public void addRocketRouteTo( World otherWorld )
          {
              ((NodeBacked) this).relateTo( otherWorld, RelationshipTypes.REACHABLE_BY_ROCKET );
          }
        
          public boolean canBeReachedFrom( World otherWorld )
          {
              return this.reachableByRocket.contains( otherWorld );
          }
        }

* Create a repository to perform typical CRUD like data access.  The FinderFactory and Finder helper classes make searching easy for common use cases.  You can also use the introduce method find(Class class, TraversalDescription traversal) within the World class to perform traversals that start from a given 'World' instance location in the graph.

        @Repository
        public class WorldRepository {

          @Autowired
          private FinderFactory finderFactory;
        
          @Transactional
          public Collection<World> makeSomeWorlds()  {
            ArrayList<World> newWorlds = new ArrayList<World>();
            newWorlds.add( new World( "Mercury", 0 ) );
            newWorlds.add( new World( "Venus", 0 ) );
            World earth = new World( "Earth", 1 );
            newWorlds.add( earth );
            World mars = new World( "Mars", 2 );
            mars.addRocketRouteTo( earth );
            newWorlds.add( mars );
            newWorlds.add( new World( "Jupiter", 63 ) );
            newWorlds.add( new World( "Saturn", 62 ) );
            newWorlds.add( new World( "Uranus", 27 ) );
            newWorlds.add( new World( "Neptune", 13 ) );
            return newWorlds;
          }
        
          public World findWorldIdentifiedBy( long id )  {
             return (World) finderFactory.getFinderForClass( World.class ).findById( id );
          }
            
          public Iterable<World> findAllWorlds()  {
             return (Iterable<World>) finderFactory.getFinderForClass( World.class ).findAll();
          }
            
          public long countWorlds()  {
            return finderFactory.getFinderForClass( World.class ).count();
          }
            
          public World findWorldNamed( String name ) {
            return (World) finderFactory.getFinderForClass( World.class ).findByPropertyValue( "name", name );
          }
            
          public World findWorldWithMoons( long moonCount ) {
            return finderFactory.getFinderForClass( World.class ).findById( moonCount );
          }
          
          public Iterable<World> findWorldsWithMoons( int moonCount )  {
            return (Iterable<World>) finderFactory.getFinderForClass( World.class ).findAllByPropertyValue( "moons", moonCount );
          }
                       
        }

Please see the [Hello Worlds sample project](https://github.com/SpringSource/spring-data-graph-examples/tree/master/hello-worlds) in the examples repository for more information.


Contributing to Spring Data
---------------------------

Here are some ways for you to get involved in the community:

* Get involved with the Spring community on the Spring Community Forums.  Please help out on the [forum](http://forum.springsource.org/forumdisplay.php?f=80) by responding to questions and joining the debate.
* Create [JIRA](https://jira.springframework.org/browse/DATAGRAPH) tickets for bugs and new features and comment and vote on the ones that you are interested in.  
* Github is for social coding: if you want to write code, we encourage contributions through pull requests from [forks of this repository](http://help.github.com/forking/). If you want to contribute code this way, please reference a JIRA ticket as well covering the specific issue you are addressing.
* Watch for upcoming articles on Spring by [subscribing](http://www.springsource.org/node/feed) to springframework.org

Before we accept a non-trivial patch or pull request we will need you to sign the [contributor's agreement](https://support.springsource.com/spring_committer_signup).  Signing the contributor's agreement does not grant anyone commit rights to the main repository, but it does mean that we can accept your contributions, and you will get an author credit if we do.  Active contributors might be asked to join the core team, and given the ability to merge pull requests.




