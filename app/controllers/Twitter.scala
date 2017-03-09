package controllers

import javax.inject.Inject

import play.api.libs.oauth.{ConsumerKey, OAuth, RequestToken, ServiceInfo}
import play.api.{Configuration, Play}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller, RequestHeader}

class Twitter @Inject()(ws: WSClient, configuration: Configuration) extends Controller {

  val oauth = OAuth(ServiceInfo(
    "https://api.twitter.com/oauth/request_token",
    "https://api.twitter.com/oauth/access_token",
    "https://api.twitter.com/oauth/authorize", credentials.get),
    true)

  private val credentials = for {
    apiKey <- configuration.getString("twitter.apikey")
    apiSecret <- configuration.getString("twitter.apisecret")
    callbackUrl <- configuration.getString("twitter.callbackUrl")
  } yield ConsumerKey(apiKey, apiSecret)

  def sessionTokenPair(implicit request: RequestHeader): Option[RequestToken] = {
    for {
      token <- request.session.get("token")
      secret <- request.session.get("secret")
    } yield {
      RequestToken(token, secret)
    }
  }

  def authenticate = Action { request =>
    request.getQueryString("oauth_verifier").map { verifier =>
      val tokenPair = sessionTokenPair(request).get
      // We got the verifier; now get the access token, store it and back to index
      oauth.retrieveAccessToken(tokenPair, verifier) match {
        case Right(t) => {
          // We received the authorized tokens in the OAuth object - store it before we proceed
          Redirect(routes.Twitter.index).withSession("token" -> t.token, "secret" -> t.secret)
        }
        case Left(e) => throw e
      }
    }.getOrElse(
      oauth.retrieveRequestToken("https://localhost:9000/auth") match {
        case Right(t) => {
          // We received the unauthorized tokens in the OAuth object - store it before we proceed
          Redirect(oauth.redirectUrl(t.token)).withSession("token" -> t.token, "secret" -> t.secret)
        }
        case Left(e) => throw e
      })
  }


  def topArtists = Action {
    Ok
  }

}
