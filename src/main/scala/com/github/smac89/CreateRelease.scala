package com.github.smac89

import ghscala.CodecJson
import httpz.JsonToString

/**
 * Create release data
 *
 * @param name The name of the release
 * @param tagName The tag for the release
 * @param body The description of the release
 * @param draft Whether or not this is a draft release
 */
case class CreateRelease(name: String,
                         tagName: String,
                         body: Option[String] = None,
                         draft: Boolean = true) extends JsonToString[CreateRelease]

object CreateRelease {
   implicit val createCodecJson: CodecJson[CreateRelease] = CodecJson.casecodec4(apply, unapply)(
      "name",
      "tag_name",
      "body",
      "draft"
   )
}
