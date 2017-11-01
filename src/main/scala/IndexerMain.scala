package main.scala

import org.apache.spark.SparkContext
import org.apache.spark.sql.functions.array_contains
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.graphframes._
import spray.json.{JsArray, JsObject, JsString}
import spray.json._

object IndexerMain {
  val file = "/Users/hp/workbench/projects/gmu/tweets/2017100309.txt"
//  val file = "src/main/resources/yagoFactInfluence.tsv"
//  val file = "hdfs://master:9000/data/yagoFactInfluence.tsv"
  def getSparkSession: SparkSession = {
  val master = "spark://localhost:7077"
  val spark = SparkSession
    .builder()
    .appName("YAGO_Indexer")
    .config("spark.executor.memory", "3g")
    .config("spark.executor.instances", "2")
    .master("local")
    .getOrCreate()
  spark.sparkContext.setLogLevel("ERROR")
  spark
}
  def getLinkedNodes(nodeId : String): JsObject = {

    val spark: SparkSession = getSparkSession
    val edges = spark.sql("SELECT src, dst, attr FROM global_temp.edges e WHERE e.src='" + nodeId + "' OR e.dst= '" + nodeId + "'")
    edges.createOrReplaceTempView("e")
    val vertices = spark.sql("SELECT id, attr FROM global_temp.vertices v WHERE v.id IN (SELECT src FROM e) OR v.id IN (SELECT dst from e)")
//    GraphFrame(vertices, edges)
    JsObject(
      "edges" -> getJs(edges),
      "vertices" -> getJs(vertices)
    )
  }

  def getJs(df: DataFrame): JsValue = {
    val collectedData  = df.toJSON.coalesce(1).collect().mkString("\n")
    val json = "[" + ("}\n".r replaceAllIn (collectedData, "},\n")) + "]"
    json.parseJson
  }

  def getGraphFrame: JsObject = {
    val spark: SparkSession = getSparkSession
    val in = readRdfDf(spark, file)
    in.vertices.createOrReplaceGlobalTempView("vertices")
    in.edges.createOrReplaceGlobalTempView("edges")
    in.inDegrees.filter(_.size>1)
    val out = getTopGraphFrame(spark, in)
    JsObject(
      "edges" -> getJs(out.edges),
      "vertices" -> getJs(out.vertices)
    )
  }

  def getTopGraphFrame(spark: SparkSession, in: GraphFrame): GraphFrame = {

    in.edges.createOrReplaceTempView("e")
    in.vertices.createOrReplaceTempView("v")

    val topVertexIds = spark.sql("SELECT allview.ID as ID, count(*) as CNT FROM (SELECT src as ID FROM e UNION ALL SELECT dst as ID FROM e) as allview GROUP BY allview.ID")
    topVertexIds.filter(_.getLong(1) > 2).orderBy(topVertexIds.col("CNT").desc).limit(15).createOrReplaceTempView("f")

    val finalEdges = spark.sql("SELECT src, dst, attr FROM e WHERE e.src IN (SELECT ID FROM f) OR e.dst IN (SELECT ID FROM f)")
    finalEdges.createOrReplaceTempView("finalE")

    val finalVertices = spark.sql("SELECT id, attr FROM v WHERE v.id IN (SELECT src FROM finalE) OR v.id IN (SELECT dst from finalE)")

    GraphFrame(finalVertices, finalEdges)

  }

  def main(args: Array[String]) {
    val sc: SparkSession = getSparkSession
    import sc.implicits._
    //    val arrayContains = udf( (col1: Int, col2: Seq[Int]) => if(col2.contains(col1) ) 1 else 0 )

    val in = readRdfDf(sc, file)

    // *** Experimental Start ***
    import org.apache.spark.sql.functions.{collect_set, struct, array_contains}
    val out: DataFrame = in.find("(a)-[ab]->(b); (a)-[ac]->(c)")
    val filter = Array("tweet_text")
    out.show(false)
    val filtered = out.filter($"a.attr".like("%tweet%"))
    filtered.show(false)
    // *** Experimental End ***


    in.edges.createOrReplaceTempView("e")
    in.vertices.createOrReplaceTempView("v")

    val topVertexIds = sc.sql("SELECT allview.ID as ID, count(*) as CNT FROM (SELECT src as ID FROM e UNION ALL SELECT dst as ID FROM e) as allview GROUP BY allview.ID")
    topVertexIds.filter(_.getLong(1) > 2).orderBy(topVertexIds.col("CNT").desc).limit(15).createOrReplaceTempView("f")

    val finalEdges = sc.sql("SELECT src, dst, attr FROM e WHERE e.src IN (SELECT ID FROM f) OR e.dst IN (SELECT ID FROM f)")
    finalEdges.createOrReplaceTempView("finalE")

    val finalVertices = sc.sql("SELECT id, attr FROM v WHERE v.id IN (SELECT src FROM finalE) OR v.id IN (SELECT dst from finalE)")

    GraphFrame(finalVertices, finalEdges)
  }

  def readRdfDf(spark: SparkSession, filename: String): GraphFrame = {
    val sc: SparkContext = spark.sparkContext
    import spark.implicits._
    import org.apache.spark.sql.functions.{collect_set, struct}

    val id_rows = sc.textFile(filename).filter(_.matches("^.*(_hashtags_text|entities_user_mentions_id|place_id|tweet_user_id).*$")).map(_.split('|')).filter(_.length>2)
    val attr_rows = sc.textFile(filename).filter(_.matches("^.*(tweet_text|tweet_user_screen_name|mentions_screen_name|user_screen_name|place_name).*$")).map(_.split('|')).filter(_.length>2)

    val ids = id_rows.map(_(0)).union(id_rows.map(_(2))).distinct()

    val ids_df = ids.map{a => (a, "id")}.toDF("id", "attr")

    val attr_df = attr_rows.map{case Array(a,b,c) =>
      (a,b.split("_").takeRight(2).mkString("_")+ ":" + c)}.toDF("id","attr")

    val vdf = ids_df.union(attr_df).groupBy($"id")
      .agg(collect_set("attr").as("attr"))

    val edf = id_rows.map{case Array(a, b, c) =>
      (a,c, b.split("_").takeRight(2).mkString("_"))}.toDF("src","dst","attr")

    GraphFrame(vdf, edf)

  }

  def initContextAndCreateGF: GraphFrame = {
    val spark: SparkSession = getSparkSession
    val in = readRdfDf(spark, file)
    in.persist()
  }

}
