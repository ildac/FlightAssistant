package streams

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.unmarshalling.Unmarshal
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future

case class PostEntity(userId:Int, id:Int, title:String, body:String)

trait CustomJsonProtocol extends DefaultJsonProtocol {
  implicit val templateFormat = jsonFormat4(PostEntity.apply)
}

object MyFirstStream extends CustomJsonProtocol {
  import akka.actor.ActorSystem
  import akka.event.Logging
  import akka.http.scaladsl.Http
  import akka.http.scaladsl.model.StatusCodes._
  import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
  import akka.stream._
  import akka.stream.scaladsl._
  import com.typesafe.config.ConfigFactory

  implicit val system = ActorSystem("Sys")
  implicit val materializer = ActorMaterializer()
  implicit val executor = system.dispatcher

  val logger = Logging(system, getClass)
  val config = ConfigFactory.load()

  val source = Source.single(HttpRequest(HttpMethods.GET, "/posts/1"))

  val printToConsoleFlow = Flow[HttpResponse].map((s) => {
    s.status match {
      case OK => Unmarshal(s.entity).to[PostEntity]
      case _ => Unmarshal(s.entity).to[String].flatMap(
        entity => {
          val err = s"Got not OK status. Status: ${s.status}, with message: $entity"
          logger.error(err)
          Future.failed(new Exception(err))
        })
    }
  })

  val freeGeoIpConnectionFlow =
    Http().outgoingConnection("jsonplaceholder.typicode.com", 80)

  def main(args: Array[String]): Unit = {

    val runnable = RunnableGraph.fromGraph(GraphDSL.create() {
      implicit builder =>
        import GraphDSL.Implicits._

        val A: Outlet[HttpRequest] = builder.add(source).out

        val B: FlowShape[HttpRequest, HttpResponse] = builder.add(freeGeoIpConnectionFlow)
        val C: FlowShape[HttpResponse, Future[PostEntity]] = builder.add(printToConsoleFlow)

        val D: Inlet[Any] = builder.add(Sink.foreach(println)).in

        A ~> B ~> C ~> D

        ClosedShape
    })

    runnable.run

  }
}