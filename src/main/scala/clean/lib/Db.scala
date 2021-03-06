/*
 active-learning-scala: Active Learning library for Scala
 Copyright (c) 2014 Davi Pereira dos Santos

   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package clean.lib

import java.io.File
import java.sql.{Connection, DriverManager, Statement, Timestamp}
import java.util.{Random, Calendar, UUID}


/**
 * Cada instancia desta classe representa uma conexao.
 */
class Db(val database: String, readOnly: Boolean) extends Log with Lock {
   override lazy val toString = database
   private var connection: Connection = null
   val context = database
   val connectionWait_ms = 60000
   var alive = Array.fill(Global.runs)(Array.fill(Global.folds)(false))
   val id = new File("/proc/self").getCanonicalFile.getName + java.net.InetAddress.getLocalHost.getHostName + System.currentTimeMillis() + UUID.randomUUID().toString
   val rnd = new Random(id.map(_.toByte).sum)

   def open() {
      try {
         val url = s"jdbc:mysql://${Global.mysqlHost(readOnly)}:${Global.mysqlPort(readOnly)}/" + database
         //      val url = "jdbc:sqlite:////" + database
         //      connection = DriverManager.getConnection(url)
         connection = DriverManager.getConnection(url, "davi", Global.mysqlPass(readOnly))
         //      connection.asInstanceOf[SQLiteConnection].setBusyTimeout(20 * 60 * 1000) //20min. timeout
         log(s"Connection to $database opened.")
      } catch {
         case e: Throwable => //e.printStackTrace()
            log(s"Problems opening db connection: ${e.getMessage} ! Trying again in 30s...", 30)
            Thread.sleep(connectionWait_ms)
            test("")
      }
   }

   def startbeat(r: Int, f: Int): Unit = {
      if (!alive(r)(f)) {
         alive(r)(f) = true
         Thread.sleep(100)
         new Thread(new Runnable() {
            def run() {
               while (Global.running && alive(r)(f)) {
                  heartbeat(r, f)

                  //120s
                  1 to 2000 takeWhile { _ =>
                     Thread.sleep(60)
                     Global.running && alive(r)(f)
                  }
               }
            }
         }).start()
         log(s"created alive beeper for pool $r.$f")
      } else heartbeat(r, f)
   }

