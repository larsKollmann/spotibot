package services

import javax.inject.Inject

import org.joda.time.DateTime
import play.modules.reactivemongo._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal


trait StatisticsRepository {

  def storeArtists(artists: StoredArtists)(implicit ec: ExecutionContext):Future[Unit]

  def retrieveLatestArtists(userName: String)(implicit ec: ExecutionContext):Future[StoredArtists]

}


case class StoredArtists(
                          when: DateTime,
                          username: String,
                          artists: Array[String]
                        )

object StoredArtists {
  implicit object UserCountsReader extends BSONDocumentReader[StoredArtists] with BSONDocumentWriter[StoredArtists] {
    override def read(bson: BSONDocument): StoredArtists = {
      val when = bson.getAs[BSONDateTime]("when").map(t => new DateTime(t.value)).get
      val userName = bson.getAs[String]("userName").get
      val artists = bson.getAs[Array[String]]("artists").get
      StoredArtists(when,userName,artists)
    }

    override def write(t: StoredArtists): BSONDocument = BSONDocument(
      "when" -> BSONDateTime(t.when.getMillis),
      "userName" -> t.username,
      "artists" -> t.artists
    )
  }
}

class MongoStatisticsRepository @Inject() (reactiveMongo: ReactiveMongoApi) extends StatisticsRepository {

  private val StatisticsCollection = "UserStatistics"

  private lazy val collection = reactiveMongo.db.collection[BSONCollection](StatisticsCollection)

  override def storeArtists(counts: StoredArtists)(implicit ec: ExecutionContext): Future[Unit] = {
    collection.insert(counts).map { lastError =>
      if(lastError.inError) {
        throw CountStorageException(counts)
      }
    }
  }

  override def retrieveLatestArtists(userName: String)(implicit ec: ExecutionContext): Future[StoredArtists] = {
    val query = BSONDocument("userName" -> userName)
    val order = BSONDocument("_id" -> -1)
    collection
      .find(query)
      .sort(order)
      .one[StoredArtists]
      .map { counts => counts getOrElse StoredArtists(DateTime.now, userName, Array()) }
  } recover {
    case NonFatal(t) =>
      throw CountRetrievalException(userName, t)
  }
}

case class CountRetrievalException(userName: String, cause: Throwable) extends RuntimeException("Could not read counts for " + userName, cause)
case class CountStorageException(counts: StoredArtists) extends RuntimeException