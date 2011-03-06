package de.tdng2011.game.library

import actors.Actor
import de.tdng2011.game.library.util.StreamUtil

import java.nio.ByteBuffer
import java.io.DataInputStream

/**
 * Created by IntelliJ IDEA.
 * User: benjamin
 * Date: 23.01.11
 * Time: 16:59
 */

object ScoreBoard {

  def parseScoreBoard(iStream : DataInputStream) = {
    val size = StreamUtil.read(iStream, 4).getInt
    val buf = StreamUtil.read(iStream, size)

    var scores = Map[Long, Int]()

    while(buf.hasRemaining) {
      scores = scores + (buf.getLong -> buf.getInt)
    }

    scores
  }
}