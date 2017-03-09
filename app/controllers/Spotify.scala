package controllers

import javax.inject._

import com.google.common.base.Strings
import org.apache.commons.lang3.RandomStringUtils
import play.api._
import play.api.libs.ws.ahc.AhcCurlRequestLogger
import play.api.libs.ws.{WSAuthScheme, WSClient}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.libs.Json
import services.{DefaultStatisticsService, MongoStatisticsRepository, SpotifyArtists, WSSpotifyService}

import scala.concurrent.Future

class Spotify @Inject()(ws: WSClient, configuration: Configuration, mongoStatisticsRepository: MongoStatisticsRepository) extends
  Controller {

  val spotifyService = new WSSpotifyService(ws, configuration)
  val statisticsService = new DefaultStatisticsService(mongoStatisticsRepository,spotifyService)
  val stateKey = "spotify_auth_state"

  lazy val clientId = configuration.getString("spotify.clientId").get
  lazy val clientSecret = configuration.getString("spotify.clientSecret").get
  lazy val redirectUri = configuration.getString("spotify.callbackUrl").get

  def index = Action {
    Ok(views.html.index.render())
  }

  def accessToken(implicit request: RequestHeader): Option[String] = {
    request.session.get("access_token")
  }

  def login = Action {
    val currentState = RandomStringUtils.random(16, true, true)
    Redirect("https://accounts.spotify.com/authorize",
      Map("response_type" -> List("code"),
        "client_id" -> List(clientId),
        "scope" -> List("user-top-read"),
        "redirect_uri" -> List(redirectUri),
        "state" -> List(currentState)))
      .withCookies(Cookie(stateKey, currentState, path = "/", maxAge = Some(30000)))
  }


  def showMe = Action.async { implicit request =>
    val artists = spotifyService.fetchLatestTopArtists(accessToken)
    val tweet = statisticsService.createUserStatistics(accessToken.get)
    artists.map(artistList => Ok(views.html.topartists(artistList))).
      recover { case thrown => Redirect(routes.Spotify.login()) }
  }

  def callback(code: String, state: String) = Action.async { request =>
    request.cookies.get(stateKey).map[Future[Result]] { c =>
      val currentState = c.value
      if (state != currentState) {
        Logger.info(s"state mismatch Server: $state; Local: $currentState")
        Future.successful(Redirect("/#error:state_mismatch"))
      } else {
        ws.url("https://accounts.spotify.com/api/token")
          .withAuth(clientId, clientSecret, WSAuthScheme.BASIC)
          .post(Map(
            "code" -> Seq(code),
            "redirect_uri" -> Seq(redirectUri),
            "grant_type" -> Seq("authorization_code")
          )).flatMap[Result] { resp =>
          val jsonResp = resp.json
          val accessToken = (jsonResp \ "access_token").get.as[String]
          val refreshToken = (jsonResp \ "refresh_token").get.as[String]

          Future.successful(
            Redirect(routes.Spotify.showMe())
              .discardingCookies(DiscardingCookie(stateKey))
              .withSession("access_token" -> accessToken, "refresh_token" -> refreshToken))
        }
      }
    }.getOrElse(Future.successful(Redirect("/#error:no_cookies")))
  }


}
