package com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.endpoint

import com.cyrelis.srag.infrastructure.adapters.driving.gateway.rest.dto.test.*
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

object TestEndpoints {

  val testTranscriber: PublicEndpoint[TestTranscriberRestDto, String, TestTranscriberResultRestDto, Any] =
    sttp.tapir.endpoint.post
      .in("test" / "transcriber")
      .in(multipartBody[TestTranscriberRestDto])
      .out(jsonBody[TestTranscriberResultRestDto])
      .errorOut(stringBody)
      .description("Test transcriber adapter with multipart audio file")

  val testEmbedder: PublicEndpoint[TestEmbedderRestDto, String, TestEmbedderResultRestDto, Any] =
    sttp.tapir.endpoint.post
      .in("test" / "embedder")
      .in(jsonBody[TestEmbedderRestDto])
      .out(jsonBody[TestEmbedderResultRestDto])
      .errorOut(stringBody)
      .description("Test embedder adapter with text")

  val testBlobStore: PublicEndpoint[TestBlobStoreRestDto, String, TestResultRestDto, Any] =
    sttp.tapir.endpoint.post
      .in("test" / "blobstore")
      .in(multipartBody[TestBlobStoreRestDto])
      .out(jsonBody[TestResultRestDto])
      .errorOut(stringBody)
      .description("Test blob store adapter with multipart audio file")

  val testDatabase: PublicEndpoint[TestDatabaseRestDto, String, TestResultRestDto, Any] =
    sttp.tapir.endpoint.post
      .in("test" / "database")
      .in(jsonBody[TestDatabaseRestDto])
      .out(jsonBody[TestResultRestDto])
      .errorOut(stringBody)
      .description("Test database adapter")

  val testVectorStore: PublicEndpoint[TestVectorStoreRestDto, String, TestResultRestDto, Any] =
    sttp.tapir.endpoint.post
      .in("test" / "vectorstore")
      .in(jsonBody[TestVectorStoreRestDto])
      .out(jsonBody[TestResultRestDto])
      .errorOut(stringBody)
      .description("Test vector store adapter - upsert embeddings")

  val testVectorStoreQuery
    : PublicEndpoint[TestVectorStoreQueryRestDto, String, TestVectorStoreQueryResultRestDto, Any] =
    sttp.tapir.endpoint.post
      .in("test" / "vectorstore" / "query")
      .in(jsonBody[TestVectorStoreQueryRestDto])
      .out(jsonBody[TestVectorStoreQueryResultRestDto])
      .errorOut(stringBody)
      .description("Test vector store adapter - search similar vectors")

  val getAllTranscripts: PublicEndpoint[Unit, String, List[TranscriptResponseDto], Any] =
    sttp.tapir.endpoint.get
      .in("test" / "transcripts")
      .out(jsonBody[List[TranscriptResponseDto]])
      .errorOut(stringBody)
      .description("Get all transcripts from database")

  val testReranker: PublicEndpoint[TestRerankerRestDto, String, TestRerankerResultRestDto, Any] =
    sttp.tapir.endpoint.post
      .in("test" / "reranker")
      .in(jsonBody[TestRerankerRestDto])
      .out(jsonBody[TestRerankerResultRestDto])
      .errorOut(stringBody)
      .description("Test reranker adapter with query and candidates")

  val testLexicalStoreIndex: PublicEndpoint[TestLexicalStoreIndexRestDto, String, TestResultRestDto, Any] =
    sttp.tapir.endpoint.post
      .in("test" / "lexicalstore" / "index")
      .in(jsonBody[TestLexicalStoreIndexRestDto])
      .out(jsonBody[TestResultRestDto])
      .errorOut(stringBody)
      .description("Test lexical store adapter - index segments")

  val testLexicalStoreSearch
    : PublicEndpoint[TestLexicalStoreQueryRestDto, String, TestLexicalStoreQueryResultRestDto, Any] =
    sttp.tapir.endpoint.post
      .in("test" / "lexicalstore" / "search")
      .in(jsonBody[TestLexicalStoreQueryRestDto])
      .out(jsonBody[TestLexicalStoreQueryResultRestDto])
      .errorOut(stringBody)
      .description("Test lexical store adapter - search segments")

  def all: List[PublicEndpoint[?, ?, ?, ?]] =
    List(
      testTranscriber,
      testEmbedder,
      testBlobStore,
      testDatabase,
      testVectorStore,
      testVectorStoreQuery,
      getAllTranscripts,
      testReranker,
      testLexicalStoreIndex,
      testLexicalStoreSearch
    )
}
