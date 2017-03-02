package services

import org.joda.time.DateTime

import scala.concurrent.{ExecutionContext, Future}


trait StatisticsRepository {

  def storeArtists(artists: StoredArtists)(implicit ec: ExecutionContext):Future[Unit]

  def retrieveLatestArtists(userName: String)(implicit ec: ExecutionContext):Future[StoredArtists]

}


case class StoredArtists(
                          when: DateTime,
                          username: String,
                          artists: Array[String]
                        )

