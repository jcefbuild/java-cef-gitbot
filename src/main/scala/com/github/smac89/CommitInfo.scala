package com.github.smac89

import ghscala.{CodecJson, Commit}
import httpz.JsonToString

case class CommitInfo(commit: Commit) extends JsonToString[CommitInfo]

object CommitInfo {
   implicit val createCodecJson: CodecJson[CommitInfo] = CodecJson.casecodec1(apply, unapply)(
      "commit"
   )
}
