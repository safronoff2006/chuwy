package me.chuwy.otusfp

import scala.concurrent.duration._
/*
object Main extends IOApp.Simple {
  import io.circe.literal._
  import io.circe.syntax._
  import io.circe.parser.parse


  def run: IO[Unit] = {
    val exampleJson = json"""{"name": "Bob", "id": 42}"""
    val result = User("Alice", 1).asJson

    for {
//      _ <- Restful.builder.resource.use(_ => IO.never).start
//      _ <- IO.sleep(2.seconds)
      _ <- Last.requestWithUser
    } yield ()
  }
}
*/