package com.github.smac89

import argonaut.{EncodeJson, PrettyParams}
import com.redis.RedisClient
import httpz.{Action, Core, Request}
import io.lemonlabs.uri.dsl._
import scalaz.Ordering

import scala.language.implicitConversions

package object gitbot {
   type ReleaseId = String

   lazy val redis = new RedisClient(sys.env("REDIS_URL").url.toJavaURI)

   implicit class EnhancedGithub(val github: ghscala.Github.type) {
      val baseURL = "https://api.github.com"

      object Commits {
         /**
          * List the commits on a repository
          * https://developer.github.com/v3/repos/commits/#list-commits-on-a-repository
          *
          * @param owner        The repository owner
          * @param repo         The repository path
          * @param filterParams GET parameters for the query
          * @return List of commits
          */
         def list(owner: String,
                  repo: String,
                  filterParams: Map[String, String] = Map()): Action[List[CommitInfo]] =
            Core.json(Request(url = baseURL / s"repos/$owner/$repo/commits",
               params = (
                  "sha" -> filterParams.get("sha"),
                  "path" -> filterParams.get("path"),
                  "author" -> filterParams.get("author"),
                  "since" -> filterParams.get("since"),
                  "until" -> filterParams.get("until")).productIterator.collect {
                  case (param, Some(value)) => (param, value)
               }.asInstanceOf[Iterator[(String, String)]].toMap))
      }

      object Repo {
         /**
          * Retrieves a single release
          *
          * @param owner     The repository owner
          * @param repo      The repository name
          * @param releaseId The release id (use [["latest"]] to retrieve the latest release)
          * @return The release action
          */
         def releases(owner: String,
                      repo: String,
                      releaseId: ReleaseId): Action[Release] = {
            val url = s"repos/$owner/$repo/releases/$releaseId"
            Core.json(Request(url = baseURL / url))
         }

         /**
          * Retrieve a list of all releases
          *
          * @param owner The repository owner
          * @param repo  The repository name
          * @return The release list action
          */
         def releases(owner: String,
                      repo: String): Action[List[Release]] = {
            Core.json(Request(url = baseURL / s"repos/$owner/$repo/releases"))
         }

         /**
          * Creates a release
          *
          * @param owner The owner of the repository to create the release in
          * @param repo  The repository to create a release in
          * @param data  The contents of the release. See [[CreateRelease]]
          * @return An action that is used to get the result
          */
         def createRelease(owner: String,
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

   }

   implicit object SimpleSemverOrder extends scalaz.Order[SimpleSemver] {
      override def order(x: SimpleSemver, y: SimpleSemver): Ordering = (x, y) match {
         case (SimpleSemver(major1, _, _, _), SimpleSemver(major2, _, _, _)) if major1 != major2 =>
            Ordering.fromInt(major1.compare(major2))
         case (SimpleSemver(_, minor1, _, _), SimpleSemver(_, minor2, _, _)) if minor1 != minor2 =>
            Ordering.fromInt(minor1.compare(minor2))
         case (SimpleSemver(_, _, patch1, _), SimpleSemver(_, _, patch2, _)) if patch1 != patch2 =>
            Ordering.fromInt(patch1.compare(patch2))
         case _ => Ordering.EQ
      }
   }
}