   private def heartbeat(r: Int, f: Int) {
      val now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Calendar.getInstance().getTime)
      if (readOnly) error("read only")
      else write(s"update l set u='$id', t='$now' where r=$r and f=$f")
   }

   def toDate(timestamp: java.sql.Timestamp) = {
      val milliseconds = timestamp.getTime + (timestamp.getNanos / 1000000)
      new java.util.Date(milliseconds)
   }

   def isAliveByOtherJob(r: Int, f: Int, lifetimeSeconds: Double = 180): Boolean = {
      val now = Calendar.getInstance().getTime
      val sql = s"select * from l where r=$r and f=$f"
      try {
         val statement = connection.createStatement()
         val resultSet = statement.executeQuery(sql)
         val rsmd = resultSet.getMetaData
         val numColumns = rsmd.getColumnCount
         val columnsType = new Array[Int](numColumns + 1)
         columnsType(0) = 0
         1 to numColumns foreach (i => columnsType(i) = rsmd.getColumnType(i))
         val queue = collection.mutable.Queue[(Int, Int, String, Timestamp)]()
         while (resultSet.next()) {
            val tup = (resultSet.getInt(1), resultSet.getInt(2), resultSet.getString(3), resultSet.getTimestamp(4))
            queue.enqueue(tup)
         }
         resultSet.close()
         statement.close()
         val past = toDate(queue.head._4)
         val idPast = queue.head._3
         val elapsedSeconds = (now.getTime - past.getTime) / 1000d
         val ocupado = idPast != id && elapsedSeconds < lifetimeSeconds
         if (ocupado) {
            log(s"meu:$id outro:$idPast\n lifetime:$elapsedSeconds r=$r and f=$f", 30)
            true
         } else {
            //marca e espera pra ver se tem alguém concorrendo
            heartbeat(r, f)
            Thread.sleep(rnd.nextInt(3000))
            val statement = connection.createStatement()
            val resultSet = statement.executeQuery(sql)
            val rsmd = resultSet.getMetaData
            val numColumns = rsmd.getColumnCount
            val columnsType = new Array[Int](numColumns + 1)
            columnsType(0) = 0
            1 to numColumns foreach (i => columnsType(i) = rsmd.getColumnType(i))
            val queue = collection.mutable.Queue[(Int, Int, String, Timestamp)]()
            while (resultSet.next()) {
               val tup = (resultSet.getInt(1), resultSet.getInt(2), resultSet.getString(3), resultSet.getTimestamp(4))
               queue.enqueue(tup)
            }
            resultSet.close()
            statement.close()
            val idPast = queue.head._3
            val res = idPast != id
            if (res) {
               log(s"Passaram na frente: $idPast", 30)
               true
            } else {
               //marca e espera pra ver se tem alguém concorrendo
               heartbeat(r, f)
               Thread.sleep(rnd.nextInt(3000))
               val statement = connection.createStatement()
               val resultSet = statement.executeQuery(sql)
               val rsmd = resultSet.getMetaData
               val numColumns = rsmd.getColumnCount
               val columnsType = new Array[Int](numColumns + 1)
               columnsType(0) = 0
               1 to numColumns foreach (i => columnsType(i) = rsmd.getColumnType(i))
               val queue = collection.mutable.Queue[(Int, Int, String, Timestamp)]()
               while (resultSet.next()) {
                  val tup = (resultSet.getInt(1), resultSet.getInt(2), resultSet.getString(3), resultSet.getTimestamp(4))
                  queue.enqueue(tup)
               }
               resultSet.close()
               statement.close()
               val idPast = queue.head._3
               val res = idPast != id
               if (res) {
                  log(s"Passaram na frente: $idPast", 30)
                  true
               } else {
                  //marca e espera pra ver se tem alguém concorrendo
                  heartbeat(r, f)
                  Thread.sleep(rnd.nextInt(3000))
                  val statement = connection.createStatement()
                  val resultSet = statement.executeQuery(sql)
                  val rsmd = resultSet.getMetaData
                  val numColumns = rsmd.getColumnCount
                  val columnsType = new Array[Int](numColumns + 1)
                  columnsType(0) = 0
                  1 to numColumns foreach (i => columnsType(i) = rsmd.getColumnType(i))
                  val queue = collection.mutable.Queue[(Int, Int, String, Timestamp)]()
                  while (resultSet.next()) {
                     val tup = (resultSet.getInt(1), resultSet.getInt(2), resultSet.getString(3), resultSet.getTimestamp(4))
                     queue.enqueue(tup)
                  }
                  resultSet.close()
                  statement.close()
                  val idPast = queue.head._3
                  val res = idPast != id
                  if (res) log(s"Passaram na frente: $idPast", 30)
                  res
               }
            }
         }
      } catch {
         case e: Throwable => //e.printStackTrace()
            log(s"\nProblems executing SQL query '$sql': ${e.getMessage} .\nTrying againg in 60s.\n", 30)
            Thread.sleep(120000) //waiting time is longer than normal to allow for other alive connections to update the table
            test(sql)
            isAliveByOtherJob(r, f, lifetimeSeconds + 120) //each time we recover, the elapsed time should be higher
      }
   }

   override def error(msg: String) = {
      if (connection != null && !connection.isClosed) close()
      else log("error: closed or null")
      super.error(database + ": " + msg)
   }

   def quit(msg: String) = {
      close()
      justQuit(msg)
   }

   def read(sql: String): List[Vector[Double]] = {
      test(sql)
      log(s"[$sql]", 5)
      try {
         val statement = connection.createStatement()
         val resultSet = statement.executeQuery(sql)
         val rsmd = resultSet.getMetaData
         val numColumns = rsmd.getColumnCount
         val columnsType = new Array[Int](numColumns + 1)
         columnsType(0) = 0
         1 to numColumns foreach (i => columnsType(i) = rsmd.getColumnType(i))
         val queue = collection.mutable.Queue[Seq[Double]]()
         while (resultSet.next()) {
            val seq = 1 to numColumns map { i => resultSet.getDouble(i)}
            queue.enqueue(seq)
         }
         resultSet.close()
         statement.close()
         queue.toList.map(_.toVector)
      } catch {
         case e: Throwable => //e.printStackTrace()
            log(s"\nProblems executing SQL query '$sql': ${e.getMessage} .\nTrying againg in  $connectionWait_ms ms.\n", 30)
            Thread.sleep(connectionWait_ms)
            test(sql)
            read(sql)
      }
   }

   def test(sql: String) = if (isClosed) {
      log(s"[$sql]\n[Re]opening database...")
      open()
   }

   def write(sql: String): Unit = if (readOnly) error("read only")
   else {
      test(sql)
      log(s"[$sql]", 10)
      try {
         acquire()
         val statement = connection.createStatement()
         statement.executeUpdate(sql)
         statement.close()
      } catch {
         case e: Throwable => //e.printStackTrace()
            val emsg = e.getMessage
            if (emsg.contains("Duplicate entry")) error(s"\nProblems executing SQL query '$sql' in: $emsg}")
            else log(s"\nProblems executing SQL query '$sql' in: $emsg} .\nTrying againg in  $connectionWait_ms ms", 30)
            release()
            Thread.sleep(connectionWait_ms)
            test(sql)
            write(sql)
      } finally release()
   }

   def readBlobs(sql: String): List[(Array[Byte], Int)] = {
      test(sql)
      log(s"[$sql]", 5)
      try {
         val statement = connection.createStatement()
         val resultSet = statement.executeQuery(sql)
         val queue = collection.mutable.Queue[(Array[Byte], Int)]()
         while (resultSet.next()) {
            val bytes = resultSet.getBytes(1)
            queue.enqueue(bytes -> resultSet.getInt(2))
         }
         resultSet.close()
         statement.close()
         queue.toList
      } catch {
         case e: Throwable => //e.printStackTrace()
            log(s"\nProblems executing SQL read blobs query '$sql': ${e.getMessage} .\nTrying againg in  $connectionWait_ms ms.\n", 30)
            Thread.sleep(connectionWait_ms)
            test(sql)
            readBlobs(sql)
      }
   }

   def readString(sql: String): List[Vector[String]] = {
      test(sql)
      log(s"[$sql]", 5)
      try {
         val statement = connection.createStatement()
         val resultSet = statement.executeQuery(sql)
         val rsmd = resultSet.getMetaData
         val numColumns = rsmd.getColumnCount
         val queue = collection.mutable.Queue[Seq[String]]()
         while (resultSet.next()) {
            val seq = 1 to numColumns map { i => resultSet.getString(i)}
            queue.enqueue(seq)
         }
         resultSet.close()
         statement.close()
         queue.toList.map(_.toVector)
      } catch {
         case e: Throwable => //e.printStackTrace()
            log(s"\nProblems executing SQL read styrings query '$sql': ${e.getMessage} .\nTrying againg in  $connectionWait_ms ms.\n", 30)
            Thread.sleep(connectionWait_ms)
            test(sql)
            readString(sql)
      }
   }

   def readBlobs4(sql: String): List[(Array[Byte], Int, Int, Int)] = {
      test(sql)
      log(s"[$sql]", 5)
      try {
         val statement = connection.createStatement()
         val resultSet = statement.executeQuery(sql)
         val queue = collection.mutable.Queue[(Array[Byte], Int, Int, Int)]()
         while (resultSet.next()) {
            val bytes = resultSet.getBytes(1)
            queue.enqueue((bytes, resultSet.getInt(2), resultSet.getInt(3), resultSet.getInt(4)))
         }
         resultSet.close()
         statement.close()
         queue.toList
      } catch {
         case e: Throwable => //e.printStackTrace()
            log(s"\nProblems executing read blobs4 SQL query '$sql' in: ${e.getMessage} .\nTrying againg in  $connectionWait_ms ms.\n", 30)
            Thread.sleep(connectionWait_ms)
            test(sql)
            readBlobs4(sql)
      }
   }

   def writeBlob(sql: String, data: Array[Byte]): Unit = if (readOnly) error("read only")
   else {
      test(sql)
      log(s"[$sql]", 10)
      try {
         acquire()
         val statement = connection.prepareStatement(sql)
         statement.setBytes(1, data)
         statement.execute()
         statement.close()
      } catch {
         case e: Throwable => //e.printStackTrace()
            val emsg = e.getMessage
            if (emsg.contains("Duplicate entry")) error(s"\nProblems executing SQL query '$sql' in: $emsg}")
            else log(s"\nProblems executing SQL query '$sql' in: $emsg} .\nTrying againg in  $connectionWait_ms ms", 30)
            release()
            Thread.sleep(connectionWait_ms)
            test(sql)
            writeBlob(sql, data)
      } finally release()
   }

   /**
    * Several blob writings inside a transaction.
    * @param sqls
    */
   def batchWriteBlob(sqls: List[String], blobs: List[Array[Byte]]): Unit = if (readOnly) error("read only")
   else {
      if (connection.isClosed) error(s"Not applying sql queries $sqls. Database $database is closed.")
      log("batch write blob ... head: " + sqls.head, 10)
      log(sqls.mkString("\n"), 2)
      var stats: List[Statement] = null
      try {
         acquire()
         connection.setAutoCommit(false)
         stats = sqls.zip(blobs) map { case (sql, blob) =>
            val statement = connection.prepareStatement(sql)
            if (blob != null) statement.setBytes(1, blob)
            statement.execute()
            statement
         }
         connection.commit()
         stats foreach (_.close())
      } catch {
         case e: Throwable => //e.printStackTrace()
            val emsg = e.getMessage
            if (emsg.contains("Duplicate entry")) error(s"\nProblems executing SQL query '${sqls.head}' in: $emsg}")
            else log(s"\nProblems executing SQL batch query '${sqls.head}' in: $emsg} .\nTrying againg in  $connectionWait_ms ms", 30)
            if (connection != null) {
               try {
                  System.err.print("Transaction is being rolled back")
                  connection.rollback()
                  connection.setAutoCommit(true)
               } catch {
                  case e2: Throwable => log(s"\nProblems 'rolling back'/'setting auto commit' SQL queries '$sqls': ${e2.getMessage} .\n" +
                     s"Probably it wasn't needed anyway.", 30)
               }
               release()
            }
            Thread.sleep(connectionWait_ms)
            test(sqls.mkString("; "))
            batchWriteBlob(sqls, blobs)
      } finally {
         //      if (stats != null && stats.forall(_ != null)) stats foreach (_.close())
         connection.setAutoCommit(true)
         release()
      }
      log("batch write blob finished.")
   }

   /**
    * Several queries inside a transaction.
    * @param sqls
    */
   def batchWrite(sqls: List[String]): Unit = if (readOnly) error("read only")
   else {
      if (connection.isClosed) error(s"Not applying sql queries $sqls. Database $database is closed.")
      //      log("batch write blob ... head: " + sqls.head, 10)
      sqls foreach (m => log(m, 20))
      log("\n\n", 20)
      var statement: Statement = null
      try {
         acquire()
         connection.setAutoCommit(false)
         statement = connection.createStatement()
         sqls foreach statement.execute
         connection.commit()
         statement.close()
      } catch {
         case e: Throwable => //e.printStackTrace()
            val emsg = e.getMessage
            if (emsg.contains("Duplicate entry")) error(s"\nProblems executing SQL query '${sqls.head}' in: $emsg}")
            else log(s"\nProblems executing SQL query '${sqls.head}' in: $emsg} .\nTrying againg in  $connectionWait_ms ms", 30)
            if (connection != null) {
               try {
                  log("Transaction is being rolled back...", 30)
                  connection.rollback()
                  connection.setAutoCommit(true)
               } catch {
                  case e2: Throwable => log(s"\nProblems 'rolling back'/'setting auto commit' SQL queries '$sqls': ${e2.getMessage}.\n" +
                     s"Probably it wasn't needed anyway.", 30)
               }
               release()
            }
            Thread.sleep(connectionWait_ms)
            test(sqls.mkString("; "))
            log("Recursive call...", 30)
            batchWrite(sqls)
            println(s"depois")
      } finally {
         if (statement != null) statement.close()
         connection.setAutoCommit(true)
         release()
      }
   }

   def isClosed = connection == null || connection.isClosed

   def close() {
      0 until Global.runs foreach { r =>
         0 until Global.folds foreach { f =>
            alive(r)(f) = false
         }
      }
      log(s"Connection to $database closed.")
      if (connection != null && !connection.isClosed) connection.close()
   }
}

object DbTest extends App {
   val ds = Ds("iris", readOnly = false)
   ds.open()
   ds.write("insert into r values (1999999999, 1, 1999999999)")
   ds.close()
}