package scalaz.stream

import org.scalacheck.Prop._
import org.scalacheck.Properties

import scalaz._
import scalaz.concurrent._
import scalaz.stream._
import scalaz.stream.async.mutable._


/**
 * Created by pach on 06/11/14.
 */
object WyeMergeSpinSpec extends Properties("WyeMerge") {

  property("deadlocks.on.spin") = secure {

    val all = new All(); all.Effects.start(); all.runOutput.size

    println("DONE ONE")

    Thread.sleep(1000)

    val all2 = new All(); all2.Effects.start(); all2.runOutput.size

    println("DONE TWO")

    Thread.sleep(1000)


    val all3 = new All(); all3.Effects.start(); all3.runOutput.size

    println("DONE THREE")

    Thread.sleep(1000)

    true
  }

}


class All {
  // Interesting to note: I have always seen `runOutput` terminate when
  // either of the following alternatives to `unitProcess` are used instead:
  // (1) Process.awakeEvery(1.millis).map(_ => ())
  // (2) Process(())
  val unitProcess: Process[Task, Unit] = Process.constant(())

  val effects: Process[Task, Unit] = {
    // With fewer `unitProcess` passed to mergeN, the issue presents
    // itself less frequently. When I pass a single `unitProcess` to mergeN,
    // I have *still* seen `runOutput` not terminate.
    // I have not seen the issue arise with the mergeN removed entirely.
    val processes: Process[Task, Process[Task, Unit]] = Process(
      unitProcess,
      unitProcess,
      unitProcess,
      unitProcess,
      unitProcess,
      unitProcess,
      unitProcess)

    merge.mergeN(processes)
  }

  val poison: Signal[Boolean] = async.signal[Boolean]

  val poisonObserver: Sink[Task, Boolean] = {
    def look(bool: Boolean): Task[Unit] = Task delay { println(s"SINK SET TO: $bool") }
    Process.constant(look _)
  }

  val observedPoison = poison.discrete observe poisonObserver

  def runOutput: Seq[Unit] = {
    val result: Process[Task, Unit] = {
      val interrupted = (observedPoison wye effects)(wye.interrupt) map { msg => println(s"GOT MSG $msg"); msg }
      interrupted onComplete Process.eval(Task.delay(println(s"COMPLETE"))).drain
    }

    result.runLog[Task, Unit].run
  }

  object Effects extends Thread {
    override def run(): Unit = {
      poison.set(true).run
      println("POISON SET")
    }
  }
}