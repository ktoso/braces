package org.h3nk3.braces.web

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, StandardRoute}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink

import scala.concurrent.{Future, Promise}

object HttpMain_Step2 extends App 
  with Directives with OurOwnWebSocketSupport { 

  import org.h3nk3.braces.domain.Domain._
  
  implicit val system = ActorSystem("HttpApp")
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  
  Http().bindAndHandle(routes, "127.0.0.1", 8080)

  override def ingestionHub: Sink[Any, Future[Done]] = Sink.ignore

  // format: OFF
  def routes =
    path("drone" / "data") {
      entity(asSourceOf[DroneInfo]) { infos =>
        infos.to(ingestionHub).run()
        neverRespond()
      }
    }
  // format: ON
  
  private def neverRespond() = 
    complete(Promise[String]().future)
}