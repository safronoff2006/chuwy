package homework

import cats.data.{Validated, ValidatedNec}
import cats.effect.{IO, IOApp, Ref}
import cats.implicits.catsSyntaxTuple3Semigroupal
import fs2.Chunk
import fs2.io.file.Files
import io.circe._
import io.circe.generic.semiauto._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.{HttpRoutes, Request, Response}

import java.time._
import scala.concurrent.duration._
import scala.language.postfixOps

object homework_to_lesson_28 {

  case class Counter(counter: Long)

  implicit val counterEncoder: Encoder[Counter] = deriveEncoder[Counter]
  implicit val counterDecoder: Decoder[Counter] = deriveDecoder[Counter]

  val path = "src/test/scala/homework/FunctionalProgrammingInScala.pdf"

  private def validateStringToIntMore0 (value: String, error: String): Validated[String, Int] = {
    val intPatternGood =    value.matches("^\\d+$")

    val valueGood = if (intPatternGood) {
      if (value.toInt > 0) true else false
    } else false

    if (valueGood)
      Validated.Valid(value.toInt)
    else
      Validated.Invalid(error)
  }

  case class ValidPath(chunk: Int, total: Int, time: Int)

  def validatePath(chunk: String, total: String, time: String): ValidatedNec[String, ValidPath] = (
    validateStringToIntMore0(chunk, "chunk должен быть целым больше нуля").toValidatedNec,
    validateStringToIntMore0(total, "total должен быть целым больше нуля").toValidatedNec,
    validateStringToIntMore0(time, "time должен быть целым больше нуля").toValidatedNec,
  ).mapN { (chunk, total, time) => ValidPath(chunk, total, time) }


  def slow(params: ValidPath): IO[Response[IO]] = {
    val httpdsl: Http4sDsl[IO] = Http4sDsl[IO]
    import httpdsl._

    val stream: fs2.Stream[IO, Chunk[Byte]] = Files[IO].readAll(java.nio.file.Path.of(path), params.chunk)
      .chunkN(params.chunk, false)
      .metered(params.time seconds)
      .take(params.total / params.chunk)

    Ok(stream)
  }


  def routes(ref: Ref[IO, Long]): HttpRoutes[IO] = {
    val httpdsl: Http4sDsl[IO] = Http4sDsl[IO]
    import httpdsl._

    HttpRoutes.of[IO] {
      case GET -> Root / "counter" => ref.getAndUpdate(counter => counter + 1).flatMap(result => Ok(Counter(result + 1)))

      case GET -> Root / "slow" / chunk / total / time =>

        val validPathEither = validatePath(chunk, total, time) match {
          case Validated.Valid(valid) => Right(valid)
          case Validated.Invalid(e) => Left(new Exception(e.toNonEmptyList.toList.mkString(",")))
        }

        validPathEither match {
          case Left(exp) => BadRequest(exp.getMessage)
          case Right(validPath) => slow(validPath)
        }

    }


  }

  def router(ref: Ref[IO, Long]): HttpRoutes[IO] = Router("/" -> routes(ref))

  def server(ref: Ref[IO, Long]): BlazeServerBuilder[IO] = {
    BlazeServerBuilder[IO](scala.concurrent.ExecutionContext.global)
      .bindHttp(9091, "localhost")
      .withHttpApp(router(ref).orNotFound)
  }

}

object Server extends IOApp.Simple {
  override def run: IO[Unit] = {
    val refWithIo: IO[Ref[IO, Long]] = Ref[IO].of(0L)
    refWithIo.flatMap(ref => homework_to_lesson_28.server(ref).resource.use(_ => IO.never))
  }
}

object TestWithClient extends IOApp.Simple {
import homework_to_lesson_28._
  implicit val counterDecoder: Decoder[Counter] = deriveDecoder

  private val req1: Request[IO] = Request( uri = uri"/counter")

  def client(counter: Ref[IO, Long]): Client[IO] = Client.fromHttpApp(router(counter).orNotFound)

  private val counterInit: Long = 22L
  private val expected: Long = 23L


  private val req2: Request[IO] = Request( uri =  uri"/show/500/4000/1")

  def run: IO[Unit] = for {
    _ <- IO.println("Тестирование Counter начато")
    counterRef <- IO.ref(counterInit)
    responce <- client(counterRef).expect[Json](req1)
    _ <- responce.as[Counter] match {
      case Left(value) => IO.println(value.getMessage())
      case Right(value) =>
        IO.println(s"Значение каунтера равно  $expected?\n ${value.counter}  ${if(expected == value.counter) "OK" else "Fail"}")


    }
    _ <- IO.println("Тестирование Counter завершено")

    _ <- IO.println("\n\n")
    _ <- IO.println("Тестирование Slow начато")
    sTime <- IO.pure(Instant.now())
    chs <- client(counterRef).stream(req2)
      .flatMap(_.body.chunks)
      .compile
      .toList
    eTime <- IO.pure(Instant.now())
    _ <- IO.println(s"Время: ${eTime.toEpochMilli - sTime.toEpochMilli }  msec.")
    _ <- IO.println(s"Получено чанков: ${chs.size}")
    bytes: Seq[Byte] = chs.flatMap(_.toList)
    _ <- IO.println(s"Получено байт: ${bytes.size}")
    _ <- IO.println("Тестирование Slow завершено")

  } yield ()




}
