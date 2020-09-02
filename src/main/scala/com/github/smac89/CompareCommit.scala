package com.github.smac89

import argonaut.JsonPath.root
import argonaut.{DecodeJson, DecodeResult}

import scala.language.postfixOps

final case class CompareCommit(permaLink: String, commitShas: List[String])

object CompareCommit {
   implicit val createCodecJson: DecodeJson[CompareCommit] = DecodeJson(
      c => for {
         permaLink <- c.get[String]("permalink_url")
         commits <- DecodeResult.okResult(root.commits.each.sha.string.getAll(c.focus))
      } yield CompareCommit(permaLink = permaLink, commitShas = commits)
   )
//      implicit val createCodecJson: CodecJson[CompareCommit] = CodecJson.casecodec2(apply, unapply)(
//         "permalink_url",
//         "commits"
//      )
}
