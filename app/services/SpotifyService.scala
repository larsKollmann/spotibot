package services

import scala.concurrent.{ExecutionContext, Future}


trait SpotifyService {


  def fetchLatestTopArtists(userName: String)(implicit ec: ExecutionContext): Future[SpotifyArtists]

  def postTweet (message: String)(implicit ec: ExecutionContext):Future[Unit]

}

case class SpotifyArtists(Artists: Array[String])

