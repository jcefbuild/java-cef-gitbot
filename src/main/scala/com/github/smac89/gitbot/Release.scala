package com.github.smac89.gitbot

import java.time.LocalDateTime

import argonaut.CodecJson
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
                   createdAt: LocalDateTime,
                   publishedAt: Option[LocalDateTime]) extends JsonToString[Release]

object Release {
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
