package services

import com.google.common.collect.MapDifference.ValueDifference
import org.joda.time.{DateTime, Period}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

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

          println(artistdifference.mkString(" "))

          def phrasing(difference: Array[String]): String = if (difference.isEmpty) "remained the same" else "changed"

          val durationInDays = new Period(previous.when, DateTime.now).getDays

          spotifyService.postTweet(
            s"@$username in the past $durationInDays your artists " +
              s"${phrasing(artistdifference)} ${artistdifference.mkString(" ")}"
          )

      }
    }

    val previousArtists: Future[StoredArtists] = statisticsRepository.retrieveLatestArtists(username)

    val currentArtists: Future[SpotifyArtists] = spotifyService.fetchLatestTopArtists(Option(username))

    val counts: Future[(StoredArtists, SpotifyArtists)] = for {
      previous <- previousArtists
      current <- currentArtists
    } yield {
      (previous, current)
    }

    val storedArtists: Future[Unit] = counts.flatMap(storeArtists)
    val publishedMessage: Future[Unit] = counts.flatMap(publishMessage)

    val result = for {
      _ <- storedArtists
      _ <- publishedMessage
    } yield {}

    result recoverWith {
      case CountStorageException(countsToStore) =>
        retryStoring(countsToStore, attemptNumber = 0)
    } recover {
      case CountStorageException(countsToStore) =>
        throw StatisticsServiceFailed("We couldn't save the statistics to our database. Next time it will work!")
      case CountRetrievalException(user, cause) =>
        throw StatisticsServiceFailed("We have a problem with our database. Sorry!", cause)
      case NonFatal(t) =>
        throw StatisticsServiceFailed("We have an unknown problem. Sorry!", t)
    }

  }

  private def retryStoring(counts: StoredArtists, attemptNumber: Int)(implicit ec: ExecutionContext): Future[Unit] = {
    if (attemptNumber < 3) {
      statisticsRepository.storeArtists(counts).recoverWith {
        case NonFatal(t) => retryStoring(counts, attemptNumber + 1)
      }
    } else {
      Future.failed(CountStorageException(counts))
    }
  }

}

class StatisticsServiceFailed(cause: Throwable)
  extends RuntimeException(cause) {
  def this(message: String) = this(new RuntimeException(message))

  def this(message: String, cause: Throwable) =
    this(new RuntimeException(message, cause))
}

object StatisticsServiceFailed {
  def apply(message: String): StatisticsServiceFailed =
    new StatisticsServiceFailed(message)

  def apply(message: String, cause: Throwable):
  StatisticsServiceFailed =
    new StatisticsServiceFailed(message, cause)
}