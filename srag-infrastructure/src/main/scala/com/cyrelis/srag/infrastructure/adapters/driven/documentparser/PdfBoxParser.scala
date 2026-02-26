package com.cyrelis.srag.infrastructure.adapters.driven.documentparser

import java.util.Base64

import com.cyrelis.srag.application.errors.PipelineError
import com.cyrelis.srag.application.ports.DocumentParserPort
import com.cyrelis.srag.infrastructure.resilience.ErrorMapper
import zio.*

private final class PdfBoxParser extends DocumentParserPort {

  override def parseDocument(documentContent: String, mediaType: String): ZIO[Any, PipelineError, String] =
    ErrorMapper.mapDocumentParserError {
      mediaType match {
        case mt if mt.startsWith("application/pdf") =>
          parsePdf(documentContent)
        case mt if mt.contains("officedocument") || mt.contains("msword") =>
          parseOfficeDocument(documentContent, mt)
        case mt if mt.startsWith("text/") =>
          parseTextDocument(documentContent)
        case unsupported =>
          ZIO.fail(new UnsupportedOperationException(s"Unsupported media type: $unsupported"))
      }
    }

  private def parsePdf(base64Content: String): Task[String] =
    ZIO.attempt {
      // TODO: Implement with Apache PDFBox
      // val bytes = Base64.getDecoder.decode(base64Content)
      // val document = PDDocument.load(bytes)
      // try {
      //   val stripper = new PDFTextStripper()
      //   stripper.getText(document)
      // } finally {
      //   document.close()
      // }

      val _ = Base64.getDecoder.decode(base64Content)
      "[PDF text extraction not yet implemented]"
    }

  private def parseOfficeDocument(base64Content: String, mediaType: String): Task[String] =
    ZIO.attempt {
      // TODO: Implement with Apache POI for .docx, .xlsx, etc.
      val _ = Base64.getDecoder.decode(base64Content)
      s"[Office document extraction not yet implemented for $mediaType]"
    }

  private def parseTextDocument(base64Content: String): Task[String] =
    ZIO.attempt {
      val bytes = Base64.getDecoder.decode(base64Content)
      new String(bytes, "UTF-8")
    }
}

object PdfBoxParser {
  val layer: ZLayer[Any, Nothing, DocumentParserPort] =
    ZLayer.succeed(new PdfBoxParser())
}
