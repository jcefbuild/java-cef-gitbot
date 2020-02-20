package com.github.smac89

import java.time.OffsetDateTime

import argonaut.{CodecJson, DecodeJson, EncodeJson}
import ghscala.User
import httpz.JsonToString

/**
 * Partial release data
 * @param name The name of the release
 * @param tagName The name of the tag associated with this release
 * @param body The description of the release
 * @param author The name of the author who authored this release
 * @param draft Is this a draft release?
 * @param prerelease Is this a pre-release?
 * @param createdAt The date the commit for the release
 * @param publishedAt The date this release was published
 */
case class Release(name: String,
                   tagName: String,
                   body: Option[String],
                   author: User,
                   draft: Boolean,
                   prerelease: Boolean,
                   createdAt: OffsetDateTime,
                   publishedAt: Option[OffsetDateTime]) extends JsonToString[Release]

object Release {
   /**
    * [[java.time.OffsetDateTime]] json encoder/decoder
    */
   implicit val offsetDateTimeCodec: CodecJson[OffsetDateTime] =
      CodecJson.derived(EncodeJson.jencode1(_.toString),
         DecodeJson.optionDecoder (_.string.map(OffsetDateTime.parse),
            "OffsetDateTime"))

   implicit val releaseCodecJson: CodecJson[Release] = CodecJson.casecodec8(apply, unapply) (
      "name",
      "tag_name",
      "body",
      "author",
      "draft",
      "prerelease",
      "created_at",
      "published_at"
   )
}
