package services

import javax.inject.Inject

import controllers.routes
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcCurlRequestLogger
import play.api.mvc.Result
import play.api.{Configuration, Environment}
import play.libs.Json

import scala.concurrent.{ExecutionContext, Future}


trait SpotifyService {


  def fetchLatestTopArtists(token: Option[String])(implicit ec: ExecutionContext): Future[SpotifyArtists]

  def postTweet(message: String)(implicit ec: ExecutionContext): Future[Unit]

}

case class SpotifyArtists(Artists: Array[String])


class WSSpotifyService @Inject()(ws: WSClient, configuration: Configuration) extends SpotifyService {
  override def fetchLatestTopArtists(token: Option[String])(implicit ec: ExecutionContext): Future[services.SpotifyArtists] = {
    token match {
      case Some(token) =>
        ws.url("https://api.spotify.com/v1/me/top/artists?limit=5")
          .withHeaders("Authorization" -> ("Bearer " + token))
          .withRequestFilter(AhcCurlRequestLogger())
          .get.map[SpotifyArtists] { meResp =>
          SpotifyArtists((Json.parse(meResp.body).findValues("name").toArray.map(_.toString)))
        }
      case _ => Future.failed(new SpotifyServiceException("Login failed"))
    }
  }

  override def postTweet(message: String)(implicit ec: ExecutionContext): Future[Unit] = ???


  private def credentials = for {
    clientId <- configuration.getString("spotify.clientId")
    clientSecret <- configuration.getString("spotify.clientSecret")
    callbackUrl <- configuration.getString("spotify.callbackUrl")
  } yield (clientId, clientSecret, callbackUrl)

}


case class SpotifyServiceException(message: String) extends RuntimeException(message)