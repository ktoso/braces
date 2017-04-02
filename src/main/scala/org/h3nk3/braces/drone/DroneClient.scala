package org.h3nk3.braces.drone


import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{CoupledTerminationFlow, Flow, Sink, Source}
import com.typesafe.config.ConfigFactory
import org.h3nk3.braces.backend.InputParser
import org.h3nk3.braces.domain.Domain._
import org.h3nk3.braces.domain.OurDomainJsonSupport

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.Random

object DroneClient extends InputParser with OurDomainJsonSupport {
  import sys.dispatcher
  implicit val sys = ActorSystem("DroneClient-" + System.currentTimeMillis(), ConfigFactory.load("drone-client.conf"))
  implicit val mat = ActorMaterializer()

  private val startTime: Long = System.currentTimeMillis()
  private val basePosition: Position = Position(sys.settings.config.getDouble("braces.client.base.lat"), sys.settings.config.getDouble("braces.client.base.long"))
  private val maxVelocity: Double = sys.settings.config.getDouble("braces.client.max-velocity")
  private val xCoordinates: Int = sys.settings.config.getInt("braces.client.x-coordinates")
  private val yCoordinates: Int = sys.settings.config.getInt("braces.client.y-coordinates")
  private var currentPosition = 0

  // Explain why this might need to be protected...
  private var upperLeft: Option[Position] = None
  private var lowerRight: Option[Position] = None
  private var incrementalLatDistance: Double = 0.0
  private var incrementalLongDistance: Double = 0.0

  var droneId: Int = 0

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      println("**** You must provide a drone id when starting the drone client. ****")
      System.exit(0)
    } else {
      droneId = args.head.toInt
      bootstrap()
      println(s"*** Drone client $droneId running\nType 'e|exit' to quit the application. Type 'h|help' for information.")
      commandLoop()
    }
  }

  @tailrec
  def commandLoop(): Unit = {
    Cmd(StdIn.readLine()) match {
      case Cmd.Help =>
        println("Available commands:")
        println("h: Help")
        println("e: Exit")
        commandLoop()
      case Cmd.Unknown(s) =>
        println(s"Unknown command: $s")
        commandLoop()
      case Cmd.Exit =>
        println("Exiting application. Bye!")
        sys.terminate()
      case Cmd.Initiate =>
        // ignore
        commandLoop()
      case Cmd.Stop =>
        // ignore
        commandLoop()
      case Cmd.AddDrone =>
        // ignore
        commandLoop()
    }
  }

  def bootstrap(): Unit = {
    Http().singleWebSocketRequest(
      WebSocketRequest(s"${sys.settings.config.getString("braces.http-server")}drone/$droneId"),
      clientFlow = emitPositionAndMetrics)
  }

  def handleCommand(json: akka.http.scaladsl.model.ws.Message): Unit = {
    Unmarshal.apply(json).to[DroneClientCommand] map { _ match {
      case SurveilArea(upperLeft, lowerRight) =>
        this.upperLeft = Some(upperLeft)
        this.lowerRight = Some(lowerRight)
        incrementalLatDistance = 
    }}
  }

  def emitPositionAndMetrics: Flow[Message, Message, Any] =
    CoupledTerminationFlow.fromSinkAndSource(
      Sink.foreach(handleCommand(_)),
      Source.tick(initialDelay = 1.second, interval = 1.second, tick = NotUsed)
        .map(_ => getCurrentInfo)
        .via(renderAsJson)
        .map(TextMessage(_))
    )

  def getCurrentInfo: DroneData = {
    def position(): Position = {
      Position(0.0, 0.0)
    }

    def velocity(): Double = {
      maxVelocity - Random.nextDouble() // let the velocity fluctuate a bit (depending on winds)
    }

    def direction: Int =
      if ((currentPosition / yCoordinates) % 2 == 0) 90
      else 270

    def batteryPower: Int = {
      100 - ((System.currentTimeMillis() - startTime) / 60000).toInt // drain 1 % per minute
    }

    if (lowerRight.isDefined) DroneData(droneId, Operating, position(), velocity, direction, batteryPower)
    else DroneData(droneId, Ready, Position(0.0, 0.0), 0.0, 0, 100)
  }

  val renderAsJson: Flow[DroneData, String, NotUsed] =
    Flow[DroneData]
      .mapAsync(parallelism = 1)(p => Marshal(p).to[HttpEntity].flatMap(_.toStrict(1.seconds)))
      .map(_.data.utf8String)

}
