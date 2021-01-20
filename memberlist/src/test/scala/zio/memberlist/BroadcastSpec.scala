package zio.memberlist

import zio._
import zio.logging._
import zio.clock._
import zio.test.Assertion._
import zio.test._
import zio.memberlist.state._

object BroadcastSpec extends KeeperSpec {

  val logger    = Logging.console((_, line) => line)
  val testLayer = (ZLayer.requires[Clock] ++ logger) >>> Nodes.live(NodeAddress(Array(0,0,0,0), 1111))

  def generateMessage(size: Int): Chunk[Byte] =
    Chunk.fromArray(Array.fill[Byte](size)(1))

  val spec = suite("broadcast")(
    testM("add and retrieve from broadcast") {
      for {
        broadcast <- Broadcast.make(500, 2)
        _         <- Nodes.addNode(NodeAddress(Array(1, 1, 1, 1), 1111)).commit
        _         <- Nodes.addNode(NodeAddress(Array(2, 2, 2, 2), 1111)).commit
        _         <- broadcast.add(Message.Broadcast(generateMessage(100)))
        _         <- broadcast.add(Message.Broadcast(generateMessage(50)))
        _         <- broadcast.add(Message.Broadcast(generateMessage(200)))
        result    <- broadcast.broadcast(200)
      } yield assert(result)(hasSameElements(List(generateMessage(50), generateMessage(200))))
    },
    testM("resent message") {
      for {
        broadcast <- Broadcast.make(500, 2)
        _         <- Nodes.addNode(NodeAddress(Array(1, 1, 1, 1), 1111)).commit
        _         <- Nodes.addNode(NodeAddress(Array(2, 2, 2, 2), 1111)).commit
        _         <- broadcast.add(Message.Broadcast(generateMessage(100)))
        result <- ZIO.reduceAll(
                   ZIO.succeedNow(List.empty[Chunk[Byte]]),
                   (1 to 3).map(_ => broadcast.broadcast(100))
                 )(_ ++ _)
      } yield assert(result.size)(equalTo(2))
    }
  ).provideCustomLayer(testLayer)
}
