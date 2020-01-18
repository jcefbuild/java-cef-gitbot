package com.github.smac89

import argonaut.{EncodeJson, PrettyParams}
import httpz.{Action, Core, Request}
import io.lemonlabs.uri.dsl._
import scalaz.Ordering

import scala.language.implicitConversions

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

   implicit object SimpleSemverOrder extends scalaz.Order[SimpleSemver] {
      override def order(x: SimpleSemver, y: SimpleSemver): Ordering = (x, y) match {
         case (SimpleSemver(major1, _, _), SimpleSemver(major2, _, _)) if major1 != major2 =>
            Ordering.fromInt(major1.compare(major2))
         case (SimpleSemver(_, minor1, _), SimpleSemver(_, minor2, _)) if minor1 != minor2 =>
            Ordering.fromInt(minor1.compare(minor2))
         case (SimpleSemver(_, _, patch1), SimpleSemver(_, _, patch2)) if patch1 != patch2 =>
            Ordering.fromInt(patch1.compare(patch2))
         case _ => Ordering.EQ
      }
   }
}
