package ai.chronon.spark.test
import ai.chronon.api.Constants.ChrononMetadataKey
import ai.chronon.api.{Builders, Constants, IntType, StructField, StructType}
import ai.chronon.online.Fetcher.Request
import org.junit.Assert._
import org.junit.Test

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, SECONDS}

class ExternalSourcesTest {
  @Test
  def testFetch(): Unit = {
    val plusOneSource = Builders.ExternalSource(
      metadata = Builders.MetaData(
        name = "plus_one"
      ),
      keySchema = StructType("keys_plus_one", Array(StructField("number", IntType))),
      valueSchema = StructType("values_plus_one", Array(StructField("number", IntType)))
    )

    val alwaysFailsSource = Builders.ExternalSource(
      metadata = Builders.MetaData(
        name = "always_fails"
      ),
      keySchema = StructType("keys_always_fails", Array(StructField("str", IntType))),
      valueSchema = StructType("values_always_fails", Array(StructField("str", IntType)))
    )

    val javaPlusOneSource = Builders.ExternalSource(
      metadata = Builders.MetaData(
        name = "java_plus_one"
      ),
      keySchema =
        StructType("keys_java_plus_one", Array(StructField("number", IntType), StructField("number_mapped", IntType))),
      valueSchema =
        StructType("values_java_plus_one", Array(StructField("number", IntType), StructField("number_mapped", IntType)))
    )

    val contextualSource = Builders.ExternalSource(
      metadata = Builders.MetaData(
        name = Constants.ContextualSourceName
      ),
      keySchema =
        StructType("keys_contextual", Array(StructField("context_1", IntType), StructField("context_2", IntType))),
      valueSchema =
        StructType("keys_contextual", Array(StructField("context_1", IntType), StructField("context_2", IntType)))
    )

    val namespace = "external_source_test"
    val join = Builders.Join(
      // left defined here shouldn't really matter for this test
      left = Builders.Source.events(Builders.Query(selects = Map("number" -> "number", "str" -> "test_str")), table = "non_existent_table"),
      externalParts = Seq(
        Builders.ExternalPart(
          plusOneSource,
          prefix = "p1"
        ),
        Builders.ExternalPart(
          plusOneSource,
          prefix = "p2"
        ),
        Builders.ExternalPart(
          alwaysFailsSource
        ),
        Builders.ExternalPart(
          javaPlusOneSource,
          keyMapping = Map("number" -> "number_mapped"),
          prefix = "p3"
        ),
        Builders.ExternalPart(
          contextualSource
        )
      ),
      metaData =
        Builders.MetaData(name = "test/payments_join", namespace = namespace, team = "chronon", samplePercent = 30)
    )

    // put this join into kv store
    val kvStoreFunc = () => OnlineUtils.buildInMemoryKVStore("external_test")
    val mockApi = new MockApi(kvStoreFunc, "external_test")
    val fetcher = mockApi.buildFetcher(true)
    fetcher.kvStore.create(ChrononMetadataKey)
    fetcher.putJoinConf(join)

    val requests = (10 until 21).map(x => Request(join.metaData.name, Map(
      "number" -> new Integer(x),
      "str" -> "a",
      "context_1" -> new Integer(2 + x),
      "context_2" -> new Integer(3 + x)
    )))
    val responsesF = fetcher.fetchExternal(requests)
    val responses = Await.result(responsesF, Duration(10, SECONDS))
    responses.map(_.values).foreach(println)
  }
}
