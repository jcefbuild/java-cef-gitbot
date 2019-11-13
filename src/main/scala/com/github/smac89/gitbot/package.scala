package com.github.smac89

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import argonaut.{DecodeJson, EncodeJson, PrettyParams}
import ghscala.CodecJson
import httpz.{Action, Core, JsonToString, Request}
import io.lemonlabs.uri.dsl._
import scalaz.Ordering

import scala.language.implicitConversions
import scala.util.matching.Regex

package object gitbot {
   type ReleaseId = String

   implicit class GitHubWithRelease(val github: ghscala.Github.type) {
      val baseURL = "https://api.github.com"

      /**
       * Retrieve a list of all releases
       * @param owner The repository owner
       * @param repo The repository name
       * @return The release list action
       */
      def repoReleases(owner: String,
                       repo: String): Action[List[Release]] = {
         Core.json(Request(url = baseURL / s"repos/$owner/$repo/releases"))
      }

      /**
       * Retrieves a single release
       * @param owner The repository owner
       * @param repo The repository name
       * @param releaseId The release id (use [["latest"]] to retrieve the latest release)
       * @return The release action
       */
      def repoReleases(owner: String,
                       repo: String,
                       releaseId: ReleaseId): Action[Release] = {
         val url = s"repos/$owner/$repo/releases/$releaseId"
         Core.json(Request(url = baseURL / url))
      }

      /**
       * Creates a release
       * @param owner The owner of the repository to create the release in
       * @param repo The repository to create a release in
       * @param data The contents of the release. See [[CreateRelease]]
       * @return An action that is used to get the result
       */
      def createRepoRelease(owner: String,
                            repo: String,
                            data: CreateRelease): Action[Release] = {

         val url = s"repos/$owner/$repo/releases"
         Core.json(Request(url = baseURL / url, method = "POST",
            body = Some(EncodeJson.of[CreateRelease]
                                  .apply(data)
                                  .pretty(PrettyParams.nospace.copy(dropNullKeys = true))
                                  .getBytes)))
      }
   }

   /**
    * [[java.time.LocalDateTime]] json encoder/decoder
    */
   implicit val localDateTimeCodec: CodecJson[LocalDateTime] =
      CodecJson.derived(EncodeJson.jencode1(_.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
         DecodeJson.optionDecoder (_.string.map(LocalDateTime.parse(_, DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
            "LocalDateTime"))

   // https://semver.org/
   case class SimpleSemVer(major: String, minor: String, patch: String)

   implicit object SimpleSemVer extends scalaz.Order[SimpleSemVer] {
      val pattern: Regex = """(\d+)\.(\d+).(\d+)""".r.unanchored
      implicit def unapply(arg: CharSequence): Option[SimpleSemVer] = arg match {
         case pattern(major, minor, patch) => Some(SimpleSemVer(major, minor, patch))
         case _ => None
      }

      implicit def unapply(arg: Option[CharSequence]): Option[SimpleSemVer] = arg match {
         case Some(chr) => unapply(chr)
         case _ => None
      }

      override def order(x: SimpleSemVer, y: SimpleSemVer): Ordering = (x, y) match {
         case (SimpleSemVer(major1, _, _), SimpleSemVer(major2, _, _)) if major1 != major2 =>
            scalaz.Ordering.fromInt(major1.compare(major2))
         case (SimpleSemVer(_, minor1, _), SimpleSemVer(_, minor2, _)) if minor1 != minor2 =>
            scalaz.Ordering.fromInt(minor1.compare(minor2))
         case (SimpleSemVer(_, _, patch1), SimpleSemVer(_, _, patch2)) if patch1 != patch2 =>
            scalaz.Ordering.fromInt(patch1.compare(patch2))
         case _ => scalaz.Ordering.EQ
      }
   }

   /**
    * Create release data
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
}
