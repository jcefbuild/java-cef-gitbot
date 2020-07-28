package com.github.smac89

import ghscala.{CodecJson, CommitResponse}

case class CompareCommit(permaLink: String, commits: List[CommitResponse])

object CompareCommit {
   implicit val createCodecJson: CodecJson[CompareCommit] = CodecJson.casecodec2(apply, unapply)(
      "permalink_url",
      "commits"
   )
}
