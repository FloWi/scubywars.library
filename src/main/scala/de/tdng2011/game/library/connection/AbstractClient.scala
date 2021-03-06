package de.tdng2011.game.library.connection

import java.net.Socket
import de.tdng2011.game.library.{ World, Shot, Player, ScoreBoard, EntityTypes }
import de.tdng2011.game.library.util.{ ScubywarsLogger, ByteUtil, StreamUtil }
import de.tdng2011.game.library.event.{ CollisionEvent, PlayerKilledEvent, PlayerSpawnedEvent, ShotSpawnedEvent }
import java.io.{ EOFException, IOException, DataInputStream }

abstract class AbstractClient(hostname: String, relation: RelationTypes.Value, autoconnect: Boolean = true) extends Runnable with ScubywarsLogger {

  private var world: World = _
  private var scoreBoard: Map[Long, Int] = Map()
  private var nameMap: Map[Long, String] = Map()

  private var publicId: Long = -1

  private var connected = false

  private var connection: Socket = _

  if (autoconnect) {
    connect
  }

  def connect {
    if (connected) {
      disconnect
      Thread.sleep(500)
    }
    connected = true
    connection = connectSocket()
    new Thread(this).start
  }

  def disconnect {
    connected = false
    connection.close
  }

  def run() {
    var iStream = new DataInputStream(connection.getInputStream)

    while (connected) {
      try {
        readEntity(iStream)
      } catch {
        case e @ (_: IOException | _: EOFException) => {
          if (connected) {
            logger.warn("error while getting frame. trying to reconnect!", e)
            disconnect
            connection = connectSocket()
            iStream = new DataInputStream(connection.getInputStream)
          } else {
            logger.debug("Disconnected!")
          }
        }
        case e => {
          logger.error("exception while process entity", e)
        }
      }
    }
  }

  def readEntity(iStream: DataInputStream) {
    StreamUtil.read(iStream, 2).getShort match {
      case x if x == EntityTypes.World.id => {
        world = World(iStream)
        processWorld(world)
      }
      case x if x == EntityTypes.ScoreBoard.id => {
        scoreBoard = ScoreBoard.parseScoreBoard(iStream)
        processScoreBoard(scoreBoard)
      }
      case x if x == EntityTypes.PlayerJoined.id => {
        val player = Player.parsePlayerIdAndName(iStream)
        addPlayer(player)
      }
      case x if x == EntityTypes.PlayerLeft.id => {
        val playerId = Player.parsePlayerId(iStream)
        scoreBoard = scoreBoard - playerId
        nameMap = nameMap - playerId
        updatePlayers
      }
      case x if x == EntityTypes.PlayerName.id => {
        val player = Player.parsePlayerIdAndName(iStream)
        addPlayer(player)
      }

      // NG
      case x if x == EntityTypes.PlayerKilledEvent.id => {
        playerKilledEvent(new PlayerKilledEvent(iStream))
      }

      case x if x == EntityTypes.PlayerCollisionEvent.id => {
        playerCollisionEvent(new CollisionEvent(iStream))
      }

      case x if x == EntityTypes.ShotCollisionEvent.id => {
        shotCollisionEvent(new CollisionEvent(iStream))
      }

      case x if x == EntityTypes.PlayerSpawnedEvent.id => {
        playerSpawnedEvent(new PlayerSpawnedEvent(iStream))
      }

      case x if x == EntityTypes.ShotSpawnedEvent.id => {
        shotSpawnedEvent(new ShotSpawnedEvent(iStream))
      }

      case x => {
        logger.warn("unknown typeId received: " + x)
        val size = StreamUtil.read(iStream, 4).getInt
        StreamUtil.read(iStream, size) //skip
      }
    }
  }

  def addPlayer(player: (Long, String)) {
    scoreBoard = scoreBoard + (player._1 -> 0)
    nameMap = nameMap + (player._1 -> player._2)
    updatePlayers
  }

  def playerKilledEvent(event: PlayerKilledEvent) {}

  def playerCollisionEvent(event: CollisionEvent) {}

  def shotCollisionEvent(event: CollisionEvent) {}

  def playerSpawnedEvent(event: PlayerSpawnedEvent) {}

  def shotSpawnedEvent(event: ShotSpawnedEvent) {}

  def updatePlayers {
    processScoreBoard(scoreBoard)
    processNames(nameMap)
  }

  private def handshake(s: Socket) {
    relation match {
      case x if x == RelationTypes.Player => handshakePlayer(s)
      case x if x == RelationTypes.Visualizer => handshakeVisualizer(s)
      case x if x == RelationTypes.PlayerNG => handshakePlayer(s, true)
      case x if x == RelationTypes.VisualizerNG => handshakeVisualizer(s, true)
      case x => {
        logger.warn("unknown relation: " + x)
        System exit -1
      }
    }
  }

  private def handshakePlayer(s: Socket, ng: Boolean = false) {
    val iStream = new DataInputStream(s.getInputStream)

    val relationType = if (ng) RelationTypes.PlayerNG.id.shortValue else RelationTypes.Player.id.shortValue

    s.getOutputStream.write(ByteUtil.toByteArray(EntityTypes.Handshake, relationType, name))

    val buf = StreamUtil.read(iStream, 6)
    val typeId = buf.getShort
    val size = buf.getInt

    val response = StreamUtil.read(iStream, size)
    if (typeId == EntityTypes.Handshake.id) {
      val responseCode = response.get
      logger.info("connected! response code: " + responseCode)
      if (responseCode == 0)
        publicId = response.getLong
      logger.info("public ID: " + publicId)
    }
  }

  private def handshakeVisualizer(s: Socket, ng: Boolean = false) {
    val relationType = if (ng) RelationTypes.VisualizerNG.id.shortValue else RelationTypes.Visualizer.id.shortValue
    s.getOutputStream.write(ByteUtil.toByteArray(EntityTypes.Handshake, relationType))
  }

  private def connectSocket(): Socket = {
    try {
      val s = new Socket(hostname, 1337)
      s.setTcpNoDelay(true)
      handshake(s)
      s
    } catch {
      case e => {
        logger.warn("connecting failed. retrying in 5 seconds");
        Thread.sleep(5000)
        connectSocket()
      }
    }
  }

  def getConnection = connection

  def getPublicId = publicId

  def name = "Player"

  def getWorld = world
  def getScoreBoard = scoreBoard
  def getNames = nameMap

  @throws(classOf[Exception])
  def processWorld(world: World): Unit
  def processScoreBoard(scoreBoard: Map[Long, Int]) {}
  def processNames(names: Map[Long, String]) {}

  def action(turnLeft: Boolean, turnRight: Boolean, thrust: Boolean, fire: Boolean) {
    getConnection.getOutputStream.write(ByteUtil.toByteArray(EntityTypes.Action, turnLeft, turnRight, thrust, fire))
  }
}
