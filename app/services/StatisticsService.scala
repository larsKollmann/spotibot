package services

import com.google.common.collect.MapDifference.ValueDifference
import org.joda.time.{DateTime, Period}

import scala.concurrent.{ExecutionContext, Future}

trait StatisticsService {

  def createUserStatistics(username: String)(implicit ec: ExecutionContext): Future[Unit]

}

class DefaultStatisticsService(
                                statisticsRepository: StatisticsRepository,
                                spotifyService: SpotifyService) extends StatisticsService {

  override def createUserStatistics(username: String)(implicit ec: ExecutionContext): Future[Unit] = {

    def storeArtists(artists: (StoredArtists, SpotifyArtists)): Future[Unit] =
      artists match {
        case (previous, current) =>
          statisticsRepository.storeArtists(StoredArtists(DateTime.now, username, current.Artists))
      }

    def publishMessage(counts: (StoredArtists, SpotifyArtists)): Future[Unit] = {
      counts match {
        case (previous, current) => val artistdifference = previous.artists.diff(current.Artists)

          def phrasing(difference: Array[String]): String = if (difference.isEmpty) "remained the same" else "changed"

          val durationInDays = new Period(previous.when, DateTime.now).getDays

          spotifyService.postTweet(
            s"@$username in the past $durationInDays your artists " +
              s"${phrasing(artistdifference)} ${artistdifference.mkString}"
          )

      }
    }

    val previousArtists: Future[StoredArtists] = statisticsRepository.retrieveLatestArtists(username)

    val currentArtists: Future[SpotifyArtists] = spotifyService.fetchLatestTopArtists(username)

    val counts: Future[(previousArtists, currentArtists)] = for {
      previous <- previousArtists
      current <- currentArtists
    } yield {
      (previous, current)
    }
    Future.successful({})
  }


}